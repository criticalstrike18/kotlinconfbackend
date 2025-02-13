package org.jetbrains.kotlinconf.Database

import app.cash.sqldelight.db.SqlDriver
import org.jetbrains.kotlinconf.ApplicationContext

actual class DriverFactory actual constructor(context: ApplicationContext) {
    actual fun createDriver(): SqlDriver {
        TODO("Not yet implemented")
    }
}