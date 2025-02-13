package org.jetbrains.kotlinconf

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinconfapp.shared.generated.resources.Res
import kotlinconfapp.shared.generated.resources.schedule_day_2_banner
import org.jetbrains.compose.resources.painterResource


@Composable
fun EditableBanner(
    dayText: String,
    monthText: String,
    dateText: String
) {
    // Total width is 412 dp, total height is 56 dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        // 1) The cleaned-up vector background (no text paths)
        Image(
            painter = painterResource(Res.drawable.schedule_day_2_banner),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.FillBounds
        )

        // 2) Three text fields laid out in the same spots as original
        Row(modifier = Modifier.matchParentSize()) {

            // FIRST shape (purple circle) was 56 px wide
            Spacer(
                modifier = Modifier
                    .weight(56f)
                    .fillMaxHeight()
            )

            // SECOND shape (the pink/orange bubble) was 107 px wide
            Box(
                modifier = Modifier
                    .weight(107f)
                    .fillMaxHeight()
            ) {
                Text(
                    text = dayText,
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // THIRD shape (the orange bubble) was 99 px wide
            Box(
                modifier = Modifier
                    .weight(99f)
                    .fillMaxHeight()
            ) {
                Text(
                    text = monthText,
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // FOURTH shape (the smaller gold circle) was 56 px wide
            Box(
                modifier = Modifier
                    .weight(56f)
                    .fillMaxHeight()
            ) {
                Text(
                    text = dateText,
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // LAST shape (the right orange bubble) was 94 px wide
            Spacer(
                modifier = Modifier
                    .weight(94f)
                    .fillMaxHeight()
            )
        }
    }
}

