package org.jetbrains.kotlinconf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn


class ChannelsViewModel(
    private val repository: DatabaseStorage  // wrap around your DatabaseStorage functions
) : ViewModel() {

    // Convert the Flow to a StateFlow so the UI always has the current state.
    val channelsState: StateFlow<List<PodcastChannels>> =
        repository.getAllChannelsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
}


class EpisodesViewModel(
    private val repository: DatabaseStorage,
    private val channelId: Long
) : ViewModel() {

    val episodesState: StateFlow<List<PodcastEpisodes>> =
        repository.getEpisodesForChannelFlow(channelId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
}


class PlayerViewModel : ViewModel() {
    // Holds the currently playing episode
    private val _currentEpisode = MutableStateFlow<PodcastEpisodes?>(null)
    val currentEpisode: StateFlow<PodcastEpisodes?> = _currentEpisode

    // Call this when a user selects an episode to play.
    fun playEpisode(episode: PodcastEpisodes) {
        _currentEpisode.value = episode
        // Initialize your ExoPlayer and playback controls here.
    }
}