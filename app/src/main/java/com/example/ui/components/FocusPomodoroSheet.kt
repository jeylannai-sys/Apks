package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TaskEntity
import kotlinx.coroutines.delay

enum class PomodoroPhase(val label: String, val defaultMinutes: Int, val color: Color) {
    FOCUS("Deep Focus", 25, Color(0xFF10B981)),
    SHORT_BREAK("Short Break", 5, Color(0xFF0EA5E9)),
    LONG_BREAK("Long Break", 15, Color(0xFF8B5CF6))
}

/**
 * Focus Mode & Pomodoro Timer linked to active tasks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusPomodoroSheet(
    pendingTasks: List<TaskEntity>,
    onDismiss: () -> Unit,
    onSessionCompleted: (String, Int) -> Unit = { _, _ -> }
) {
    var selectedPhase by remember { mutableStateOf(PomodoroPhase.FOCUS) }
    var totalSeconds by remember { mutableIntStateOf(25 * 60) }
    var remainingSeconds by remember { mutableIntStateOf(25 * 60) }
    var isRunning by remember { mutableStateOf(false) }
    var completedPomodoros by remember { mutableIntStateOf(0) }
    var linkedTask by remember { mutableStateOf<TaskEntity?>(pendingTasks.firstOrNull()) }

    // Countdown Timer Loop
    LaunchedEffect(isRunning, remainingSeconds) {
        if (isRunning && remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds -= 1
            if (remainingSeconds == 0) {
                isRunning = false
                if (selectedPhase == PomodoroPhase.FOCUS) {
                    completedPomodoros += 1
                    onSessionCompleted(linkedTask?.title ?: "Deep Work", 25)
                    selectedPhase = if (completedPomodoros % 4 == 0) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK
                    totalSeconds = selectedPhase.defaultMinutes * 60
                    remainingSeconds = totalSeconds
                } else {
                    selectedPhase = PomodoroPhase.FOCUS
                    totalSeconds = 25 * 60
                    remainingSeconds = totalSeconds
                }
            }
        }
    }

    val progress = remember(remainingSeconds, totalSeconds) {
        if (totalSeconds > 0) (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f) else 0f
    }
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(300), label = "pomo_prog")

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timeDisplay = "%02d:%02d".format(minutes, seconds)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("pomodoro_focus_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Focus Mode & Pomodoro", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Linked to: ${linkedTask?.title ?: "General Deep Focus"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Phase Selector Chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PomodoroPhase.values().forEach { phase ->
                    FilterChip(
                        selected = selectedPhase == phase,
                        onClick = {
                            selectedPhase = phase
                            totalSeconds = phase.defaultMinutes * 60
                            remainingSeconds = totalSeconds
                            isRunning = false
                        },
                        label = { Text(phase.label, fontSize = 12.sp) },
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            selectedContainerColor = phase.color.copy(alpha = 0.2f),
                            selectedLabelColor = phase.color
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Circular Countdown Canvas
            Box(
                modifier = Modifier.size(210.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(200.dp)) {
                    val strokeW = 12.dp.toPx()
                    // Track circle
                    drawCircle(
                        color = Color(0xFF334155).copy(alpha = 0.35f),
                        radius = size.width / 2f - strokeW / 2f,
                        style = Stroke(width = strokeW)
                    )
                    // Animated Arc
                    drawArc(
                        color = selectedPhase.color,
                        startAngle = -90f,
                        sweepAngle = animatedProgress * 360f,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = timeDisplay,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selectedPhase.label.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = selectedPhase.color,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Task Selector Card if tasks are available
            if (pendingTasks.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "Target Task for Session:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            pendingTasks.take(3).forEach { task ->
                                FilterChip(
                                    selected = linkedTask?.id == task.id,
                                    onClick = { linkedTask = task },
                                    label = { Text(task.title.take(18) + if (task.title.length > 18) "…" else "", fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            // Controls Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset Button
                IconButton(
                    onClick = {
                        isRunning = false
                        remainingSeconds = totalSeconds
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset")
                }

                // Primary Start/Pause Button
                Button(
                    onClick = { isRunning = !isRunning },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = selectedPhase.color),
                    modifier = Modifier
                        .height(52.dp)
                        .width(150.dp)
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isRunning) "Pause" else "Start"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRunning) "PAUSE" else "START",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                // Skip Button
                IconButton(
                    onClick = {
                        isRunning = false
                        remainingSeconds = 0
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Skip")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Completed Sessions Banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF10B981).copy(alpha = 0.12f)
            ) {
                Text(
                    text = "🍅 $completedPomodoros Focus Session(s) Completed Today",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF10B981),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}
