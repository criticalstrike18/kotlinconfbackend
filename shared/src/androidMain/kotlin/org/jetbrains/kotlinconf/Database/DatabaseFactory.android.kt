package org.jetbrains.kotlinconf.Database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.jetbrains.kotlinconf.ApplicationContext
import org.jetbrains.kotlinconf.SessionDatabase

actual class DriverFactory actual constructor(private val context: ApplicationContext) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            SessionDatabase.Schema,
            context.application,
            "kotlinconf.db"
        )
    }
}