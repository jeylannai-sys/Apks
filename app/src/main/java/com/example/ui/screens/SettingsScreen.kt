package com.example.ui.screens

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.TaskPulseApp
import com.example.model.RingtoneOption
import com.example.service.SoundManager
import com.example.ui.theme.ThemeMode
import com.example.widget.TaskPulseWidgetProvider

import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Speed
import com.example.model.AppCurrency
import com.example.service.BackgroundMusicState
import com.example.util.DeviceDisplayInfo
import com.example.util.GoogleReporterProfile
import com.example.util.RefreshRatePreference
import com.example.util.SupportReportHelper
import com.example.util.SupportReportType
import com.example.util.SupportSendOutcome
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    currentTheme: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit,
    currency: AppCurrency,
    onCurrencyChanged: (AppCurrency) -> Unit,
    refreshRatePreference: RefreshRatePreference = RefreshRatePreference.MAX_ULTRA,
    deviceDisplayInfo: DeviceDisplayInfo = DeviceDisplayInfo(60, 60, listOf(60)),
    onRefreshRateChanged: (RefreshRatePreference) -> Unit = {},
    bgmState: BackgroundMusicState = BackgroundMusicState(),
    onImportBackgroundMusic: (Uri, (Result<String>) -> Unit) -> Unit = { _, _ -> },
    onClearBackgroundMusic: () -> Unit = {},
    onToggleBgmEnabled: (Boolean) -> Unit = {},
    onToggleBgmAutoPlay: (Boolean) -> Unit = {},
    onToggleBgmLoop: (Boolean) -> Unit = {},
    onToggleBgmPauseOnLeave: (Boolean) -> Unit = {},
    onBgmVolumeChanged: (Float) -> Unit = {},
    onToggleBgmPlayPause: () -> Unit = {},
    onOpenFileSafe: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var previewingTone by remember { mutableStateOf<String?>(null) }
    var showReportProblemDialog by remember { mutableStateOf(false) }

    val bgmFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportBackgroundMusic(uri) { result ->
                result.onSuccess { name ->
                    Toast.makeText(context, "Loaded background music: $name", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(
                        context,
                        "Could not load music file: ${err.message ?: "Unsupported audio"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // Secret Private File Safe Vault Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenFileSafe)
                    .testTag("private_file_safe_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Private File Safe",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Secret PIN-locked vault to hide videos, photos & any file type",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onOpenFileSafe,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_private_file_safe_button")
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Secret File Safe", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Active Currency Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("currency_settings_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Paid, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "App Currency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = "Currently using: ${currency.displayName} (${currency.symbol})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Horizontal / grid of currencies
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AppCurrency.entries) { curr ->
                            FilterChip(
                                selected = currency == curr,
                                onClick = { onCurrencyChanged(curr) },
                                label = { Text("${curr.flag} ${curr.code} (${curr.symbol})", fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }
        }

        // Night Visibility / Theme Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("theme_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Appearance & Night Visibility", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = "High-contrast dark mode for optimal viewing in low light", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeOptionChip(
                            label = "Dark Mode",
                            icon = Icons.Default.DarkMode,
                            isSelected = currentTheme == ThemeMode.DARK,
                            onClick = { onThemeChanged(ThemeMode.DARK) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionChip(
                            label = "Light Mode",
                            icon = Icons.Default.LightMode,
                            isSelected = currentTheme == ThemeMode.LIGHT,
                            onClick = { onThemeChanged(ThemeMode.LIGHT) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionChip(
                            label = "System",
                            icon = Icons.Default.SettingsBrightness,
                            isSelected = currentTheme == ThemeMode.SYSTEM,
                            onClick = { onThemeChanged(ThemeMode.SYSTEM) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Display Refresh Rate & Ultra FPS Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ultra_fps_settings_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3B82F6).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = Color(0xFF3B82F6),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Display Refresh Rate (Ultra FPS)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Device Max: ${deviceDisplayInfo.maxSupportedHz} Hz • Active: ${deviceDisplayInfo.activeHz} Hz • Supported: ${deviceDisplayInfo.supportedHzOptions.joinToString(" / ") { "${it}Hz" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(RefreshRatePreference.entries) { pref ->
                            val isSelected = refreshRatePreference == pref
                            FilterChip(
                                selected = isSelected,
                                onClick = { onRefreshRateChanged(pref) },
                                label = {
                                    Text(
                                        text = if (pref == RefreshRatePreference.MAX_ULTRA) {
                                            "Ultra / Max (${deviceDisplayInfo.maxSupportedHz} Hz)"
                                        } else {
                                            pref.label
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.testTag("fps_chip_${pref.name}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = refreshRatePreference.description,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Background Music Customization Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bgm_settings_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF8B5CF6).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LibraryMusic,
                                    contentDescription = null,
                                    tint = Color(0xFF8B5CF6),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "App Background Music",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Upload any music file & auto-play when opening TaskPulse",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = bgmState.enabled,
                            onCheckedChange = onToggleBgmEnabled,
                            modifier = Modifier.testTag("bgm_enabled_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Current track pill with play/pause & clear controls
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AudioFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bgmState.trackName,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (bgmState.hasCustomFile) {
                                        if (bgmState.isPlaying) "Custom Track • Playing" else "Custom Track • Paused"
                                    } else {
                                        if (bgmState.isPlaying) "Built-in Ambient • Playing" else "Built-in Ambient • Ready"
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = onToggleBgmPlayPause,
                                modifier = Modifier.testTag("bgm_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (bgmState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (bgmState.isPlaying) "Pause Music" else "Play Music",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (bgmState.hasCustomFile) {
                                IconButton(
                                    onClick = {
                                        onClearBackgroundMusic()
                                        Toast.makeText(context, "Reverted to default ambient track", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("bgm_clear_custom_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove Custom Music",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { bgmFilePickerLauncher.launch(arrayOf("audio/*", "*/*")) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("upload_bgm_music_button")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import / Upload Music File (MP3, WAV, M4A...)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Volume Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Volume",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Volume",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(54.dp)
                        )
                        Slider(
                            value = bgmState.volume,
                            onValueChange = onBgmVolumeChanged,
                            valueRange = 0f..1f,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("bgm_volume_slider")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(bgmState.volume * 100).roundToInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Toggle rows: Auto-play, Loop, Pause on leave
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Play When Opening App", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Automatically starts background music on launch", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = bgmState.autoPlayOnOpen,
                            onCheckedChange = onToggleBgmAutoPlay,
                            modifier = Modifier.testTag("bgm_autoplay_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Loop Track Continuously", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Repeat music smoothly without stopping", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = bgmState.loop,
                            onCheckedChange = onToggleBgmLoop,
                            modifier = Modifier.testTag("bgm_loop_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pause When Leaving App", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Automatically pause when switching apps or locking screen", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = bgmState.pauseWhenLeavingApp,
                            onCheckedChange = onToggleBgmPauseOnLeave,
                            modifier = Modifier.testTag("bgm_pause_leave_switch")
                        )
                    }
                }
            }
        }

        // Customize Ringtone Showcase Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Customizable Ringtones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = "Audible waveforms synthesized for alarms & timer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    RingtoneOption.entries.forEach { option ->
                        val isPlaying = previewingTone == option.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = option.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(text = option.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            IconButton(
                                onClick = {
                                    if (isPlaying) {
                                        SoundManager.stop()
                                        previewingTone = null
                                    } else {
                                        previewingTone = option.id
                                        SoundManager.playRingtone(context, option, loop = false, vibrate = true) {
                                            previewingTone = null
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Stop" else "Play",
                                    tint = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Lock Screen & Push Notifications Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Lock Screen Push Alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = "Head-up banners & lock screen status visibility", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Alarms and task deadlines are pushed to the Android notification drawer with full lock-screen visibility, action buttons (Dismiss / Snooze), and custom ringtone playback.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            val intent = Intent(context, MainActivity::class.java)
                            val pending = PendingIntent.getActivity(
                                context,
                                777,
                                intent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )

                            val notification = NotificationCompat.Builder(context, TaskPulseApp.CHANNEL_TASKS)
                                .setSmallIcon(R.drawable.ic_launcher_foreground)
                                .setContentTitle("📌 Test Deadline Alert: TaskPulse")
                                .setContentText("This is how your task deadlines look on your lock screen.")
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                                .setAutoCancel(true)
                                .setContentIntent(pending)
                                .build()

                            notificationManager.notify(777, notification)
                            Toast.makeText(context, "Test notification sent! Check drawer/lock screen", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Send Test Lock Screen Notification")
                    }
                }
            }
        }

        // Home Screen Widget Support Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Widgets, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Home Screen Widgets (2 Types)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = "1) Tasks Widget  •  2) Income & Expenses Widget", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "TaskPulse now provides 2 dedicated Android home screen widgets:\n• Tasks Widget: Glance at pending tasks and next deadline\n• Finance Widget: Track today's income, expenses, and net balance\n\nTo add: Long-press your home screen, tap 'Widgets', find TaskPulse, and drag your preferred widget onto the home screen.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            TaskPulseWidgetProvider.updateAllWidgets(context)
                            Toast.makeText(context, "All home screen widgets refreshed!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Force Refresh All Widgets")
                    }
                }
            }
        }

        // Report a Problem & Suggestions Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("report_problem_settings_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Feedback,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Report a Problem & Suggestions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Send feedback or bug reports directly to ${SupportReportHelper.SUPPORT_EMAIL}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { showReportProblemDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("report_problem_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Report a Problem",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // About & Version Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "About TaskPulse", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "TaskPulse v1.0 • Unified Task Organizer, Precision Clock Studio, and Financial Budget Intelligence.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        }
    }

    if (showReportProblemDialog) {
        ReportProblemDialog(
            onDismiss = { showReportProblemDialog = false }
        )
    }
}

@Composable
private fun ReportProblemDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var reportType by remember { mutableStateOf(SupportReportType.PROBLEM) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isFromGoogle by remember { mutableStateOf(false) }
    var isFetchingGoogle by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // Load cached Google reporter info and attempt silent Credential Manager auto-fill if blank
    LaunchedEffect(Unit) {
        val cached = SupportReportHelper.loadCachedProfile(context)
        if (cached.name.isNotBlank() || cached.email.isNotBlank()) {
            name = cached.name
            email = cached.email
            isFromGoogle = cached.isFromGoogle
        } else {
            isFetchingGoogle = true
            val silentProfile = SupportReportHelper.attemptSilentGoogleProfileLookup(context)
            isFetchingGoogle = false
            if (silentProfile != null) {
                name = silentProfile.name
                email = silentProfile.email
                isFromGoogle = silentProfile.isFromGoogle
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Feedback,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Report a Problem",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Destination Email Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Opens Gmail compose to: ${SupportReportHelper.SUPPORT_EMAIL}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Problem vs Suggestion selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SupportReportType.entries.forEach { type ->
                        FilterChip(
                            selected = reportType == type,
                            onClick = { reportType = type },
                            label = {
                                Text(
                                    text = type.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (reportType == type) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Auto-fill from Google Account button
                OutlinedButton(
                    onClick = {
                        isFetchingGoogle = true
                        errorText = null
                        scope.launch {
                            val res = SupportReportHelper.launchInteractiveGoogleProfilePicker(context)
                            isFetchingGoogle = false
                            res.onSuccess { profile ->
                                name = profile.name
                                email = profile.email
                                isFromGoogle = profile.isFromGoogle
                                Toast.makeText(
                                    context,
                                    "Auto-filled from Google (${profile.email})",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }.onFailure { err ->
                                if (err !is androidx.credentials.exceptions.GetCredentialCancellationException) {
                                    Toast.makeText(
                                        context,
                                        "Google account unavailable on this device — you can type Name & Email below.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    },
                    enabled = !isFetchingGoogle,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("report_google_autofill_button")
                ) {
                    if (isFetchingGoogle) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Loading Google Account...", fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isFromGoogle && email.isNotBlank()) "Switch Google Account" else "Auto-fill from Google",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        isFromGoogle = false
                    },
                    label = {
                        Text(if (isFromGoogle && name.isNotBlank()) "Name (from Google)" else "Name")
                    },
                    placeholder = { Text("Your name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_name_input")
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        isFromGoogle = false
                    },
                    label = {
                        Text(if (isFromGoogle && email.isNotBlank()) "Email (from Google)" else "Email")
                    },
                    placeholder = { Text("your.email@gmail.com") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_email_input")
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = {
                        message = it
                        errorText = null
                    },
                    label = { Text("Message *") },
                    placeholder = {
                        Text(
                            if (reportType == SupportReportType.PROBLEM) {
                                "Describe the issue or steps to reproduce..."
                            } else {
                                "Share your feature idea or suggestion..."
                            }
                        )
                    },
                    minLines = 4,
                    maxLines = 6,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_message_input")
                )

                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedMsg = message.trim()
                    val trimmedEmail = email.trim()
                    if (trimmedMsg.isEmpty()) {
                        errorText = "Please enter a message before submitting."
                        return@Button
                    }
                    if (trimmedEmail.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
                        errorText = "Please enter a valid email address."
                        return@Button
                    }

                    if (name.isNotBlank() || trimmedEmail.isNotBlank()) {
                        SupportReportHelper.saveCachedProfile(
                            context,
                            GoogleReporterProfile(
                                name = name.trim(),
                                email = trimmedEmail,
                                isFromGoogle = isFromGoogle
                            )
                        )
                    }

                    when (
                        val outcome = SupportReportHelper.sendSupportEmail(
                            context = context,
                            type = reportType,
                            reporterName = name,
                            reporterEmail = trimmedEmail,
                            message = trimmedMsg
                        )
                    ) {
                        is SupportSendOutcome.LaunchedEmailApp -> {
                            Toast.makeText(
                                context,
                                "Pasted into Gmail compose for ${SupportReportHelper.SUPPORT_EMAIL}",
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        }
                        is SupportSendOutcome.CopiedToClipboardFallback -> {
                            Toast.makeText(
                                context,
                                "No email app installed — report copied for ${SupportReportHelper.SUPPORT_EMAIL}",
                                Toast.LENGTH_LONG
                            ).show()
                            onDismiss()
                        }
                        is SupportSendOutcome.Failed -> {
                            errorText = outcome.reason
                        }
                    }
                },
                modifier = Modifier.testTag("report_submit_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Submit", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ThemeOptionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        },
        leadingIcon = {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp))
        },
        modifier = modifier
    )
}
