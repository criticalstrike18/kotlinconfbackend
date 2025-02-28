package org.jetbrains.kotlinApp.fileImport

import kotlinx.coroutines.flow.Flow
import org.jetbrains.kotlinApp.ApplicationContext

enum class DownloadStatus {
    IDLE,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class DownloadProgress(
    val status: DownloadStatus = DownloadStatus.IDLE,
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val error: String? = null
)

/**
 * Service responsible for downloading files from a remote server with progress tracking.
 */
expect class FileDownloadService(context: ApplicationContext) {
    /**
     * Downloads a file from the specified URL and saves it to the specified destination.
     * Returns a flow of download progress updates.
     *
     * @param url The URL of the file to download
     * @param destinationPath The path where the file will be saved
     * @param fileName The name of the file to save
     * @param headers Optional map of HTTP headers to include with the request
     * @return A Flow of DownloadProgress updates
     */
    fun downloadFile(
        url: String,
        destinationPath: String? = null,
        fileName: String? = null,
        headers: Map<String, String> = emptyMap()
    ): Flow<DownloadProgress>

    /**
     * Cancels any ongoing download operations
     */
    fun cancelDownloads()

    /**
     * Gets the default download directory for the platform
     *
     * @return The path to the default download directory
     */
    fun getDefaultDownloadDirectory(): String
}