@file:OptIn(ExperimentalLayoutApi::class)

package org.jetbrains.kotlinApp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.util.date.GMTDate
import io.ktor.util.date.Month
import kotlinconfapp.shared.generated.resources.Res
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.jetbrains.kotlinApp.ui.DatePickerField
import org.jetbrains.kotlinApp.ui.DropdownOption
import org.jetbrains.kotlinApp.ui.IntervalSelection
import org.jetbrains.kotlinApp.ui.MultiSelectDropdown
import org.jetbrains.kotlinApp.ui.TimePickerField
import org.jetbrains.kotlinApp.ui.components.NavigationBar
import org.jetbrains.kotlinApp.ui.theme.whiteGrey
import kotlin.time.Duration

@Composable
fun SessionFormScreen(
    service: ConferenceService,
    onSessionCreated: () -> Unit,
    back: () -> Unit
) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colors.whiteGrey)
            .fillMaxHeight()
            .fillMaxWidth()
    ) {
        NavigationBar(
            title = "Add New Session",
            isLeftVisible = true,
            onLeftClick = back,
            isRightVisible = false
        )

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            SessionForm(
                service = service,
                onSessionCreated = {
                    onSessionCreated()
                    back()
                }
            )
        }
    }
}


@Composable
fun SessionForm(service: ConferenceService, onSessionCreated: () -> Unit) {
    val scope = rememberCoroutineScope()

    // Text fields
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var roomName by remember { mutableStateOf("") }

    // For multi-select, use a mutableStateList
    val selectedSpeakers = remember { mutableStateListOf<DropdownOption>() }
    val selectedCategories = remember { mutableStateListOf<DropdownOption>() }

    // Separate start date & time pickers
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedTime by remember { mutableStateOf<LocalTime?>(null) }
    // Instead of an end picker, we select a duration/interval.
    var selectedInterval by remember { mutableStateOf<Duration?>(null) }

    // Load options from your DB with proper IDs
    val categories by produceState(initialValue = emptyList()) {
        value = service.dbStorage.getAllCategories().map { category ->
            DropdownOption(
                id = category.id.toString(),
                displayName = category.title
            )
        }
    }

    val speakers by produceState(initialValue = emptyList()) {
        value = service.dbStorage.getAllSpeakers().map { speaker ->
            DropdownOption(
                id = speaker.id,
                displayName = "${speaker.firstName} ${speaker.lastName}"
            )
        }
    }



    Column(modifier = Modifier.padding(8.dp)) {
//        Spacer(modifier = Modifier.height(4.dp))

        // --- Title input ---
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
            textStyle = TextStyle(color = MaterialTheme.colors.onSurface),
        )
        Spacer(modifier = Modifier.height(6.dp))

        // --- Description input ---
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
            textStyle = TextStyle(fontSize = 1.sp ,color = MaterialTheme.colors.onSurface)
        )
        Spacer(modifier = Modifier.height(6.dp))

        // --- Room input (new room every time) ---
        OutlinedTextField(
            value = roomName,
            onValueChange = { roomName = it },
            label = { Text("Room Name") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
            textStyle = TextStyle(color = MaterialTheme.colors.onSurface)
        )
        Spacer(modifier = Modifier.height(6.dp))

        // --- Multi-select Category list ---
        MultiSelectDropdown(
            label = "Select Categories",
            options = categories,
            selectedOptions = selectedCategories,
            onSelectionChanged = { newSelection ->
                selectedCategories.clear()
                selectedCategories.addAll(newSelection)
            }
        )
        Spacer(modifier = Modifier.height(6.dp))

        // --- Multi-select Speaker list ---
        MultiSelectDropdown(
            label = "Select Speakers",
            options = speakers,
            selectedOptions = selectedSpeakers,
            onSelectionChanged = { newSelection ->
                selectedSpeakers.clear()
                selectedSpeakers.addAll(newSelection)
            }
        )
        Spacer(modifier = Modifier.height(6.dp))

        // --- Start Date Picker ---
        DatePickerField(
            label = "Date",
            selectedDate = selectedDate,
            onDateSelected = { selectedDate = it }
        )
        Spacer(modifier = Modifier.height(6.dp))

        // --- Start Time Picker ---
        TimePickerField(
            label = "Start Time",
            selectedTime = selectedTime,
            onTimeSelected = { selectedTime = it }
        )
        Spacer(modifier = Modifier.height(6.dp))

        // --- Interval Selection (instead of End Time) ---
        IntervalSelection(
            label = "Select Session Duration",
            selectedInterval = selectedInterval,
            onIntervalSelected = { selectedInterval = it }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // --- Submit Button ---
        Button(
            onClick = {
                if (title.isNotBlank() &&
                    description.isNotBlank() &&
                    roomName.isNotBlank() &&
                    selectedDate != null &&
                    selectedTime != null &&
                    selectedInterval != null &&
                    selectedCategories.isNotEmpty() &&
                    selectedSpeakers.isNotEmpty()
                ) {
                    // Combine selected date and time into a start Instant.
                    val localDateTime = LocalDateTime(
                        date = selectedDate!!,
                        time = selectedTime!!
                    )
                    val startTime = GMTDate(
                        seconds = localDateTime.second,
                        minutes = localDateTime.minute,
                        hours = localDateTime.hour,
                        dayOfMonth = localDateTime.dayOfMonth,
                        month = Month.from(localDateTime.monthNumber - 1), // GMTDate months are 0-based
                        year = localDateTime.year
                    )

                    // Calculate end time by adding interval milliseconds to start time
                    val endTime = GMTDate(startTime.timestamp + selectedInterval!!.inWholeMilliseconds)

                    scope.launch {
                        // 1. Insert new room and retrieve its ID.
                        val newRoom = RoomTable(id = null, name = roomName, sort = null)
                        service.dbStorage.insertRoomThroughForm(newRoom)
                        val latestRoom = service.dbStorage.getAllRooms().lastOrNull()
                        val roomId = latestRoom?.id?.toInt()

                        if (roomId != null) {
                            // 2. Generate session ID.
                            val sessionId = generateSessionId(title, description, startTime, roomName)
                            // 3. Insert new session with computed end time.
                            service.dbStorage.insertSessionThroughForm(
                                SessionInfo(
                                    id = sessionId,
                                    title = title,
                                    description = description,
                                    startsAt = startTime,
                                    endsAt = endTime,
                                    roomId = roomId,
                                    speakerIds = selectedSpeakers.map { it.id },
                                    categoryIds = selectedCategories.map { it.id.toInt() }
                                )
                            )
                            onSessionCreated()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create Session")
        }
    }
}

// Generate session ID based on title, description, start date, and room name
fun generateSessionId(title: String, description: String, startDate: GMTDate, roomName: String): String {
    return "${title.lowercase().replace(" ", "_")}-${description.hashCode()}-${startDate}-${roomName.lowercase()}"
}

fun Instant.toGMTDate(): GMTDate = GMTDate(timestamp = toEpochMilliseconds())