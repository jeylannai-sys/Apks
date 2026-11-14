package com.example.ui.screens

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AlarmEntity
import com.example.model.RingtoneOption
import com.example.service.SoundManager
import com.example.ui.components.RingCountdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockScreen(
    alarms: List<AlarmEntity>,
    onSaveAlarm: (AlarmEntity) -> Unit,
    onToggleAlarm: (Long, Boolean) -> Unit,
    onDeleteAlarm: (AlarmEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Alarms", "Stopwatch", "Timer")

    var showAddAlarmSheet by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showAddAlarmSheet = true },
                    modifier = Modifier.testTag("add_alarm_fab"),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Alarm")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
            ) {
                // Sub-tabs bar
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            icon = {
                                when (index) {
                                    0 -> Icon(Icons.Default.Alarm, contentDescription = null)
                                    1 -> Icon(Icons.Default.Timer, contentDescription = null)
                                    else -> Icon(Icons.Default.HourglassBottom, contentDescription = null)
                                }
                            }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> AlarmsTabContent(
                        alarms = alarms,
                        onToggleAlarm = onToggleAlarm,
                        onDeleteAlarm = onDeleteAlarm
                    )
                    1 -> StopwatchTabContent()
                    2 -> TimerTabContent()
                }
            }
        }
    }

    if (showAddAlarmSheet) {
        AlarmEditorBottomSheet(
            onDismiss = { showAddAlarmSheet = false },
            onSave = {
                onSaveAlarm(it)
                showAddAlarmSheet = false
            }
        )
    }
}

