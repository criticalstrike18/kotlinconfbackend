package org.jetbrains.kotlinApp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.jetbrains.kotlinApp.AppController
import org.jetbrains.kotlinApp.Speaker
import org.jetbrains.kotlinApp.ui.components.AsyncImage
import org.jetbrains.kotlinApp.ui.components.NavigationBar
import org.jetbrains.kotlinApp.ui.theme.subtitle
import org.jetbrains.kotlinApp.ui.theme.title
import org.jetbrains.kotlinApp.ui.theme.whiteGrey
import org.jetbrains.kotlinApp.utils.Screen
import org.jetbrains.kotlinApp.utils.isTooWide

@Composable
fun SpeakersScreen(speakers: List<Speaker>, scrollState: LazyListState, controller: AppController) {
    Column(Modifier.background(MaterialTheme.colors.whiteGrey)) {
        NavigationBar(
            title = "Speakers",
            isLeftVisible = false,
            isRightVisible = false
        )

        LazyColumn(Modifier.background(MaterialTheme.colors.whiteGrey), state = scrollState) {
            items(speakers) { speaker ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colors.whiteGrey)
                ) {
                    SpeakerCard(
                        name = speaker.name,
                        position = speaker.position,
                        photoUrl = speaker.photoUrl,
                        onClick = {
                            controller.showSpeaker(speaker.id)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SpeakerCard(
    name: String,
    position: String,
    photoUrl: String,
    onClick: () -> Unit = {}
) {
    val screenSizeIsTooWide = Screen.isTooWide()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.whiteGrey)
            .padding(0.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.whiteGrey)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    modifier = Modifier
                        .size(if (screenSizeIsTooWide) 170.dp else 85.dp)
                        .padding(0.dp),
                    imageUrl = photoUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                )
                Column(
                    modifier = Modifier
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.h4.copy(color = MaterialTheme.colors.title),
                        maxLines = 1
                    )
                    Text(
                        text = position,
                        style = MaterialTheme.typography.body2.copy(color = MaterialTheme.colors.subtitle),
                        maxLines = 2
                    )
                }
            }

            HDivider()
        }
    }
}
