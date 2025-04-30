package org.jetbrains.kotlinApp.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinconfapp.shared.generated.resources.Res
import kotlinconfapp.shared.generated.resources.X
import kotlinconfapp.shared.generated.resources.about_conference
import kotlinconfapp.shared.generated.resources.arrow_right
import kotlinconfapp.shared.generated.resources.hashtag
import kotlinconfapp.shared.generated.resources.menu
import kotlinconfapp.shared.generated.resources.mobile_app
import kotlinconfapp.shared.generated.resources.search
import kotlinconfapp.shared.generated.resources.slack
import kotlinconfapp.shared.generated.resources.x
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinApp.AppController
import org.jetbrains.kotlinApp.ui.components.BigMenuItem
import org.jetbrains.kotlinApp.ui.components.MenuItem
import org.jetbrains.kotlinApp.ui.components.MenuLogo
import org.jetbrains.kotlinApp.ui.components.NavigationBar
import org.jetbrains.kotlinApp.ui.theme.grey20Grey80

@Composable
fun MenuScreen(controller: AppController) {
    val uriHandler = LocalUriHandler.current
    controller.refreshData()
    Column(Modifier.fillMaxWidth()) {
        NavigationBar(
            title = stringResource(Res.string.menu),
            isLeftVisible = false,
            isRightVisible = false
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.grey20Grey80)
        ) {
            item(span = { GridItemSpan(2) }) {
                Column {
                    MenuLogo()
                    HDivider()
                    MenuItem(text = stringResource(Res.string.search), icon = Res.drawable.search) {
                        controller.showSearch()
                    }
                    HDivider()
                    MenuItem(
                        text = stringResource(Res.string.about_conference),
                        icon = Res.drawable.arrow_right
                    ) {
                        controller.showAboutTheConf()
                    }
                    HDivider()
                    MenuItem(
                        text = stringResource(Res.string.mobile_app),
                        icon = Res.drawable.arrow_right
                    ) {
                        controller.showAppInfo()
                    }
                    HDivider()
                    MenuItem(
                        text = "Session Form",
                        icon = Res.drawable.arrow_right
                    ) {
                        controller.showSessionForm()
                    }
                    HDivider()
                    MenuItem(
                        text = "Request Podcast",
                        icon = Res.drawable.arrow_right
                    ) {
                        controller.showPodcastRequestForm()
                    }
                }
            }

            item {
                BigMenuItem(
                    stringResource(Res.string.X),
                    stringResource(Res.string.hashtag),
                    Res.drawable.x
                ) {
                    uriHandler.openUri("https://twitter.com/hashtag/KotlinConf")
                }
            }
            item {
                BigMenuItem(stringResource(Res.string.slack), "", Res.drawable.slack) {
                    uriHandler.openUri("https://kotlinlang.slack.com/messages/kotlinconf/")
                }
            }
            item(span = { GridItemSpan(2) }) {
                // last divider
            }
        }
    }
}