@Composable
fun AlarmsTabContent(
    alarms: List<AlarmEntity>,
    onToggleAlarm: (Long, Boolean) -> Unit,
    onDeleteAlarm: (AlarmEntity) -> Unit
) {
    val context = LocalContext.current
    if (alarms.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Alarms Set",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap the + button to create a customized alarm with custom ringtone and lock screen push notification.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(alarms, key = { it.id }) { alarm ->
                AlarmItemCard(
                    alarm = alarm,
                    onToggle = { onToggleAlarm(alarm.id, !alarm.isEnabled) },
                    onDelete = { onDeleteAlarm(alarm) },
                    onTest = {
                        SoundManager.playRingtone(
                            context = context,
                            ringtone = RingtoneOption.fromId(alarm.ringtone),
                            loop = false,
                            vibrate = alarm.vibrate
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun AlarmItemCard(
    alarm: AlarmEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    val isPm = alarm.hour >= 12
    val displayHour = when {
        alarm.hour == 0 -> 12
        alarm.hour > 12 -> alarm.hour - 12
        else -> alarm.hour
    }
    val timeString = String.format(Locale.US, "%02d:%02d", displayHour, alarm.minute)
    val amPmString = if (isPm) "PM" else "AM"
    val ringtone = RingtoneOption.fromId(alarm.ringtone)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alarm_card_${alarm.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isEnabled)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (alarm.isEnabled) 3.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = amPmString,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.testTag("alarm_toggle_${alarm.id}")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = alarm.label.ifBlank { "Alarm" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata row (Ringtone, Test button, Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = ringtone.displayName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onTest) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Test Sound", fontSize = 12.sp)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Alarm",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorBottomSheet(
    onDismiss: () -> Unit,
    onSave: (AlarmEntity) -> Unit
) {
    val context = LocalContext.current
    var label by remember { mutableStateOf("") }
    var hour by remember { mutableIntStateOf(7) }
    var minute by remember { mutableIntStateOf(0) }
    var selectedRingtone by remember { mutableStateOf(RingtoneOption.RADAR_PULSE) }
    var vibrate by remember { mutableStateOf(true) }
    var isPreviewing by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = {
            SoundManager.stop()
            onDismiss()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Set New Alarm",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = {
                    SoundManager.stop()
                    onDismiss()
                }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Time Selector Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val displayHour = when {
                        hour == 0 -> 12
                        hour > 12 -> hour - 12
                        else -> hour
                    }
                    val amPm = if (hour >= 12) "PM" else "AM"

                    Column {
                        Text(
                            text = "Alarm Time",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.US, "%02d:%02d %s", displayHour, minute, amPm),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m ->
                                    hour = h
                                    minute = m
                                },
                                hour,
                                minute,
                                false
                            ).show()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Change Time")
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Alarm Label") },
                placeholder = { Text("e.g. Wakeup, Morning Review") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("alarm_label_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Ringtone selection
            Text(
                text = "Customize Ringtone",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RingtoneOption.entries) { option ->
                    FilterChip(
                        selected = selectedRingtone == option,
                        onClick = { selectedRingtone = option },
                        label = { Text(option.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sound Preview
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        if (isPreviewing) {
                            SoundManager.stop()
                            isPreviewing = false
                        } else {
                            isPreviewing = true
                            SoundManager.playRingtone(context, selectedRingtone, loop = false) {
                                isPreviewing = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isPreviewing) "Stop Sound" else "Preview ${selectedRingtone.displayName}")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Vibrate switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Vibration Alert", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = vibrate, onCheckedChange = { vibrate = it })
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    SoundManager.stop()
                    onSave(
                        AlarmEntity(
                            label = label.ifBlank { "Alarm" },
                            hour = hour,
                            minute = minute,
                            ringtone = selectedRingtone.id,
                            vibrate = vibrate,
                            isEnabled = true
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("confirm_alarm_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Set Alarm", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// ---------------- STOPWATCH TAB ----------------
data class LapRecord(
    val lapIndex: Int,
    val lapTimeMillis: Long,
    val totalTimeMillis: Long
)

@Composable
fun StopwatchTabContent() {
    var isRunning by remember { mutableStateOf(false) }
    var elapsedTimeMillis by remember { mutableLongStateOf(0L) }
    var startTimeMillis by remember { mutableLongStateOf(0L) }
    val laps = remember { mutableStateListOf<LapRecord>() }
    var lastLapTotal by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            startTimeMillis = System.currentTimeMillis() - elapsedTimeMillis
            while (isRunning) {
                elapsedTimeMillis = System.currentTimeMillis() - startTimeMillis
                delay(30)
            }
        }
    }

    val hours = (elapsedTimeMillis / 3600000).toInt()
    val minutes = ((elapsedTimeMillis % 3600000) / 60000).toInt()
    val seconds = ((elapsedTimeMillis % 60000) / 1000).toInt()
    val millis = ((elapsedTimeMillis % 1000) / 10).toInt()

    val timeString = String.format(Locale.US, "%02d:%02d:%02d.%02d", hours, minutes, seconds, millis)

    val fastestLap = laps.minByOrNull { it.lapTimeMillis }
    val slowestLap = laps.maxByOrNull { it.lapTimeMillis }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Big Digital Clock
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 32.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = timeString,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Controls (Lap, Start/Stop, Reset)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lap button
            Button(
                onClick = {
                    if (isRunning) {
                        val lapTime = elapsedTimeMillis - lastLapTotal
                        lastLapTotal = elapsedTimeMillis
                        laps.add(0, LapRecord(laps.size + 1, lapTime, elapsedTimeMillis))
                    }
                },
                enabled = isRunning,
                shape = CircleShape,
                modifier = Modifier.size(68.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(imageVector = Icons.Default.Flag, contentDescription = "Lap", modifier = Modifier.size(24.dp))
            }

            // Start/Stop Primary FAB
            Button(
                onClick = { isRunning = !isRunning },
                shape = CircleShape,
                modifier = Modifier
                    .size(80.dp)
                    .testTag("stopwatch_toggle_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isRunning) "Pause" else "Start",
                    modifier = Modifier.size(36.dp)
                )
            }

            // Reset button
            Button(
                onClick = {
                    isRunning = false
                    elapsedTimeMillis = 0L
                    lastLapTotal = 0L
                    laps.clear()
                },
                enabled = elapsedTimeMillis > 0 && !isRunning,
                shape = CircleShape,
                modifier = Modifier.size(68.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(24.dp))
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Lap Times List
        if (laps.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Lap", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "Lap Split", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "Total Time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(laps) { lap ->
                    val isFastest = laps.size > 1 && lap == fastestLap
                    val isSlowest = laps.size > 1 && lap == slowestLap

                    val lapSec = (lap.lapTimeMillis / 1000).toInt()
                    val lapHundredths = ((lap.lapTimeMillis % 1000) / 10).toInt()
                    val lapStr = String.format(Locale.US, "%02d:%02d.%02d", lapSec / 60, lapSec % 60, lapHundredths)

                    val totSec = (lap.totalTimeMillis / 1000).toInt()
                    val totHundredths = ((lap.totalTimeMillis % 1000) / 10).toInt()
                    val totStr = String.format(Locale.US, "%02d:%02d.%02d", totSec / 60, totSec % 60, totHundredths)

                    val badgeColor = when {
                        isFastest -> Color(0xFF10B981)
                        isSlowest -> Color(0xFFEF4444)
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Lap ${lap.lapIndex}" + if (isFastest) " (Fastest)" else if (isSlowest) " (Slowest)" else "",
                                fontWeight = FontWeight.SemiBold,
                                color = badgeColor,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "+$lapStr",
                                fontFamily = FontFamily.Monospace,
                                color = badgeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = totStr,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------- TIMER TAB ----------------
@Composable
fun TimerTabContent() {
    val context = LocalContext.current
    var selectedDurationSeconds by remember { mutableIntStateOf(1500) } // Default 25 min Pomodoro
    var remainingSeconds by remember { mutableIntStateOf(1500) }
    var isRunning by remember { mutableStateOf(false) }
    var selectedRingtone by remember { mutableStateOf(RingtoneOption.DIGITAL_CHIME) }

    val presets = listOf(
        "1m" to 60,
        "5m" to 300,
        "10m" to 600,
        "15m" to 900,
        "25m Pomodoro" to 1500,
        "45m" to 2700,
        "60m" to 3600
    )

    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (isRunning && remainingSeconds > 0) {
                delay(1000)
                if (isRunning) {
                    remainingSeconds -= 1
                    if (remainingSeconds <= 0) {
                        isRunning = false
                        // Sound completion alert
                        SoundManager.playRingtone(context, selectedRingtone, loop = false, vibrate = true)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Quick Presets Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(presets) { (label, duration) ->
                FilterChip(
                    selected = selectedDurationSeconds == duration,
                    onClick = {
                        isRunning = false
                        selectedDurationSeconds = duration
                        remainingSeconds = duration
                    },
                    label = { Text(label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Circular Countdown Progress
        RingCountdown(
            remainingSeconds = remainingSeconds,
            totalSeconds = selectedDurationSeconds,
            isRunning = isRunning
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Ringtone selector for timer finish
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Ringtone on finish:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(RingtoneOption.entries) { opt ->
                    FilterChip(
                        selected = selectedRingtone == opt,
                        onClick = { selectedRingtone = opt },
                        label = { Text(opt.displayName, fontSize = 11.sp) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Timer Action Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reset
            IconButton(
                onClick = {
                    isRunning = false
                    remainingSeconds = selectedDurationSeconds
                    SoundManager.stop()
                },
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset Timer")
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Play / Pause Primary Button
            Button(
                onClick = {
                    if (remainingSeconds <= 0) {
                        remainingSeconds = selectedDurationSeconds
                    }
                    isRunning = !isRunning
                    if (!isRunning) SoundManager.stop()
                },
                shape = CircleShape,
                modifier = Modifier
                    .size(76.dp)
                    .testTag("timer_play_pause_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isRunning) "Pause" else "Start",
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}
