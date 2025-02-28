package org.jetbrains.kotlinApp.podcast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.kotlinApp.ApplicationContext
import org.jetbrains.kotlinApp.Database.DatabaseWrapper
import org.jetbrains.kotlinApp.Database.DriverFactory
import org.jetbrains.kotlinApp.DatabaseStorage
import org.jetbrains.kotlinApp.SessionDatabase

class PodcastService(context: ApplicationContext) {
    private val audioPlayer = createAudioPlayer(context)
    private val driver = DriverFactory(context).createDriver()
    private val database = SessionDatabase(driver)
    private val databaseWrapper = DatabaseWrapper(database)
    private val dbStorage: DatabaseStorage = DatabaseStorage(database, databaseWrapper)
    private val stateManager = PodcastStateManager(
        context = context,
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    )
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
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
    private var isInitialized = false

    init {
        var pollingJob: Job? = null
        var stateRestorationJob: Job? = null

        try {
            // Start audio polling
            pollingJob = serviceScope.launch {
                while (true) {
                    if (_playerState.value.isPlaying) {
                        try {
                            val (pos, dur) = withContext(Dispatchers.Main) {
                                Pair(
                                    audioPlayer.getCurrentPosition(),
                                    audioPlayer.getDuration()
                                )
                            }

                            withContext(Dispatchers.Default) {
                                updatePlayerState { state ->
                                    state.copy(
                                        currentPosition = pos,
                                        duration = dur
                                    )
                                }

                                updatePlaybackState { state ->
                                    state.copy(position = pos)
                                }
                                val ep = _playerState.value.currentEpisode
                                if (ep != null) {
                                    dbStorage.updateEpisodePosition(ep.id.toLong(), pos)
                                }
                            }
                        } catch (e: Exception) {
                            println("Polling error: ${e.message}")
                        }
                    }
                    delay(500L)
                }
            }

            // Restore saved state
            stateRestorationJob = serviceScope.launch {
                try {
                    val savedState = stateManager.loadSavedState()
                    savedState.episodeId?.let { episodeId ->
                        val episode = dbStorage.getEpisodeByIdFlow(episodeId.toLong())
                            .first()
                        resumePlayback(episode, savedState)
                    }
                } catch (e: Exception) {
                    println("State restoration error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("Initialization error: ${e.message}")
            pollingJob?.cancel()
            stateRestorationJob?.cancel()
        }
    }

    private fun handlePlaybackError(error: Exception) {
        println("Playback error: ${error.message}")
        _playerState.value = _playerState.value.copy(isPlaying = false)

        // Reset initialization state
        isInitialized = false
    }

    private fun resumePlayback(
        episode: PodcastEpisode,
        savedState: PodcastPlaybackState
    ) {
        mainScope.launch {
            try {
                // First, restore player settings without starting playback
                audioPlayer.setPlaybackSpeed(savedState.speed.toFloat())
                audioPlayer.enableAudioBoost(savedState.isBoostEnabled)

                // Prepare the player but don't start playing
                audioPlayer.prepare(savedState.url ?: episode.audioUrl, savedState.position)

                // Update state to reflect the restored episode but keep isPlaying false
                withContext(Dispatchers.Default) {
                    val channel = getChannelById(episode.channelId)
                    updatePlayerState { state ->
                        PlayerState(
                            isPlaying = false, // Keep it paused
                            currentPosition = savedState.position,
                            currentEpisode = episode,
                            currentChannel = channel
                        )
                    }
                }
            } catch (e: Exception) {
                println("Error restoring playback state: ${e.message}")
                handlePlaybackError(e)
            }
        }
    }

    private suspend fun getChannelById(channelId: String): PodcastChannel? {
        return try {
            // Then get the channel using the episode's channelId
            val channel = dbStorage.getChannelByIdFlow(channelId.toLong())
                .first()

            // Map to domain model
            PodcastChannel(
                id = channel.id.toString(),
                title = channel.title,
                description = channel.description,
                imageUrl = channel.imageUrl
            )
        } catch (e: Exception) {
            println("Error getting channel for episode $channelId: ${e.message}")
            null
        }
    }

    fun play(episode: PodcastEpisode) {
        try {
            mainScope.launch {
                // 1) Fetch any previously saved position from the DB:
                val storedPosition = withContext(Dispatchers.Default) {
                    dbStorage.getEpisodePosition(episode.id.toLong()) ?: 0L
                }

                // 2) Now actually prepare + start playback from that position:
                audioPlayer.play(episode.audioUrl, startPosition = storedPosition)
                isInitialized = true

                val duration = audioPlayer.getDuration()

                // 3) Update your in-memory state objects
                withContext(Dispatchers.Default) {
                    val channel = getChannelById(episode.channelId)
                    updatePlayerState { state ->
                        state.copy(
                            isPlaying = true,
                            currentEpisode = episode,
                            currentPosition = storedPosition,
                            duration = duration,
                            currentChannel = channel
                        )
                    }
                    // Also update your stored “last” playback state if you wish
                    stateManager.updateState(
                        PodcastPlaybackState(
                            episodeId = episode.id,
                            channelId = episode.channelId,
                            position = storedPosition,
                            url = episode.audioUrl,
                            speed = 1.0,
                            isBoostEnabled = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            handlePlaybackError(e)
        }
    }

    fun pause() {
        mainScope.launch {
            audioPlayer.pause()

            val currentPosition = audioPlayer.getCurrentPosition()

            withContext(Dispatchers.Default) {
                updatePlayerState { it.copy(isPlaying = false) }
                updatePlaybackState { it.copy(position = currentPosition) }
                stateManager.saveCurrentState()
            }
        }
    }

    fun stop() {
        audioPlayer.stop()
        _playerState.value = PlayerState()
    }

    fun setSpeed(speed: Float) {
        audioPlayer.setPlaybackSpeed(speed)
        _playbackState.value = _playbackState.value.copy(
            speed = speed.toDouble()
        )
    }

    fun enableBoost(enabled: Boolean) {
        audioPlayer.enableAudioBoost(enabled)
        _playbackState.value = _playbackState.value.copy(
            isBoostEnabled = enabled
        )
    }

    fun seekTo(position: Long) {
        mainScope.launch {
            audioPlayer.seekTo(position)

            withContext(Dispatchers.Default) {
                updatePlayerState { it.copy(currentPosition = position) }
                updatePlaybackState { it.copy(position = position) }
                stateManager.saveCurrentState()
            }
        }
    }

    fun release() {
        mainScope.cancel()
        serviceScope.cancel()
        audioPlayer.release()
        isInitialized = false
    }

    private val stateMutex = Mutex()

    private suspend fun updatePlayerState(update: (PlayerState) -> PlayerState) {
        stateMutex.withLock {
            _playerState.value = update(_playerState.value)
        }
    }

    private suspend fun updatePlaybackState(update: (PodcastPlaybackState) -> PodcastPlaybackState) {
        stateMutex.withLock {
            _playbackState.value = update(_playbackState.value)
        }
    }
}