package org.jetbrains.kotlinconf

import androidx.compose.runtime.Composable
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Composable
actual fun PlatformDateTimePicker(
    label: String,
    selectedDate: Instant?,
    onDateSelected: (Instant) -> Unit,
    showPicker: Boolean,
    onShowPickerChange: (Boolean) -> Unit,
) {
}

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