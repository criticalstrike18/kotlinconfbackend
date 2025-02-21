package org.jetbrains.kotlinApp.Database

import app.cash.sqldelight.db.SqlDriver
import org.jetbrains.kotlinApp.ApplicationContext

actual class DriverFactory actual constructor(context: ApplicationContext) {
    actual fun createDriver(): SqlDriver {
        TODO("Not yet implemented")
    }
}