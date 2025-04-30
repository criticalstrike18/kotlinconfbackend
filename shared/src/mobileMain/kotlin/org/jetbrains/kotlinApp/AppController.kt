package org.jetbrains.kotlinApp

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.kotlinApp.podcast.PodcastViewModel
import org.jetbrains.kotlinApp.ui.AboutAppScreen
import org.jetbrains.kotlinApp.ui.AboutConfScreen
import org.jetbrains.kotlinApp.ui.AppPrivacyPolicyScreen
import org.jetbrains.kotlinApp.ui.AppTermsOfUseScreen
import org.jetbrains.kotlinApp.ui.CodeOfConductScreen
import org.jetbrains.kotlinApp.ui.Partner
import org.jetbrains.kotlinApp.ui.Partners
import org.jetbrains.kotlinApp.ui.SearchScreen
import org.jetbrains.kotlinApp.ui.SessionScreen
import org.jetbrains.kotlinApp.ui.SpeakersDetailsScreen
import org.jetbrains.kotlinApp.ui.VisitorsPrivacyPolicyScreen
import org.jetbrains.kotlinApp.ui.VisitorsTermsScreen
import org.jetbrains.kotlinApp.ui.components.NavigationBar
import org.jetbrains.kotlinApp.ui.podcast.ChannelScreen
import org.jetbrains.kotlinApp.ui.podcast.FullScreenPlayer
import org.jetbrains.kotlinApp.ui.podcast.PodcastScreen
import org.jetbrains.kotlinApp.ui.welcome.WelcomeScreen
import org.jetbrains.kotlinApp.utils.StateFlowClass

typealias View = @Composable (AppController) -> Unit

class AppController(internal val service: ConferenceService, private val podcastViewModel: PodcastViewModel) {
    private val stack = mutableListOf<View>()
    val last: MutableStateFlow<View?> = MutableStateFlow(null)
    val sessions: StateFlowClass<List<SessionCardView>> = service.sessionCards

    private val currentRoute = MutableStateFlow<String>("")

    fun routeTo(value: String) {
        if (value == currentRoute.value) return

        stack.clear()
        last.value = null
        currentRoute.value = value
    }

    fun push(item: @Composable (AppController) -> Unit) {
        stack.add(item)
        last.value = item
    }

    fun showSession(sessionId: String) {
        push {
            val session: SessionCardView? =
                service.sessionCards.collectAsState().value.firstOrNull { it.id == sessionId }
            val speakers = session?.speakerIds?.map { service.speakerById(it) }
            if (session != null && speakers != null) {
                SessionScreen(
                    id = session.id,
                    time = session.timeLine,
                    title = session.title,
                    description = session.description,
                    location = session.locationLine,
                    isFavorite = session.isFavorite,
                    vote = session.vote,
                    isFinished = session.isFinished,
                    speakers = speakers,
                    tags = session.tags,
                    controller = this
                )
            }
        }
    }

    fun showSpeaker(speakerId: String) {
        push {
            val speakers by service.speakers.collectAsState()

            SpeakersDetailsScreen(
                controller = this, speakers = speakers.all, focusedSpeakerId = speakerId
            )
        }
    }

    private fun showPodcastFullPlayer() {
        push {
            // Collect state from the podcastViewModel
            val playerState by podcastViewModel.playerState.collectAsState()
            val playbackState by podcastViewModel.playbackState.collectAsState()

            FullScreenPlayer(
                playerState = playerState,
                playbackState = playbackState,
                onPlayPause = {
                    // Toggle play/pause based on the current episode state
                    playerState.currentEpisode?.let { episode ->
                        if (playerState.isPlaying) podcastViewModel.pause()
                        else podcastViewModel.play(episode)
                    }
                },
                onMinimize = { back() },  // Minimize action simply pops the current view
                onSeek = podcastViewModel::seekTo,
                onSpeedChange = podcastViewModel::setSpeed,
                onBoostChange = podcastViewModel::enableBoost
            )
        }
    }


    fun showPodcastScreen(channelId: Long) {
        push {
            // We collect the relevant state from the ViewModel
            val playerState by podcastViewModel.playerState.collectAsState()
            val playbackState by podcastViewModel.playbackState.collectAsState()

            // Ensure we have the channel data and episodes preloaded
            LaunchedEffect(channelId) {
                // This ensures the channel is loaded with all details
                service.ensureChannelLoaded(channelId)

                // Load episodes for this channel
                service.loadEpisodesForChannel(channelId)
            }

            PodcastScreen(
                service = service,
                playerState = playerState,
                playbackState = playbackState,
                onPlayPause = { episode ->
                    if (playerState.currentEpisode?.id == episode.id) {
                        if (playerState.isPlaying) podcastViewModel.pause()
                        else podcastViewModel.play(episode)
                    } else {
                        podcastViewModel.play(episode)
                    }
                },
                channelId = channelId,
                onBackPress = { back() },
                onExpandPlayer = {
                    showPodcastFullPlayer()
                },
                onSeek = podcastViewModel::seekTo,
                onSpeedChange = podcastViewModel::setSpeed,
                onBoostChange = podcastViewModel::enableBoost
            )
        }
    }

