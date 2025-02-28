package org.jetbrains.kotlinApp.utils

import org.jetbrains.kotlinApp.ApplicationContext
import java.io.File

actual fun getImportFilePath(context: ApplicationContext, filename: String): String? {
    return try {
        val file = File(context.application.filesDir, filename)

        if (!file.exists()) {
            // If the file doesn't exist in internal storage, copy it from assets
            try {
                println("Copying $filename from assets to ${file.absolutePath}")
                context.application.assets.open(filename).use { inputStream ->
                    file.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                println("File copied successfully to ${file.absolutePath}")
            } catch (e: Exception) {
                println("Error copying file from assets: ${e.message}")
                return null
            }
        } else {
            println("File already exists at ${file.absolutePath}")
        }

        // Check if file now exists and is readable
        if (file.exists() && file.canRead()) {
            file.absolutePath
        } else {
            println("File is not readable at ${file.absolutePath}")
            null
        }
    } catch (e: Exception) {
        println("Error accessing file path for $filename: ${e.message}")
        null
    }
}