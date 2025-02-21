package org.jetbrains.kotlinApp

actual class NotificationManager actual constructor(context: ApplicationContext) {
    actual fun requestPermission() {
    }

    actual fun schedule(
        delay: Long,
        title: String,
        message: String
    ): String? = null

    actual fun cancel(title: String) {
    }

}