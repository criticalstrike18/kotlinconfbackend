package org.jetbrains.kotlinApp.fileImport

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.jetbrains.kotlinApp.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Service responsible for downloading files from a remote server with progress tracking.
 */
actual class FileDownloadService actual constructor(context: ApplicationContext) {
    private val applicationContext = context.application
    private val downloadManager = applicationContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val activeDownloads = ConcurrentHashMap<Long, AtomicBoolean>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tag = "FileDownloadService"
    private val httpDownloader = HttpDownloader()

    // Size threshold for using direct streaming instead of DownloadManager (2GB)
    private val LARGE_FILE_THRESHOLD = 2L * 1024 * 1024 * 1024

    /**
     * Downloads a file using the Android DownloadManager for standard downloads
     * or falls back to the HttpDownloader for very large files.
     * This hybrid approach ensures we can handle files of any size efficiently.
     */
    actual fun downloadFile(
        url: String,
        destinationPath: String?,
        fileName: String?,
        headers: Map<String, String>,
    ): Flow<DownloadProgress> = flow {
        // Create the download request
        val finalDestinationPath = destinationPath ?: getDefaultDownloadDirectory()
        val finalFileName = fileName ?: Uri.parse(url).lastPathSegment ?: "downloaded_file"

        // Ensure the destination directory exists
        val destDir = File(finalDestinationPath)
        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        val destinationFile = File(destDir, finalFileName)

        // Send initial progress
        emit(DownloadProgress(DownloadStatus.DOWNLOADING, 0f, 0, 0))

        try {
            // Check content length from HEAD request
            val contentLengthHeaders = headers.toMutableMap()
            contentLengthHeaders["Accept-Encoding"] = "identity" // Prevent compression

            // For very large files (>2GB), use our custom streaming downloader
            if (isLargeFile(url, contentLengthHeaders)) {
                Log.d(tag, "Large file detected - using direct streaming downloader")
                // Use httpDownloader for large files
                httpDownloader.downloadFile(url, finalDestinationPath, finalFileName, headers)
                    .collect { progress ->
                        emit(progress)
                    }
            } else {
                // Use standard DownloadManager for normal sized files
                val destinationUri = Uri.fromFile(destinationFile)

                // Create download request
                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle(finalFileName)
                    .setDescription("Downloading file")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationUri(destinationUri)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)

                // Add headers if provided
                headers.forEach { (key, value) ->
                    request.addRequestHeader(key, value)
                }

                // Enqueue the download and get the download ID
                val downloadId = downloadManager.enqueue(request)

                // Add to active downloads
                activeDownloads[downloadId] = AtomicBoolean(true)

                // Create a channel for broadcast receiver communication
                val downloadComplete = Channel<Int>(Channel.CONFLATED)

                // Register broadcast receiver for download completion
                val downloadCompleteReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                        if (id == downloadId) {
                            // Query download status
                            val query = DownloadManager.Query().setFilterById(downloadId)
                            val cursor = downloadManager.query(query)

                            if (cursor.moveToFirst()) {
                                val statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                val status = if (statusColumn != -1) cursor.getInt(statusColumn) else -1

                                when (status) {
                                    DownloadManager.STATUS_SUCCESSFUL -> {
                                        downloadComplete.trySend(DownloadManager.STATUS_SUCCESSFUL)
                                    }
                                    DownloadManager.STATUS_FAILED -> {
                                        downloadComplete.trySend(DownloadManager.STATUS_FAILED)
                                    }
                                }
                            }
                            cursor.close()
                        }
                    }
                }

                // Register the receiver - must be unregistered later
                val receiverRegistration = ContextCompat.registerReceiver(
                    applicationContext,
                    downloadCompleteReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )

                try {
                    var isCompleted = false

                    while (!isCompleted && activeDownloads[downloadId]?.get() == true) {
                        // Check if we got a broadcast completion message
                        val completionStatus = downloadComplete.tryReceive().getOrNull()
                        if (completionStatus != null) {
                            when (completionStatus) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    emit(DownloadProgress(DownloadStatus.COMPLETED, 1f, 100, 100))
                                    isCompleted = true
                                    continue
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    val query = DownloadManager.Query().setFilterById(downloadId)
                                    val cursor = downloadManager.query(query)
                                    val reasonMessage = if (cursor.moveToFirst()) {
                                        val reasonColumn = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                        val reason = if (reasonColumn != -1) cursor.getInt(reasonColumn) else -1
                                        "Download failed with reason code: $reason"
                                    } else {
                                        "Download failed"
                                    }
                                    cursor.close()
                                    emit(DownloadProgress(DownloadStatus.FAILED, 0f, 0, 0, reasonMessage))
                                    isCompleted = true
                                    continue
                                }
                            }
                        }

                        // If not completed, check progress directly
                        val query = DownloadManager.Query().setFilterById(downloadId)
                        val cursor = downloadManager.query(query)

                        if (cursor.moveToFirst()) {
                            val statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val status = if (statusColumn != -1) cursor.getInt(statusColumn) else -1

                            when (status) {
                                DownloadManager.STATUS_RUNNING -> {
                                    val bytesDownloadedColumn = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                                    val bytesTotalColumn = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                                    val bytesDownloaded = if (bytesDownloadedColumn != -1) cursor.getLong(bytesDownloadedColumn) else 0
                                    val bytesTotal = if (bytesTotalColumn != -1) cursor.getLong(bytesTotalColumn) else 0

                                    val progress = if (bytesTotal > 0) {
                                        bytesDownloaded.toFloat() / bytesTotal.toFloat()
                                    } else {
                                        0f
                                    }

                                    emit(DownloadProgress(DownloadStatus.DOWNLOADING, progress, bytesDownloaded, bytesTotal))
                                }
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    emit(DownloadProgress(DownloadStatus.COMPLETED, 1f, 100, 100))
                                    isCompleted = true
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    val reasonColumn = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                    val reason = if (reasonColumn != -1) cursor.getInt(reasonColumn) else -1
                                    val errorMessage = "Download failed with reason code: $reason"
                                    emit(DownloadProgress(DownloadStatus.FAILED, 0f, 0, 0, errorMessage))
                                    isCompleted = true
                                }
                                DownloadManager.STATUS_PAUSED -> {
                                    val reasonColumn = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                    val reason = if (reasonColumn != -1) cursor.getInt(reasonColumn) else -1
                                    Log.d(tag, "Download paused with reason code: $reason")
                                }
                                DownloadManager.STATUS_PENDING -> {
                                    Log.d(tag, "Download pending")
                                }
                            }
                        }
                        cursor.close()

                        // Wait before next check - don't check too frequently to save resources
                        delay(500)
                    }
                } finally {
                    // Cleanup regardless of how the loop ends
                    try {
                        applicationContext.unregisterReceiver(downloadCompleteReceiver)
                    } catch (e: IllegalArgumentException) {
                        // Receiver might already be unregistered
                    }

                    downloadComplete.close()
                    activeDownloads[downloadId]?.set(false)
                    activeDownloads.remove(downloadId)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error in download: ${e.message}")
            emit(DownloadProgress(DownloadStatus.FAILED, 0f, 0, 0, e.message))
        }
    }.flowOn(Dispatchers.IO)

    actual fun cancelDownloads() {
        activeDownloads.keys.forEach { downloadId ->
            downloadManager.remove(downloadId)
            activeDownloads[downloadId]?.set(false)
        }
        activeDownloads.clear()
    }

    actual fun getDefaultDownloadDirectory(): String {
        return applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
            ?: applicationContext.filesDir.absolutePath
    }

    /**
     * Checks if the file at the given URL is larger than the threshold for large files
     */
    private suspend fun isLargeFile(url: String, headers: Map<String, String>): Boolean {
        return try {
            httpDownloader.getContentLength(url, headers).let { contentLength ->
                contentLength > LARGE_FILE_THRESHOLD || contentLength < 0
            }
        } catch (e: Exception) {
            Log.e(tag, "Error checking content length: ${e.message}")
            false
        }
    }

    fun cleanup() {
        cancelDownloads()
        httpDownloader.close()
        serviceScope.cancel()
    }
}