    fun showPodcastChannels() {
        push {
            // We collect state from the podcastViewModel available in the controller.
            val playerState by podcastViewModel.playerState.collectAsState()
            val playbackState by podcastViewModel.playbackState.collectAsState()

            ChannelScreen(
                service = service,
                playerState = playerState,
                playbackState = playbackState,
                onPlayPause = { episode ->
                    if (playerState.currentEpisode?.id == episode.id) {
                        if (playerState.isPlaying) podcastViewModel.pause()
                        else podcastViewModel.play(episode)
                    } else {
                        podcastViewModel.play(episode)
                    }
                },
                onSeek = podcastViewModel::seekTo,
                onSpeedChange = podcastViewModel::setSpeed,
                onBoostChange = podcastViewModel::enableBoost,
                onNavigateToEpisodes = { channelId ->
                    // Push a new PodcastScreen showing episodes for this channel.
                    showPodcastScreen(channelId)
                },
                onExpandPlayer = {
                    // Push the full-screen player when user expands the mini-player.
                    showPodcastFullPlayer()
                }
            )
        }
    }


    fun back() {
        if (stack.isEmpty()) return

        stack.removeAt(stack.size - 1)
        last.value = stack.lastOrNull()
    }

    fun toggleFavorite(sessionId: String) {
        service.toggleFavorite(sessionId)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun vote(sessionId: String, it: Score?) {
        GlobalScope.launch {
            if (!service.vote(sessionId, it)) showPrivacyPolicyPrompt()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun sendFeedback(sessionId: String, feedback: String) {
        GlobalScope.launch {
            if (!service.sendFeedback(sessionId, feedback)) showPrivacyPolicyPrompt()
        }
    }

    private fun showPrivacyPolicyPrompt() {
        push {
            WelcomeScreen(onAcceptPrivacy = {
                service.acceptPrivacyPolicy()
                it.back()
            }, onRejectPrivacy = {
                it.back()
            }, onClose = {}, onAcceptNotifications = {})
        }
    }

    fun showSearch() {
        push {
//            val sessions by service.agenda.collectAsState()
//            val allSessions = sessions.days.flatMap { it.timeSlots }.flatMap { it.sessions }

//            val podcasts by service.podcastChannels.collectAsState()
//            val episodes by service.allEpisodes.collectAsState()

            SearchScreen(it, { back() })
        }
    }

    // Make sure your ConferenceService has these paginated search methods:

    fun showAppInfo() {
        push {
            AboutAppScreen(showAppPrivacyPolicy = { showAppPrivacyPolicy() },
                showAppTerms = { showAppTerms() },
                back = { back() })
        }
    }

    fun showPartners() {
        push {
            Partners({
                showPartner(it)
            }, {
                back()
            })
        }
    }

    fun showSessionForm() {
        push {
            SessionFormScreen(
                service = service,
                onSessionCreated = {
                    // Optionally show a confirmation message or refresh data
                    refreshData()
                },
                back = { back() }
            )
        }
    }

    fun showPodcastRequestForm() {
        push {
            PodcastRequestScreen(
                service = service,
                back = { back() }
            )
        }
    }

    fun showCodeOfConduct() {
        push {
            CodeOfConductScreen { it.back() }
        }
    }

    fun showPartner(partner: Partner) {
        push {
            Partner(it, partner)
        }
    }

    fun showAboutTheConf() {
        push {
            AboutConfScreen(service,
                showVisitorsPrivacyPolicy = { showVisitorsPrivacy() },
                showVisitorsTerms = { showVisitorsTerms() },
                back = { it.back() })
        }
    }

    fun showAppPrivacyPolicy() {
        push("Privacy policy") {
            AppPrivacyPolicyScreen(false) {}
        }
    }

    fun showAppTerms() {
        push("Terms of use") {
            AppTermsOfUseScreen()
        }
    }

    fun showVisitorsPrivacy() {
        push("Privacy policy") {
            VisitorsPrivacyPolicyScreen()
        }
    }

    fun showVisitorsTerms() {
        push("Terms and Conditions") {
            VisitorsTermsScreen()
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    private fun push(title: String, block: @Composable () -> Unit) {
        push {
            Column {
                NavigationBar(
                    title = title,
                    isRightVisible = false,
                    onLeftClick = { back() },
                )
                block()
            }
        }
    }

    fun refreshData() {
        // Trigger any necessary data refresh after session creation
        service.updateConferenceData()
    }

}