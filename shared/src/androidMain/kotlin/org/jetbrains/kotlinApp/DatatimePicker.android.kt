package org.jetbrains.kotlinApp

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.util.Calendar

//@Composable
//actual fun PlatformDateTimePicker(
//    label: String,
//    selectedDate: Instant?,
//    onDateSelected: (Instant) -> Unit,
//    showPicker: Boolean,
//    onShowPickerChange: (Boolean) -> Unit
//) {
//    val context = LocalContext.current
//
//    // Format the selected date for display
//    val displayText = remember(selectedDate) {
//        selectedDate?.toLocalDateTime(TimeZone.currentSystemDefault())?.let { dateTime ->
//            val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
//            formatter.format(java.time.LocalDateTime.of(
//                dateTime.year,
//                dateTime.monthNumber,
//                dateTime.dayOfMonth,
//                dateTime.hour,
//                dateTime.minute
//            ))
//        } ?: ""
//    }
//
//    OutlinedTextField(
//        value = displayText,
//        onValueChange = {},
//        readOnly = true,
//        label = { Text(label) },
//        modifier = Modifier.fillMaxWidth(),
//        trailingIcon = {
//            IconButton(onClick = { onShowPickerChange(true) }) {
//                Icon(Icons.Default.DateRange, contentDescription = "Pick Date and Time")
//            }
//        }
//    )
//
//    if (showPicker) {
//        // Get current date/time as default values
//        val currentDateTime = selectedDate
//            ?.toLocalDateTime(TimeZone.currentSystemDefault())
//            ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
//
//        DatePickerDialog(
//            context,
//            { _, year, month, day ->
//                // After date selection, show time picker
//                onShowPickerChange(false)
//
//                TimePickerDialog(
//                    context,
//                    { _, hour, minute ->
//                        val finalDateTime = LocalDateTime(
//                            year = year,
//                            monthNumber = month + 1,
//                            dayOfMonth = day,
//                            hour = hour,
//                            minute = minute,
//                            second = 0,
//                            nanosecond = 0
//                        ).toInstant(TimeZone.currentSystemDefault())
//
//                        onDateSelected(finalDateTime)
//                    },
//                    currentDateTime.hour,
//                    currentDateTime.minute,
//                    true
//                ).show()
//            },
//            currentDateTime.year,
//            currentDateTime.monthNumber - 1,
//            currentDateTime.dayOfMonth
//        ).show()
//    }
//}

@Composable
actual fun PlatformDatePicker(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current

    // Create calendar instance for initial date
    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, initialDate.year)
        set(Calendar.MONTH, initialDate.monthNumber - 1)
        set(Calendar.DAY_OF_MONTH, initialDate.dayOfMonth)
    }

    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onDateSelected(LocalDate(year, month + 1, dayOfMonth))
            onDismissRequest()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).apply {
        setOnDismissListener { onDismissRequest() }
        show()
    }
}

@Composable
actual fun PlatformTimePicker(
    initialTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current

    TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            onTimeSelected(LocalTime(hourOfDay, minute))
            onDismissRequest()
        },
        initialTime.hour,
        initialTime.minute,
        true // Use 24 hour format
    ).apply {
        setOnDismissListener { onDismissRequest() }
        show()
    }
}

// === Material 3 Version (Alternative Android Implementation) ===
// PlatformPickersMaterial3.android.kt
//
//import androidx.compose.material3.DatePicker
//import androidx.compose.material3.DatePickerDialog
//import androidx.compose.material3.DisplayMode
//import androidx.compose.material3.ExperimentalMaterial3Api
//import androidx.compose.material3.SelectableDates
//import androidx.compose.material3.Text
//import androidx.compose.material3.TextButton
//import androidx.compose.material3.TimePicker
//import androidx.compose.material3.TimePickerState
//import androidx.compose.material3.rememberDatePickerState
//import androidx.compose.material3.rememberTimePickerState
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//actual fun PlatformDatePicker(
//    initialDate: LocalDate,
//    onDateSelected: (LocalDate) -> Unit,
//    onDismissRequest: () -> Unit
//) {
//    val datePickerState = rememberDatePickerState(
//        initialDisplayMode = DisplayMode.Picker,
//        initialSelectedDateMillis = initialDate.toEpochMillis()
//    )
//
//    DatePickerDialog(
//        onDismissRequest = onDismissRequest,
//        confirmButton = {
//            TextButton(onClick = {
//                datePickerState.selectedDateMillis?.let { millis ->
//                    onDateSelected(LocalDate.fromEpochMillis(millis))
//                }
//                onDismissRequest()
//            }) {
//                Text("OK")
//            }
//        },
//        dismissButton = {
//            TextButton(onClick = onDismissRequest) {
//                Text("Cancel")
//            }
//        }
//    ) {
//        DatePicker(
//            state = datePickerState,
//            showModeToggle = true,
//            title = { Text("Select Date") }
//        )
//    }
//}
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//actual fun PlatformTimePicker(
//    initialTime: LocalTime,
//    onTimeSelected: (LocalTime) -> Unit,
//    onDismissRequest: () -> Unit
//) {
//    val timePickerState = rememberTimePickerState(
//        initialHour = initialTime.hour,
//        initialMinute = initialTime.minute,
//        is24Hour = true
//    )
//
//    DatePickerDialog(
//        onDismissRequest = onDismissRequest,
//        confirmButton = {
//            TextButton(onClick = {
//                onTimeSelected(LocalTime(timePickerState.hour, timePickerState.minute))
//                onDismissRequest()
//            }) {
//                Text("OK")
//            }
//        },
//        dismissButton = {
//            TextButton(onClick = onDismissRequest) {
//                Text("Cancel")
//            }
//        }
//    ) {
//        TimePicker(state = timePickerState)
//    }
//}
//
//// === Utility Extensions ===
//private fun LocalDate.toEpochMillis(): Long {
//    val calendar = Calendar.getInstance().apply {
//        set(Calendar.YEAR, year)
//        set(Calendar.MONTH, monthNumber - 1)
//        set(Calendar.DAY_OF_MONTH, dayOfMonth)
//        set(Calendar.HOUR_OF_DAY, 0)
//        set(Calendar.MINUTE, 0)
//        set(Calendar.SECOND, 0)
//        set(Calendar.MILLISECOND, 0)
//    }
//    return calendar.timeInMillis
//}
//
//private fun LocalDate.Companion.fromEpochMillis(millis: Long): LocalDate {
//    val calendar = Calendar.getInstance().apply {
//        timeInMillis = millis
//    }
//    return LocalDate(
//        year = calendar.get(Calendar.YEAR),
//        monthNumber = calendar.get(Calendar.MONTH) + 1,
//        dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
//    )
//}