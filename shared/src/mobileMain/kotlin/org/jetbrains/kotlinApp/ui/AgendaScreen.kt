package org.jetbrains.kotlinApp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinconfapp.shared.generated.resources.Res
import kotlinconfapp.shared.generated.resources.lunch
import kotlinconfapp.shared.generated.resources.lunch_active
import kotlinx.coroutines.launch
import org.jetbrains.kotlinApp.Agenda
import org.jetbrains.kotlinApp.AppController
import org.jetbrains.kotlinApp.Day
import org.jetbrains.kotlinApp.EventDay
import org.jetbrains.kotlinApp.TimeSlot
import org.jetbrains.kotlinApp.ui.components.AgendaDayHeader
import org.jetbrains.kotlinApp.ui.components.AgendaItem
import org.jetbrains.kotlinApp.ui.components.AgendaTimeSlotHeader
import org.jetbrains.kotlinApp.ui.components.Break
import org.jetbrains.kotlinApp.ui.components.LiveIndicator
import org.jetbrains.kotlinApp.ui.components.Party
import org.jetbrains.kotlinApp.ui.components.TabBar
import org.jetbrains.kotlinApp.ui.theme.agendaHeaderColor
import org.jetbrains.kotlinApp.ui.theme.blackWhite
import org.jetbrains.kotlinApp.ui.theme.grey5Black

@Composable
fun AgendaScreen(agenda: Agenda, scrollState: LazyListState, controller: AppController) {
    val coroutineScope = rememberCoroutineScope()
    val daysSize = agenda.days.map { it.itemsCount() }
    val daysIndex: List<Int> = daysSize.scan(0) { acc, i -> acc + i }

    // Track the last known visible day to prevent disappearing
    val lastKnownDay = remember { mutableStateOf<EventDay?>(EventDay.Today) }

    val displayedDay: EventDay? by remember {
        derivedStateOf {
            // Get all visible item keys
            val keys = scrollState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }

            // Extract day information from keys
            val days = keys.mapNotNull { key ->
                when {
                    key.startsWith("header-") -> {
                        // Day headers (explicit format)
                        val dayString = key.substringAfter("header-")
                        EventDay.entries.find { it.name == dayString }
                    }
                    key.contains("-day") -> {
                        // Items with day info embedded
                        val dayIndex = key.lastIndexOf("-day")
                        if (dayIndex >= 0) {
                            val dayString = key.substring(dayIndex + 1)
                            EventDay.entries.find { it.name == dayString }
                        } else null
                    }
                    else -> null
                }
            }.toSet()

            // If we found visible days, use the topmost one
            if (days.isNotEmpty()) {
                val currentDay = days.minBy { it.ordinal }
                lastKnownDay.value = currentDay // Update last known day
                currentDay
            } else {
                // If no day headers visible, use last known day
                lastKnownDay.value
            }
        }
    }

    val liveSlot = agenda.days.flatMap { it.timeSlots }.firstOrNull { it.isLive }

    fun scrollToDay(day: EventDay) {
        val dayIndex = agenda.days.indexOfFirst { it.day == day }
        if (dayIndex < 0) return // Not found, do nothing

        // daysIndex[dayIndex] is the cumulative position of that day’s header
        val index = daysIndex[dayIndex]
        // The old code subtracted day.ordinal. That was for a date-based logic.
        // If you still want it, keep it; but usually just the 'index' is enough.
        // index -= day.ordinal

        if (index >= 0) {
            coroutineScope.launch { scrollState.scrollToItem(index, 0) }
        }
    }

    LaunchedEffect(Unit) {
        if (scrollState.firstVisibleItemIndex == 0 && scrollState.firstVisibleItemScrollOffset == 0) {
            scrollToDay(EventDay.Today)
        }
    }

    Column(
        Modifier.background(MaterialTheme.colors.agendaHeaderColor)
    ) {
        val day = displayedDay
        if (day != null) {
            TabBar(
                tabs = EventDay.entries.toList(), // [Passed, Today, Tomorrow, Later]
                selected = day,
                onSelect = ::scrollToDay
            )
        }
        if (liveSlot != null) {
            LiveHeader(liveSlot) {
                coroutineScope.launch {
                    scrollState.scrollToItem(agenda.firstLiveIndex(), 0)
                }
            }
            HDivider()
        }
        LazyColumn(state = scrollState) {
            agenda.days.forEach {
                SessionsList(day = it, controller = controller)
            }
        }
    }
}

