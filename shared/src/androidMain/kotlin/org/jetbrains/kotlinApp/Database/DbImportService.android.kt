package org.jetbrains.kotlinApp.Database

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.jetbrains.kotlinApp.SessionDatabase
import java.io.File

/**
 * Service responsible for importing data from an external SQLite file directly into
 * the application's database using optimized batch transactions.
 */
actual class DatabaseImportService actual constructor(
    private val database: SessionDatabase,
    private val sqlDriver: SqlDriver
) {
    private val _importProgress = MutableStateFlow<ImportProgress>(ImportProgress.Idle)
    actual val importProgress: Flow<ImportProgress> = _importProgress

    /**
     * Imports data from a SQLite file into the app's database using batch transactions.
     * Uses larger batch processing and efficient transaction handling for better performance.
     */
    actual suspend fun importFromSqliteFile(
        sqliteFilePath: String,
        tables: List<String>?,
        batchSize: Int
    ): ImportResult = withContext(Dispatchers.IO) {
        val importFile = File(sqliteFilePath)
        if (!importFile.exists()) {
            _importProgress.value = ImportProgress.Error("Import file not found: $sqliteFilePath")
            return@withContext ImportResult.Error("Import file not found: $sqliteFilePath")
        }

        var sourceDb: SQLiteDatabase? = null
        try {
            _importProgress.value = ImportProgress.Started

            // Open the source database in read-only mode
            sourceDb = SQLiteDatabase.openDatabase(
                sqliteFilePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )

            // Get tables to import (all if not specified)
            val tablesToImport = tables ?: getTableNames(sourceDb)

            val totalTables = tablesToImport.size
            var tablesProcessed = 0
            var totalRowsProcessed = 0

            // Use a larger efficient batch size if the provided one is small
            val effectiveBatchSize = maxOf(batchSize, 500)

            // Process each table
            tablesToImport.forEach { tableName ->
                try {
                    val tableRowCount = getTableRowCount(sourceDb, tableName)
                    _importProgress.value = ImportProgress.Processing(
                        table = tableName,
                        tableProgress = 0f,
                        overallProgress = tablesProcessed.toFloat() / totalTables,
                        message = "Starting import of $tableName ($tableRowCount rows)"
                    )

                    val tableRowsProcessed = importTable(
                        sourceDb = sourceDb,
                        tableName = tableName,
                        batchSize = effectiveBatchSize,
                        tableRowCount = tableRowCount,
                        updateProgress = { progress ->
                            _importProgress.value = ImportProgress.Processing(
                                table = tableName,
                                tableProgress = progress,
                                overallProgress = (tablesProcessed + progress) / totalTables,
                                message = "Importing $tableName"
                            )
                        }
                    )

                    totalRowsProcessed += tableRowsProcessed
                    tablesProcessed++

                    _importProgress.value = ImportProgress.Processing(
                        table = tableName,
                        tableProgress = 1f,
                        overallProgress = tablesProcessed.toFloat() / totalTables,
                        message = "Completed import of $tableName ($tableRowsProcessed rows)"
                    )
                } catch (e: Exception) {
                    _importProgress.value = ImportProgress.Error(
                        "Error importing table $tableName: ${e.message}"
                    )
                }
            }

            _importProgress.value = ImportProgress.Completed(
                tablesProcessed = tablesProcessed,
                rowsProcessed = totalRowsProcessed
            )

            return@withContext ImportResult.Success(
                tablesProcessed = tablesProcessed,
                rowsProcessed = totalRowsProcessed
            )
        } catch (e: Exception) {
            _importProgress.value = ImportProgress.Error("Import failed: ${e.message}")
            return@withContext ImportResult.Error("Import failed: ${e.message}")
        } finally {
            sourceDb?.close()
        }
    }

    /**
     * Imports a single table from the source database using optimized batch processing.
     * Uses a larger transaction size and minimizes progress updates to improve performance.
     */
    private suspend fun importTable(
        sourceDb: SQLiteDatabase,
        tableName: String,
        batchSize: Int,
        tableRowCount: Int,
        updateProgress: (Float) -> Unit
    ): Int {
        var totalRowsProcessed = 0
        var offset = 0

        // Update progress less frequently for better performance
        // Only update at these percentage milestones
        val progressMilestones = if (tableRowCount > 10000) {
            listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f)
        } else if (tableRowCount > 1000) {
            listOf(0.25f, 0.5f, 0.75f, 1.0f)
        } else {
            listOf(0.5f, 1.0f)
        }

        var nextMilestoneIndex = 0

        while (true) {
            // Query a batch of rows
            val cursor = sourceDb.rawQuery(
                "SELECT * FROM $tableName LIMIT $batchSize OFFSET $offset",
                null
            )

            if (cursor.count == 0) {
                cursor.close()
                break
            }

            // Process the batch in a transaction
            database.transaction {
                processCursorBatch(cursor, tableName)
            }

            // Update counts
            val batchRows = cursor.count
            totalRowsProcessed += batchRows
            offset += batchRows
            cursor.close()

            // Only update progress at milestones to reduce overhead
            val currentProgress = if (tableRowCount > 0) {
                totalRowsProcessed.toFloat() / tableRowCount
            } else 1f

            if (nextMilestoneIndex < progressMilestones.size &&
                currentProgress >= progressMilestones[nextMilestoneIndex]) {
                updateProgress(progressMilestones[nextMilestoneIndex])
                nextMilestoneIndex++
            }

            // Break if we've processed fewer rows than the batch size
            if (batchRows < batchSize) break
        }

        // Ensure final progress is reported
        if (nextMilestoneIndex < progressMilestones.size) {
            updateProgress(1.0f)
        }

        return totalRowsProcessed
    }

    /**
     * Processes a cursor batch using bulk insert approach.
     * Improved to handle columns more efficiently.
     */
    private fun processCursorBatch(cursor: Cursor, tableName: String) {
        val columnNames = cursor.columnNames
        val columnIndices = mutableMapOf<String, Int>()

        // Pre-compute column indices for faster access
        columnNames.forEachIndexed { index, name ->
            columnIndices[name] = index
        }

        while (cursor.moveToNext()) {
            try {
                when (tableName) {
                    "PodcastChannels" -> insertPodcastChannel(cursor, columnIndices)
                    "PodcastEpisodes" -> insertPodcastEpisode(cursor, columnIndices)
                    "PodcastCategories" -> insertPodcastCategory(cursor, columnIndices)
                    "ChannelCategoryMap" -> insertChannelCategoryMap(cursor, columnIndices)
                    "EpisodeCategoryMap" -> insertEpisodeCategoryMap(cursor, columnIndices)
                    // Add other tables as needed
                }
            } catch (e: Exception) {
                println("Error processing row in $tableName: ${e.message}")
                // Continue with next row instead of failing the entire batch
            }
        }
    }

    /**
     * Insert methods for specific tables - optimized with direct column index access
     */
    private fun insertPodcastChannel(cursor: Cursor, columnIndices: Map<String, Int>) {
        val id = getColumnValueFast(cursor, columnIndices, "id") as? Long ?: return
        val title = getColumnValueFast(cursor, columnIndices, "title") as? String ?: ""
        val link = getColumnValueFast(cursor, columnIndices, "link") as? String ?: ""
        val description = getColumnValueFast(cursor, columnIndices, "description") as? String ?: ""
        val copyright = getColumnValueFast(cursor, columnIndices, "copyright") as? String
        val language = getColumnValueFast(cursor, columnIndices, "language") as? String ?: ""
        val author = getColumnValueFast(cursor, columnIndices, "author") as? String ?: ""
        val ownerEmail = getColumnValueFast(cursor, columnIndices, "ownerEmail") as? String ?: ""
        val ownerName = getColumnValueFast(cursor, columnIndices, "ownerName") as? String ?: ""
        val imageUrl = getColumnValueFast(cursor, columnIndices, "imageUrl") as? String ?: ""
        val lastBuildDate = getColumnValueFast(cursor, columnIndices, "lastBuildDate") as? Long ?: 0L

        database.sessionDatabaseQueries.insertChannel(
            id = id,
            title = title,
            link = link,
            description = description,
            copyright = copyright,
            language = language,
            author = author,
            ownerEmail = ownerEmail,
            ownerName = ownerName,
            imageUrl = imageUrl,
            lastBuildDate = lastBuildDate
        )
    }

    private fun insertPodcastEpisode(cursor: Cursor, columnIndices: Map<String, Int>) {
        val id = getColumnValueFast(cursor, columnIndices, "id") as? Long ?: return
        val channelId = getColumnValueFast(cursor, columnIndices, "channelId") as? Long ?: return
        val guid = getColumnValueFast(cursor, columnIndices, "guid") as? String ?: ""
        val title = getColumnValueFast(cursor, columnIndices, "title") as? String ?: ""
        val description = getColumnValueFast(cursor, columnIndices, "description") as? String ?: ""
        val link = getColumnValueFast(cursor, columnIndices, "link") as? String ?: ""
        val pubDate = getColumnValueFast(cursor, columnIndices, "pubDate") as? Long ?: 0L
        val duration = getColumnValueFast(cursor, columnIndices, "duration") as? Long ?: 0L
        val explicit = getColumnValueFast(cursor, columnIndices, "explicit") as? Long ?: 0L
        val imageUrl = getColumnValueFast(cursor, columnIndices, "imageUrl") as? String
        val mediaUrl = getColumnValueFast(cursor, columnIndices, "mediaUrl") as? String ?: ""
        val mediaType = getColumnValueFast(cursor, columnIndices, "mediaType") as? String ?: ""
        val mediaLength = getColumnValueFast(cursor, columnIndices, "mediaLength") as? Long ?: 0L

        database.sessionDatabaseQueries.insertEpisode(
            id = id,
            channelId = channelId,
            guid = guid,
            title = title,
            description = description,
            link = link,
            pubDate = pubDate,
            duration = duration,
            explicit = explicit,
            imageUrl = imageUrl,
            mediaUrl = mediaUrl,
            mediaType = mediaType,
            mediaLength = mediaLength
        )
    }

    private fun insertPodcastCategory(cursor: Cursor, columnIndices: Map<String, Int>) {
        val name = getColumnValueFast(cursor, columnIndices, "name") as? String ?: return
        database.sessionDatabaseQueries.insertPodcastCategory(name)
    }

    private fun insertChannelCategoryMap(cursor: Cursor, columnIndices: Map<String, Int>) {
        val channelId = getColumnValueFast(cursor, columnIndices, "channelId") as? Long ?: return
        val categoryId = getColumnValueFast(cursor, columnIndices, "categoryId") as? Long ?: return

        database.sessionDatabaseQueries.insertChannelCategory(
            channelId = channelId,
            categoryId = categoryId
        )
    }

    private fun insertEpisodeCategoryMap(cursor: Cursor, columnIndices: Map<String, Int>) {
        val episodeId = getColumnValueFast(cursor, columnIndices, "episodeId") as? Long ?: return
        val categoryId = getColumnValueFast(cursor, columnIndices, "categoryId") as? Long ?: return

        database.sessionDatabaseQueries.insertEpisodeCategory(
            episodeId = episodeId,
            categoryId = categoryId
        )
    }

    /**
     * Optimized helper method to get a column value using pre-computed indices
     */
    private fun getColumnValueFast(cursor: Cursor, columnIndices: Map<String, Int>, columnName: String): Any? {
        val columnIndex = columnIndices[columnName] ?: return null
        if (cursor.isNull(columnIndex)) return null

        return when (cursor.getType(columnIndex)) {
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(columnIndex)
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(columnIndex)
            Cursor.FIELD_TYPE_STRING -> cursor.getString(columnIndex)
            Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(columnIndex)
            else -> null
        }
    }

    /**
     * Gets the total number of rows in a table
     */
    private fun getTableRowCount(db: SQLiteDatabase, tableName: String): Int {
        db.rawQuery("SELECT COUNT(*) FROM $tableName", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /**
     * Gets all table names from the database
     */
    private fun getTableNames(db: SQLiteDatabase): List<String> {
        val tables = mutableListOf<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0))
            }
        }
        return tables
    }
}