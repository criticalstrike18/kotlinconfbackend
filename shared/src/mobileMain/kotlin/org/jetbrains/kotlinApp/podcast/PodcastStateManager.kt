package org.jetbrains.kotlinApp.podcast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.kotlinApp.ApplicationContext
import org.jetbrains.kotlinApp.Database.DatabaseWrapper
import org.jetbrains.kotlinApp.Database.DriverFactory
import org.jetbrains.kotlinApp.DatabaseStorage
import org.jetbrains.kotlinApp.SessionDatabase

class PodcastStateManager(
    private val context: ApplicationContext,
    private val scope: CoroutineScope
) {
    private val driver = DriverFactory(context).createDriver()
    private val database = SessionDatabase(driver)
    private val databaseWrapper = DatabaseWrapper(database)
    private val storage: DatabaseStorage = DatabaseStorage(database, databaseWrapper)

    // StateFlow to observe current playback state
    private val _playbackState = MutableStateFlow(
        PodcastPlaybackState(
        episodeId = null,
        channelId = null,
        position = 0L,
        url = null,
        speed = 1.0,
        isBoostEnabled = false
    )
    )
    val playbackState: StateFlow<PodcastPlaybackState> = _playbackState.asStateFlow()

    // Save state periodically
    init {
        scope.launch {
            while (true) {
                delay(5000) // Save every 5 seconds
                saveCurrentState()
            }
        }
    }

    suspend fun saveCurrentState() {
        _playbackState.value.let { state ->
            storage.savePlaybackState(state)
        }
    }

    suspend fun loadSavedState(): PodcastPlaybackState {
        return storage.loadPlaybackState() ?: PodcastPlaybackState(
            episodeId = null,
            channelId = null,
            position = 0L,
            url = null,
            speed = 1.0,
            isBoostEnabled = false
        )
    }

    fun updateState(newState: PodcastPlaybackState) {
        _playbackState.value = newState
    }

    // Add method to update just the position
    fun updatePosition(position: Long) {
        _playbackState.value = _playbackState.value.copy(
            position = position
        )
    }
}