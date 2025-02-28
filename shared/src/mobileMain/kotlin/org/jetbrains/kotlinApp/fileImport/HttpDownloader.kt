package org.jetbrains.kotlinApp.fileImport

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import kotlin.math.max

class HttpDownloader(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectTimeoutMillis: Long = 30000,
    private val requestTimeoutMillis: Long = 60000,
    private val socketTimeoutMillis: Long = 60000,
    private val maxRetries: Int = 3,
    private val retryDelayMillis: Long = 1000
) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        install(HttpTimeout) {
            connectTimeoutMillis = this@HttpDownloader.connectTimeoutMillis
            requestTimeoutMillis = this@HttpDownloader.requestTimeoutMillis
            socketTimeoutMillis = this@HttpDownloader.socketTimeoutMillis
        }
        expectSuccess = false  // Don't throw on non-2xx responses

        // Configure for large file support
        engine {
            pipelining = false  // Disable pipelining for large downloads
            dispatcher = this@HttpDownloader.dispatcher    // More threads for parallel chunk downloads
        }
    }

    /**
     * Get the content length of a file without downloading it
     */
    suspend fun getContentLength(url: String, headers: Map<String, String> = emptyMap()): Long {
        try {
            val response = client.head(url) {
                headers {
                    headers.forEach { (key, value) ->
                        append(key, value)
                    }
                    append(HttpHeaders.Range, "bytes=0-0")  // Request just the first byte
                }
            }

            // Check for Content-Range header which might have total size
            val contentRange = response.headers[HttpHeaders.ContentRange]
            if (contentRange != null) {
                // Format is usually "bytes 0-0/12345" where 12345 is total size
                val totalSize = contentRange.substringAfter("/").toLongOrNull()
                if (totalSize != null) {
                    return totalSize
                }
            }

            // If no Content-Range, use Content-Length
            return response.contentLength() ?: -1L
        } catch (e: Exception) {
            throw IOException("Failed to get content length: ${e.message}", e)
        }
    }

    /**
     * Downloads a file from the specified URL with progress tracking
     *
     * @param url URL to download from
     * @param destinationPath Path where to save the file
     * @param fileName Name to give the downloaded file
     * @param headers HTTP headers to include in the request
     * @return Flow of [DownloadProgress] updates
     */
    suspend fun downloadFile(
        url: String,
        destinationPath: String,
        fileName: String,
        headers: Map<String, String> = emptyMap()
    ): Flow<DownloadProgress> = flow {
        var retries = 0
        var success = false
        var lastError: Throwable? = null
        var startByte = 0L
        val file = File(destinationPath, fileName)

        // Create parent directories if they don't exist
        file.parentFile?.mkdirs()

        // Delete file if it exists (for fresh download)
        if (file.exists()) {
            file.delete()
        }

        emit(DownloadProgress(DownloadStatus.DOWNLOADING, 0f, 0, 0))

        while (retries <= maxRetries && !success) {
            try {
                val modifiedHeaders = headers.toMutableMap()

                // Support resume if we've already downloaded part of the file
                if (startByte > 0) {
                    modifiedHeaders["Range"] = "bytes=$startByte-"
                }

                val response: HttpResponse = client.get(url) {
                    headers {
                        modifiedHeaders.forEach { (key, value) ->
                            append(key, value)
                        }
                    }
                }

                val contentLength = response.contentLength() ?: -1L
                val totalBytes = max(contentLength, 0L)

                withContext(dispatcher) {
                    // For large files, use RandomAccessFile for better performance and seek capabilities
                    // This allows us to handle files of any size efficiently
                    val fileAccess = java.io.RandomAccessFile(file, "rw")
                    if (startByte > 0) {
                        fileAccess.seek(startByte)
                    }

                    try {
                        // Get byte channel for streaming
                        val channel: ByteReadChannel = response.bodyAsChannel()
                        var bytesWritten = startByte

                        // Use a large buffer for better performance
                        val buffer = ByteArray(1024 * 1024) // 1MB buffer

                        var bytesRead: Int
                        var lastProgressUpdate = System.currentTimeMillis()
                        val progressUpdateInterval = 100L // ms

                        while (!channel.isClosedForRead) {
                            // Read a chunk from the network
                            val chunk = channel.readRemaining(buffer.size.toLong().coerceAtMost(1024 * 1024))
                            val bytes = chunk.readByteArray()

                            if (bytes.isEmpty()) {
                                break
                            }

                            // Write the chunk to file
                            fileAccess.write(bytes)

                            // Update counters
                            bytesWritten += bytes.size

                            // Don't update progress too frequently
                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > progressUpdateInterval) {
                                val progress = if (totalBytes > 0) {
                                    bytesWritten.toFloat() / totalBytes.toFloat()
                                } else {
                                    // If content length is unknown, use a conservative progress
                                    // that never reaches 100% until complete
                                    (0.5f * bytesWritten / (bytesWritten + 1024 * 1024)).coerceAtMost(0.99f)
                                }

                                emit(DownloadProgress(
                                    DownloadStatus.DOWNLOADING,
                                    progress.coerceIn(0f, 1f),
                                    bytesWritten,
                                    totalBytes
                                ))
                                lastProgressUpdate = now
                            }
                        }
                    } finally {
                        fileAccess.close()
                    }
                }

                success = true
                emit(DownloadProgress(DownloadStatus.COMPLETED, 1f, file.length(), file.length()))

            } catch (e: IOException) {
                lastError = e
                retries++

                if (retries <= maxRetries) {
                    emit(DownloadProgress(
                        DownloadStatus.DOWNLOADING,
                        0f,
                        startByte,
                        0,
                        "Retry $retries/$maxRetries: ${e.message}"
                    ))

                    // If file exists, we can try to resume from where we left off
                    if (file.exists()) {
                        startByte = file.length()
                    }

                    delay(retryDelayMillis * retries)
                } else {
                    file.delete() // Clean up partial file on final failure
                    emit(DownloadProgress(
                        DownloadStatus.FAILED,
                        0f,
                        0,
                        0,
                        "Download failed after $maxRetries retries: ${e.message}"
                    ))
                    throw e
                }
            } catch (e: Exception) {
                lastError = e
                retries++

                if (retries <= maxRetries) {
                    emit(DownloadProgress(
                        DownloadStatus.DOWNLOADING,
                        0f,
                        startByte,
                        0,
                        "Retry $retries/$maxRetries: ${e.message}"
                    ))
                    delay(retryDelayMillis * retries)
                } else {
                    file.delete() // Clean up partial file
                    emit(DownloadProgress(
                        DownloadStatus.FAILED,
                        0f,
                        0,
                        0,
                        "Download failed after $maxRetries retries: ${e.message}"
                    ))
                    throw e
                }
            }
        }

        if (!success) {
            throw lastError ?: IOException("Download failed after $maxRetries retries")
        }
    }

    fun close() {
        client.close()
    }
}