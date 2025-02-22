package org.jetbrains.kotlinApp

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime


@Composable
actual fun PlatformTimePicker(
    initialTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    onDismissRequest: () -> Unit,
) {
}

@Composable
actual fun PlatformDatePicker(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismissRequest: () -> Unit,
) {
}