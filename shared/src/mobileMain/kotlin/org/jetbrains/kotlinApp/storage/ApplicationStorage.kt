package org.jetbrains.kotlinApp.storage

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinApp.ApplicationContext
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

expect fun ApplicationStorage(context: ApplicationContext): ApplicationStorage

interface ApplicationStorage {
    fun putBoolean(key: String, value: Boolean)
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putString(key: String, value: String)
    fun getString(key: String): String?
}

inline fun <reified T> ApplicationStorage.put(key: String, value: T) {
    putString(key, Json.encodeToString(value))
}

inline fun <reified T> ApplicationStorage.get(key: String): T? {
    val value = getString(key) ?: return null
    return runCatching {
        Json.decodeFromString<T>(value)
    }.getOrNull()
}

inline fun <reified T> ApplicationStorage.bind(
    serializer: KSerializer<T>,
    crossinline block: () -> T
): ReadWriteProperty<Any, T> = object : ReadWriteProperty<Any, T> {
    private var currentValue: T? = null

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        val key = property.name
        currentValue = value
        putString(key, Json.encodeToString(serializer, value))
    }

    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        currentValue?.let { return it }

        val key = property.name
        val value = getString(key)
        val result = runCatching {
            value?.let { Json.decodeFromString(serializer, it) }
        }.getOrNull() ?: block()

        setValue(thisRef, property, result)
        return result
    }
}

/**
 * Gets a Long value from storage with a default if not found.
 */
fun ApplicationStorage.getLong(key: String, defaultValue: Long = 0L): Long {
    val stringValue = getString(key)
    return if (stringValue.isNullOrEmpty()) {
        defaultValue
    } else {
        try {
            stringValue.toLong()
        } catch (e: NumberFormatException) {
            defaultValue
        }
    }
}

/**
 * Puts a Long value into storage.
 */
fun ApplicationStorage.putLong(key: String, value: Long) {
    putString(key, value.toString())
}

/**
 * Reset sync timestamps - useful when debugging or after major app updates.
 */
fun ApplicationStorage.resetSyncTimestamps() {
    val syncKeys = listOf(
        "last_session_pull_time",
        "last_speaker_pull_time",
        "last_category_pull_time",
        "last_room_pull_time",
        "last_podcast_pull_time"
    )
}

