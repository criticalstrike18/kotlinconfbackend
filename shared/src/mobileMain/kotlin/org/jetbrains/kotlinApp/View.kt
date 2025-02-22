package org.jetbrains.kotlinApp

import io.ktor.util.date.GMTDate
import kotlinconfapp.shared.generated.resources.Res
import kotlinconfapp.shared.generated.resources.later
import kotlinconfapp.shared.generated.resources.passed
import kotlinconfapp.shared.generated.resources.today
import kotlinconfapp.shared.generated.resources.tomorrow
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.kotlinApp.ui.components.Tab
import org.jetbrains.kotlinApp.utils.time

data class Agenda(val days: List<Day> = emptyList())

data class Speakers(
    val all: List<Speaker> = emptyList()
) {
    private val dictById = all.associateBy { it.id }
    operator fun get(id: String): Speaker? = dictById[id]
}

enum class EventDay(override val title: StringResource) : Tab {
    Passed(Res.string.passed),
    Today(Res.string.today),
    Tomorrow(Res.string.tomorrow),
    Later(Res.string.later);

    companion object {
        /**
         * Decide the correct category for a Session relative to 'now'.
         * You can refine day/year logic if needed.
         */
        fun fromSession(session: Session, now: GMTDate): EventDay {
            val sessionEnds = session.endsAt
            val sessionStartDay = session.startsAt.dayOfYear
            val nowDay = now.dayOfYear

            return when {
                sessionEnds < now -> Passed
                sessionStartDay == nowDay -> Today
                sessionStartDay == nowDay + 1 -> Tomorrow
                else -> Later
            }
        }
    }
}


data class Day(
    val day: EventDay,          // Now holds 'Passed', 'Today', 'Tomorrow', or 'Later'
    val timeSlots: List<TimeSlot>
) : Tab {
    override val title: StringResource
        get() = day.title       // e.g. "Passed", "Today", etc.
}

data class TimeSlot(
    val startsAt: GMTDate,
    val endsAt: GMTDate,
    val isLive: Boolean,
    val isFinished: Boolean,
    val sessions: List<SessionCardView>,
    val isBreak: Boolean,
    val isLunch: Boolean,
    val isParty: Boolean
) {
    val title: String = if (isLunch || isBreak) {
        sessions.firstOrNull()?.title ?: ""
    } else {
        "${startsAt.time()}-${endsAt.time()}"
    }

    val key: String =
        "${startsAt.toLocalTime()}-${endsAt.toLocalTime()}-$title-$isBreak-$isParty-$isLunch-${startsAt.dayOfMonth}"

    val duration: String = "${(endsAt.timestamp - startsAt.timestamp) / 1000 / 60} MIN"
}

fun GMTDate.toLocalTime(): String {
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(timestamp)
    val localDateTime = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    return "${localDateTime.hour.toString().padStart(2, '0')}:${localDateTime.minute.toString().padStart(2, '0')}"
}

fun Conference.buildAgenda(
    favorites: Set<String>,
    votes: List<VoteInfo>,
    now: GMTDate
): Agenda {
    // 1) Group sessions by category:
    val bucketedSessions = sessions.groupBy { EventDay.fromSession(it, now) }

    // 2) Build four "Day" objects in a fixed order:
    val categoriesInOrder = listOf(
        EventDay.Passed,
        EventDay.Today,
        EventDay.Tomorrow,
        EventDay.Later
    )

    val days: List<Day> = categoriesInOrder.map { category ->
        val catSessions = bucketedSessions[category].orEmpty()
        val timeSlots = if (catSessions.isNotEmpty()) {
            catSessions.groupByTime(
                conference = this,
                now = now,
                favorites = favorites,
                votes = votes
            ).sortedBy { it.startsAt }
        } else emptyList()

        Day(
            day = category,    // Passed, Today, Tomorrow, or Later
            timeSlots = timeSlots
        )
    }

    return Agenda(days)
}

fun List<Session>.groupByTime(
    conference: Conference,
    now: GMTDate,
    favorites: Set<String>,
    votes: List<VoteInfo>,
): List<TimeSlot> {
    val slots = filterNot { it.isLightning }
        .map { it.startsAt to it.endsAt }
        .distinct()
        .sortedBy { it.first }

    return slots.map { (start, end) ->
        val cards: List<SessionCardView> = filter { it.startsAt >= start && it.endsAt <= end }
            .map {
                it.asSessionCard(conference, now, favorites, votes)
            }

        val isBreak = cards.all { it.isBreak }
        val isLunch = cards.all { it.isLunch }
        val isParty = cards.all { it.isParty }
        val isLive = start <= now && now < end
        val isFinished = end <= now

        TimeSlot(start, end, isLive, isFinished, cards, isBreak, isLunch, isParty)
    }
}

fun Session.asSessionCard(
    conference: Conference,
    now: GMTDate,
    favorites: Set<String>,
    votes: List<VoteInfo>,
): SessionCardView {
    val isFinished = endsAt <= now
    val vote = votes.find { it.sessionId == id }?.score
    return SessionCardView(
        id = id,
        title = title,
        speakerLine = speakerLine(conference),
        locationLine = location,
        isFavorite = favorites.contains(id),
        startsAt = startsAt,
        endsAt = endsAt,
        speakerIds = speakerIds,
        isFinished = isFinished,
        vote = vote,
        description = description,
        tags = tags ?: emptyList()
    )
}

fun Session.speakerLine(conference: Conference): String {
    val speakers = conference.speakers.filter { it.id in speakerIds }
    return speakers.joinToString { it.name }
}
