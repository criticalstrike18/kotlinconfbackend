package org.jetbrains.kotlinApp

import android.app.Application

actual class ApplicationContext(
    val application: Application,
    val notificationIcon: Int
)
