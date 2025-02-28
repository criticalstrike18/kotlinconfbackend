package org.jetbrains.kotlinApp.Database

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.flow.Flow
import org.jetbrains.kotlinApp.SessionDatabase

expect class DatabaseImportService(
    database: SessionDatabase,
    sqlDriver: SqlDriver
) {
    val importProgress: Flow<ImportProgress>

    /**
     * Imports data from the provided SQLite file path directly into the app's database
     * using optimized batch transactions.
     *
     * @param sqliteFilePath Path to the SQLite file
     * @param tables Optional list of tables to import (imports all tables if null)
     * @return Result of the import operation
     */
    suspend fun importFromSqliteFile(
        sqliteFilePath: String,
        tables: List<String>? = null,
        batchSize: Int = 1000
    ): ImportResult
}

sealed class ImportProgress {
    object Idle : ImportProgress()
    object Started : ImportProgress()
    data class Processing(
        val table: String,
        val tableProgress: Float,
        val overallProgress: Float,
        val message: String
    ) : ImportProgress()
    data class Completed(
        val tablesProcessed: Int,
        val rowsProcessed: Int
    ) : ImportProgress()
    data class Error(val message: String) : ImportProgress()
}

sealed class ImportResult {
    data class Success(
        val tablesProcessed: Int,
        val rowsProcessed: Int
    ) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

/**
 * Service responsible for importing data from an external SQLite file directly into
 * the application's database using optimized batch transactions.
 */