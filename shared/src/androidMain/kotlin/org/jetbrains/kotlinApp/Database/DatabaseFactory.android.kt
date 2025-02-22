package org.jetbrains.kotlinApp.Database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.jetbrains.kotlinApp.ApplicationContext
import org.jetbrains.kotlinApp.SessionDatabase

actual class DriverFactory actual constructor(private val context: ApplicationContext) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            SessionDatabase.Schema,
            context.application,
            "kotlinApp.db"
        )
    }
}