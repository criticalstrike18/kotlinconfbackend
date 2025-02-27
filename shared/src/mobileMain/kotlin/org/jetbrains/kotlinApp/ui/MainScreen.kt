package org.jetbrains.kotlinApp.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import kotlinconfapp.shared.generated.resources.Res
import kotlinconfapp.shared.generated.resources.location
import kotlinconfapp.shared.generated.resources.location_active
import kotlinconfapp.shared.generated.resources.menu
import kotlinconfapp.shared.generated.resources.menu_active
import kotlinconfapp.shared.generated.resources.mytalks
import kotlinconfapp.shared.generated.resources.mytalks_active
import kotlinconfapp.shared.generated.resources.speakers
import kotlinconfapp.shared.generated.resources.speakers_active
import kotlinconfapp.shared.generated.resources.time
import kotlinconfapp.shared.generated.resources.time_active
import org.jetbrains.kotlinApp.AppController
import org.jetbrains.kotlinApp.AppInitState
import org.jetbrains.kotlinApp.ConferenceService
import org.jetbrains.kotlinApp.DataInitializationScreen
import org.jetbrains.kotlinApp.podcast.PodcastViewModel
import org.jetbrains.kotlinApp.ui.components.TabItem
import org.jetbrains.kotlinApp.ui.components.TabsView
import org.jetbrains.kotlinApp.ui.podcast.PodcastContainer
import org.jetbrains.kotlinApp.ui.welcome.WelcomeScreen


@Composable
fun MainScreen(service: ConferenceService, podcastViewModel: PodcastViewModel) {
    val agenda by service.agenda.collectAsState()
    val speakers by service.speakers.collectAsState()
    val time by service.time.collectAsState()
    val navigator = rememberNavController()
    val controller = remember { AppController(service, podcastViewModel) }

    // Get the current app initialization state with key to force recomposition
    val appInitState by service.appInitState.collectAsState()

    // Add logging to track state changes
    LaunchedEffect(appInitState) {
        println("MainScreen - Current AppInitState: $appInitState")
    }

    // Show the appropriate screen based on initialization state
    when (appInitState) {
        AppInitState.WELCOME -> {
            WelcomeScreen(
                onAcceptNotifications = {
                    service.requestNotificationPermissions()
                },
                onAcceptPrivacy = {
                    service.acceptPrivacyPolicy()
                },
                onClose = {
                    service.completeOnboarding()
                },
                onRejectPrivacy = {}
            )
        }

        AppInitState.INITIALIZING -> {
            // Force key to ensure proper recomposition
            key(System.currentTimeMillis()) {
                DataInitializationScreen(service)
            }
        }

        AppInitState.READY -> {
            // MainApp UI - Force key to ensure proper recomposition
            key(System.currentTimeMillis()) {
                val agendaScrollState = rememberLazyListState()
                val speakersScrollState = rememberLazyListState()

                TabsView(
                    controller,
                    navigator,
                    TabItem("menu", Res.drawable.menu, Res.drawable.menu_active) {
                        MenuScreen(controller)
                    },
                    TabItem("agenda", Res.drawable.time, Res.drawable.time_active) {
                        AgendaScreen(agenda, agendaScrollState, controller)
                    },
                    TabItem(
                        "speakers", Res.drawable.speakers, Res.drawable.speakers_active
                    ) {
                        SpeakersScreen(speakers.all, speakersScrollState, controller)
                    },
                    TabItem(
                        "bookmarks", Res.drawable.mytalks, Res.drawable.mytalks_active
                    ) {
                        val favoriteSessions = agenda.days.flatMap { it.timeSlots.flatMap { it.sessions } }
                            .filter { it.isFavorite }.map {
                                val startsIn = ((it.startsAt.timestamp - time.timestamp) / 1000 / 60).toInt()
                                when {
                                    startsIn in 1..15 -> it.copy(timeLine = "In $startsIn min!")
                                    startsIn <= 0 && !it.isFinished -> it.copy(timeLine = "NOW")
                                    else -> it
                                }
                            }

                        BookmarksScreen(favoriteSessions, controller)
                    },
                    TabItem(
                        "podcasts", Res.drawable.location, Res.drawable.location_active
                    ) {
                        PodcastContainer(
                            podcastViewModel = podcastViewModel,
                            service = service
                        )
                    }
                )
            }
        }
    }
}