@Composable
private fun LiveHeader(liveSlot: TimeSlot, scrollToLive: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colors.grey5Black).height(52.dp)
            .clickable { scrollToLive() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            liveSlot.title,
            style = MaterialTheme.typography.h3.copy(color = MaterialTheme.colors.blackWhite),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        Spacer(Modifier.weight(1f))
        LiveIndicator()
    }
}

private fun LazyListScope.SessionsList(
    day: Day,
    controller: AppController,
) {
    // The day name will be used in all keys
    val dayName = day.day.name

    // 1) Insert a day header
    item(key = "header-$dayName") {
        AgendaDayHeader(day)
    }

    // 2) If no time slots => show a "No events here" row
    if (day.timeSlots.isEmpty()) {
        item(key = "no-events-$dayName") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No events here",
                    style = MaterialTheme.typography.subtitle1.copy(color = Color.Gray)
                )
            }
        }
        return
    }

    // 3) Process each time slot - embed day info in all keys
    day.timeSlots.forEach { slot ->
        when {
            slot.isLunch -> {
                if (slot.isFinished) return@forEach
                item(key = "${slot.key}-$dayName") {
                    Break(
                        duration = slot.duration,
                        title = slot.title,
                        isLive = slot.isLive,
                        icon = Res.drawable.lunch,
                        liveIcon = Res.drawable.lunch_active
                    )
                }
            }

            slot.isBreak -> {
                if (slot.isFinished) return@forEach
                item(key = "${slot.key}-$dayName") {
                    Break(
                        duration = slot.duration,
                        title = slot.title,
                        isLive = slot.isLive
                    )
                }
            }

            slot.isParty -> {
                item(key = "${slot.key}-$dayName") {
                    Column {
                        AgendaTimeSlotHeader(slot.title, slot.isLive, slot.isFinished)
                        Party(slot.title, slot.isFinished)
                    }
                }
            }

            else -> {
                // Regular session time slot
                item(key = "${slot.key}-$dayName") {
                    AgendaTimeSlotHeader(
                        slot.title, slot.isLive, slot.isFinished
                    )
                }

                // Each session also gets the day embedded in its key
                items(
                    items = slot.sessions,
                    key = { "${it.key}-$dayName" }
                ) { session ->
                    AgendaItem(
                        title = session.title,
                        speakerLine = session.speakerLine,
                        locationLine = session.locationLine,
                        timeLine = session.badgeTimeLine,
                        isFavorite = session.isFavorite,
                        isFinished = session.isFinished,
                        isLightning = session.isLightning,
                        vote = session.vote,
                        onSessionClick = {
                            controller.showSession(session.id)
                        },
                        onFavoriteClick = {
                            controller.toggleFavorite(session.id)
                        },
                        onVote = {
                            controller.vote(session.id, it)
                        }
                    ) {
                        controller.sendFeedback(session.id, it)
                    }
                }
            }
        }
    }
}

private fun Day.itemsCount(): Int {
    val sessions =
        timeSlots.flatMap { it.sessions }.filterNot { (it.isLunch || it.isBreak || it.isParty) }
            .count()

    val slots = timeSlots.filterNot { (it.isLunch || it.isBreak || it.isParty) }.count()

    val lunches = timeSlots.filterNot { it.isFinished }.count { it.isLunch || it.isBreak }

    val party = 1
    val dayHeader = 1
    return sessions + slots + lunches + party + dayHeader
}

private fun Agenda.firstLiveIndex(): Int {
    var index = 0
    for (day in days) {
        index += 1 // Day header

        for (slot in day.timeSlots) {
            if (slot.isLive) return index

            if (slot.isFinished && (slot.isLunch || slot.isBreak)) {
                continue
            }

            index += slot.sessions.size + 1 // Time slot header
        }
    }

    return 0
}