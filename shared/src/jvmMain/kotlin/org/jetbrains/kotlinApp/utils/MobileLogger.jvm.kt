package org.jetbrains.kotlinApp.utils

import io.ktor.client.plugins.logging.Logger

actual fun appLogger(): Logger = object : Logger {
    override fun log(message: String) {
        println(message)
    }
}