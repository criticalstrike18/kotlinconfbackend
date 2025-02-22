package org.jetbrains.kotlinApp


expect class NotificationManager(context: ApplicationContext) {
    fun requestPermission()

    fun schedule(delay: Long, title: String, message: String): String?

    fun cancel(title: String)
}