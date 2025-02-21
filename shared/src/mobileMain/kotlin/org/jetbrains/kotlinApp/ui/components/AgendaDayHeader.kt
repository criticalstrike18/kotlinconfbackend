package org.jetbrains.kotlinApp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.kotlinApp.Day
import org.jetbrains.kotlinApp.EditableBanner
import org.jetbrains.kotlinApp.EventDay
import org.jetbrains.kotlinApp.ui.theme.agendaHeaderColor



@Composable
internal fun AgendaDayHeader(day: Day) {
    // day.day = the category: Passed, Today, Tomorrow, or Later
    // day.timeSlots = all time slots for that category

    // 1) Find earliest timeslot’s date
    val earliestSlot = day.timeSlots.minByOrNull { it.startsAt }
    val dayOfMonth: String = earliestSlot?.startsAt?.dayOfMonth?.toString().orEmpty()
    val monthName: String = earliestSlot?.startsAt?.month?.name?.take(3).orEmpty()

    // 2) Decide the banner’s three text fields
    val (left, center, right) = when (day.day) {
        EventDay.Passed -> Triple("", "DONE", "")
        EventDay.Today -> Triple("TDY", monthName, dayOfMonth)
        EventDay.Tomorrow -> Triple("TMR", monthName, dayOfMonth)
        EventDay.Later -> Triple("", "LTR", "")
    }

    // 3) Layout the column + banner
    Column(
        modifier = Modifier
            .background(MaterialTheme.colors.agendaHeaderColor)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(top = 4.dp)
    ) {
        EditableBanner(
            dayText = left,      // e.g. "22"
            monthText = center,  // e.g. "TODAY" or "TMR" or "LATER"
            dateText = right     // e.g. "May"
        )
    }
}
