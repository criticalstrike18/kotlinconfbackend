@file:OptIn(ExperimentalLayoutApi::class, ExperimentalLayoutApi::class)

package org.jetbrains.kotlinconf.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinconfapp.shared.generated.resources.Res
import kotlinconfapp.shared.generated.resources.calendar_month_outline
import kotlinconfapp.shared.generated.resources.clock_time_three_outline
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.kotlinconf.PlatformDatePicker
import org.jetbrains.kotlinconf.PlatformTimePicker
import org.jetbrains.kotlinconf.ui.theme.grey20Grey80
import org.jetbrains.kotlinconf.ui.theme.menuSelected
import org.jetbrains.kotlinconf.ui.theme.subtitle
import org.jetbrains.kotlinconf.ui.theme.title
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class DropdownOption(
    val id: String,  // Can be String or Int depending on your needs
    val displayName: String
)

@Composable
fun MultiSelectDropdown(
    label: String,
    options: List<DropdownOption>,
    selectedOptions: List<DropdownOption>,
    onSelectionChanged: (List<DropdownOption>) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.subtitle1,
            color = MaterialTheme.colors.title,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Selected items display as chips
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (selectedOptions.isEmpty()) 0.dp else 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            selectedOptions.forEach { selected ->
                Surface(
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colors.menuSelected,
                    border = BorderStroke(1.dp, MaterialTheme.colors.grey20Grey80)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selected.displayName,  // Use displayName here
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.title
                        )
                        IconButton(
                            onClick = {
                                onSelectionChanged(selectedOptions - selected)
                            },
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove ${selected.displayName}",  // Use displayName here
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colors.title
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                isExpanded = true
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(label) },
            textStyle = TextStyle(color = MaterialTheme.colors.onSurface),
            trailingIcon = {
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded)
                            Icons.Default.KeyboardArrowUp
                        else
                            Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            }
        )

        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = {
                isExpanded = false
                searchQuery = ""
            },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = 250.dp)
        ) {
            val filteredOptions = options.filter {
                it.displayName.contains(searchQuery, ignoreCase = true) &&
                        !selectedOptions.contains(it)
            }

            if (filteredOptions.isEmpty()) {
                DropdownMenuItem(
                    onClick = {},
                    enabled = false
                ) {
                    Text(
                        text = "No items found",
                        color = MaterialTheme.colors.subtitle,
                        style = MaterialTheme.typography.body2
                    )
                }
            } else {
                filteredOptions.forEach { option ->
                    DropdownMenuItem(
                        onClick = {
                            onSelectionChanged(selectedOptions + option)
                            searchQuery = ""
                        }
                    ) {
                        Text(
                            text = option.displayName,  // Use displayName here
                            color = MaterialTheme.colors.title,
                            style = MaterialTheme.typography.body2
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IntervalSelection(
    label: String,
    selectedInterval: Duration?,
    onIntervalSelected: (Duration) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.subtitle1,
            color = MaterialTheme.colors.title,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val intervals = listOf(15, 30, 60, 120)

            intervals.forEach { minutes ->
                val duration: Duration = minutes.toLong().minutes
                val textLabel = when (minutes) {
                    15 -> "15 min"
                    30 -> "30 min"
                    60 -> "1 hr"
                    120 -> "2 hrs"
                    else -> "$minutes min"
                }

                OutlinedButton(
                    onClick = { onIntervalSelected(duration) },
                    modifier = Modifier.height(40.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        backgroundColor = if (selectedInterval == duration) {
                            MaterialTheme.colors.menuSelected
                        } else {
                            MaterialTheme.colors.background
                        }
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (selectedInterval == duration) {
                            MaterialTheme.colors.primary
                        } else {
                            MaterialTheme.colors.grey20Grey80
                        }
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text(
                        text = textLabel,
                        style = MaterialTheme.typography.body2,
                        color = if (selectedInterval == duration) {
                            MaterialTheme.colors.primary
                        } else {
                            MaterialTheme.colors.title
                        }
                    )
                }
            }
        }
    }
}

// A DatePicker that shows a read-only text field and a trailing icon.
// (Assumes you have implemented PlatformDatePicker.)
@Composable
fun DatePickerField(
    label: String,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = selectedDate?.toString() ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        textStyle = TextStyle(color = MaterialTheme.colors.onSurface),
        trailingIcon = {
            IconButton(
                onClick = { showPicker = true },
                modifier = Modifier.size(48.dp)  // Standard IconButton touch target size
            ) {
                Icon(
                    painter = painterResource(Res.drawable.calendar_month_outline),
                    contentDescription = "Select Date",
                    modifier = Modifier.size(24.dp),  // Standard Material icon size
                )
            }
        }
    )

    if (showPicker) {
        PlatformDatePicker(
            initialDate = selectedDate ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            onDateSelected = onDateSelected,
            onDismissRequest = { showPicker = false }
        )
    }
}

@Composable
fun TimePickerField(
    label: String,
    selectedTime: LocalTime?,
    onTimeSelected: (LocalTime) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = selectedTime?.toString() ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        textStyle = TextStyle(color = MaterialTheme.colors.onSurface),
        trailingIcon = {
            IconButton(
                onClick = { showPicker = true },
                modifier = Modifier.size(48.dp)  // Standard IconButton touch target size
            ) {
                Icon(
                    painter = painterResource(Res.drawable.clock_time_three_outline),
                    contentDescription = "Select Time",
                    modifier = Modifier.size(24.dp),  // Standard Material icon size
                )
            }
        }
    )

    if (showPicker) {
        PlatformTimePicker(
            initialTime = selectedTime ?: Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .time,
            onTimeSelected = onTimeSelected,
            onDismissRequest = { showPicker = false }
        )
    }
}