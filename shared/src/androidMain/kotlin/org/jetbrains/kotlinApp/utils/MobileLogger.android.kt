package org.jetbrains.kotlinApp.utils

import android.util.Log
import io.ktor.client.plugins.logging.Logger

actual fun appLogger(): Logger = object : Logger {
    override fun log(message: String) {
        Log.w("KotlinConfClient", message)
    }
}