package org.jetbrains.kotlinApp.Database

import app.cash.sqldelight.db.SqlDriver
import org.jetbrains.kotlinApp.ApplicationContext
import org.jetbrains.kotlinApp.SessionDatabase

expect class DriverFactory(context: ApplicationContext) {
    fun createDriver(): SqlDriver
}
class DatabaseWrapper(private val database: SessionDatabase) {
    fun clearSyncedRecords() {
        database.transaction {
            database.sessionDatabaseQueries.run {
                // Delete junction tables first to maintain referential integrity
                deleteCompletedSessionCategories()
                deleteCompletedSessionSpeakers()
                // Delete main table records
                deleteCompletedSessions()
                deleteCompletedVotes()
                deleteCompletedFeedback()
                deleteCompletedFavorites()
                deleteCompletedSpeakers()
                deleteCompletedRooms()
                deleteCompletedCategories()
            }
        }
    }



    // Individual table clearing functions
    fun clearSyncedVotes() {
        database.transaction {
            database.sessionDatabaseQueries.deleteCompletedVotes()
        }
    }

    fun clearSyncedFeedback() {
        database.transaction {
            database.sessionDatabaseQueries.deleteCompletedFeedback()
        }
    }

    fun clearSyncedFavorites() {
        database.transaction {
            database.sessionDatabaseQueries.deleteCompletedFavorites()
        }
    }

    fun clearSyncedSpeakers() {
        database.transaction {
            database.sessionDatabaseQueries.deleteCompletedSpeakers()
        }
    }

    fun clearSyncedRooms() {
        database.transaction {
            database.sessionDatabaseQueries.deleteCompletedRooms()
        }
    }

    fun clearSyncedCategories() {
        database.transaction {
            database.sessionDatabaseQueries.deleteCompletedCategories()
        }
    }

    fun clearSyncedSessionCategories() {
        database.transaction {
            database.sessionDatabaseQueries.deleteCompletedSessionCategories()
        }
    }

    fun clearSyncedSessionSpeakers() {
        database.transaction {
            database.sessionDatabaseQueries.deleteCompletedSessionSpeakers()
        }
    }
}