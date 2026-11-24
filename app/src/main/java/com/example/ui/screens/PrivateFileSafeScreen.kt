package com.example.ui.screens

import android.app.Activity
import android.content.IntentSender
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VaultFileCategory
import com.example.data.VaultFileEntity
import com.example.util.PrivateFileSafeHelper
import com.example.util.VaultBatchRestoreResult
import com.example.util.VaultImportResult
import com.example.util.VaultRestoreOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PrivateFileSafeScreen(
    vaultFiles: List<VaultFileEntity>,
    onImportFiles: (List<android.net.Uri>, Boolean, (VaultImportResult) -> Unit) -> Unit,
    onExportFile: (VaultFileEntity, android.net.Uri, (Boolean) -> Unit) -> Unit,
    onRestoreToGallery: (VaultFileEntity, Boolean, (VaultRestoreOutcome) -> Unit) -> Unit = { _, _, _ -> },
    onRestoreAllToGallery: (List<VaultFileEntity>, Boolean, (VaultBatchRestoreResult) -> Unit) -> Unit = { _, _, _ -> },
    onDeleteFile: (VaultFileEntity) -> Unit,
    onLockAndExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasPin by remember { mutableStateOf(PrivateFileSafeHelper.hasPin(context)) }
    var isUnlocked by rememberSaveable { mutableStateOf(false) }

    if (!isUnlocked) {
        VaultPinLockGate(
            isSettingNewPin = !hasPin,
            onPinCreated = { newPin ->
                PrivateFileSafeHelper.setPin(context, newPin)
                hasPin = true
                isUnlocked = true
                Toast.makeText(context, "Secret File Safe PIN created!", Toast.LENGTH_SHORT).show()
            },
            onPinSubmitted = { enteredPin ->
                if (PrivateFileSafeHelper.verifyPin(context, enteredPin)) {
                    isUnlocked = true
                    true
                } else {
                    false
                }
            },
            onCancel = onLockAndExit,
            modifier = modifier
        )
    } else {
        UnlockedPrivateFileSafeContent(
            vaultFiles = vaultFiles,
            onImportFiles = onImportFiles,
            onExportFile = onExportFile,
            onRestoreToGallery = onRestoreToGallery,
            onRestoreAllToGallery = onRestoreAllToGallery,
            onDeleteFile = onDeleteFile,
            onLockVault = {
                isUnlocked = false
                onLockAndExit()
            },
            onPinChanged = { newPin ->
                PrivateFileSafeHelper.setPin(context, newPin)
                Toast.makeText(context, "Secret Safe PIN updated!", Toast.LENGTH_SHORT).show()
            },
            modifier = modifier
        )
    }
}

@Composable
private fun VaultPinLockGate(
    isSettingNewPin: Boolean,
    onPinCreated: (String) -> Unit,
    onPinSubmitted: (String) -> Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var enteredPin by remember { mutableStateOf("") }
    var firstSetupPin by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun handleDigit(digit: String) {
        if (enteredPin.length >= 6) return
        errorMessage = null
        enteredPin += digit
    }

    fun handleConfirm() {
        if (enteredPin.length < 4) {
            errorMessage = "Enter at least 4 digits"
            return
        }
        if (isSettingNewPin) {
            if (firstSetupPin == null) {
                firstSetupPin = enteredPin
                enteredPin = ""
            } else if (firstSetupPin == enteredPin) {
                onPinCreated(enteredPin)
            } else {
                errorMessage = "PINs did not match. Try again."
                firstSetupPin = null
                enteredPin = ""
            }
        } else {
            val ok = onPinSubmitted(enteredPin)
            if (!ok) {
                errorMessage = "Incorrect secret PIN. Please try again."
                enteredPin = ""
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .testTag("vault_pin_gate_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = when {
                        isSettingNewPin && firstSetupPin == null -> "Create Secret Safe PIN"
                        isSettingNewPin && firstSetupPin != null -> "Confirm Your Secret PIN"
                        else -> "Private File Safe Locked"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isSettingNewPin)
                        "Protect hidden videos, photos, and private files with a 4–6 digit secret PIN."
                    else
                        "Enter your secret PIN to view, import, or export hidden files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Masked PIN indicator dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val slotCount = maxOf(4, enteredPin.length)
                    repeat(slotCount.coerceAtMost(6)) { idx ->
                        val filled = idx < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (filled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                )
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Numeric keypad
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { rowDigits ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowDigits.forEach { digit ->
                                PinDigitButton(
                                    label = digit,
                                    onClick = { handleDigit(digit) }
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .clickable {
                                    enteredPin = ""
                                    errorMessage = null
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("CLR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        PinDigitButton(label = "0", onClick = { handleDigit("0") })

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (enteredPin.isNotEmpty()) {
                                        enteredPin = enteredPin.dropLast(1)
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Backspace",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Back")
                    }

                    Button(
                        onClick = { handleConfirm() },
                        enabled = enteredPin.length >= 4,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("vault_pin_confirm_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isSettingNewPin && firstSetupPin == null) "Next" else "Unlock",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinDigitButton(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag("pin_key_$label")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun UnlockedPrivateFileSafeContent(
    vaultFiles: List<VaultFileEntity>,
    onImportFiles: (List<android.net.Uri>, Boolean, (VaultImportResult) -> Unit) -> Unit,
    onExportFile: (VaultFileEntity, android.net.Uri, (Boolean) -> Unit) -> Unit,
    onRestoreToGallery: (VaultFileEntity, Boolean, (VaultRestoreOutcome) -> Unit) -> Unit,
    onRestoreAllToGallery: (List<VaultFileEntity>, Boolean, (VaultBatchRestoreResult) -> Unit) -> Unit,
    onDeleteFile: (VaultFileEntity) -> Unit,
    onLockVault: () -> Unit,
    onPinChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var previewFile by remember { mutableStateOf<VaultFileEntity?>(null) }
    var filePendingDelete by remember { mutableStateOf<VaultFileEntity?>(null) }
    var filePendingExport by remember { mutableStateOf<VaultFileEntity?>(null) }
    var removeAfterCustomExport by remember { mutableStateOf(false) }
    var filePendingRestore by remember { mutableStateOf<VaultFileEntity?>(null) }
    var showRestoreAllDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var autoHideGallery by remember {
        mutableStateOf(PrivateFileSafeHelper.isAutoHideFromGalleryEnabled(context))
    }
    var pendingRecoverableQueue by remember { mutableStateOf<List<IntentSender>>(emptyList()) }
    var activeRecoverableSender by remember { mutableStateOf<IntentSender?>(null) }

    val galleryHideLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            Toast.makeText(
                context,
                "Original hidden from device Gallery — now only visible in TaskPulse Safe",
                Toast.LENGTH_SHORT
            ).show()
        }
        val next = pendingRecoverableQueue.firstOrNull()
        if (next != null) {
            pendingRecoverableQueue = pendingRecoverableQueue.drop(1)
            activeRecoverableSender = next
        }
    }

    androidx.compose.runtime.LaunchedEffect(activeRecoverableSender) {
        val sender = activeRecoverableSender ?: return@LaunchedEffect
        activeRecoverableSender = null
        try {
            galleryHideLauncher.launch(IntentSenderRequest.Builder(sender).build())
        } catch (_: Exception) {}
    }

    fun handleImportResult(result: VaultImportResult) {
        if (result.importedCount <= 0) {
            Toast.makeText(context, "No files could be imported.", Toast.LENGTH_SHORT).show()
            return
        }

        val silentHidden = result.autoHiddenFromOriginCount
        val summaryMsg = when {
            autoHideGallery && silentHidden > 0 ->
                "Imported ${result.importedCount} file(s) & auto-hidden $silentHidden from original storage!"
            else ->
                "Imported ${result.importedCount} file(s) into Private Safe"
        }
        Toast.makeText(context, summaryMsg, Toast.LENGTH_SHORT).show()

        if (!autoHideGallery) return

        val pendingIntent = if (result.pendingMediaStoreDeleteUris.isNotEmpty()) {
            PrivateFileSafeHelper.buildMediaStoreDeletePendingIntent(context, result.pendingMediaStoreDeleteUris)
        } else null

        if (pendingIntent != null) {
            try {
                galleryHideLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                return
            } catch (_: Exception) {}
        }

        val firstRecoverable = result.recoverableSecurityIntents.firstOrNull()
        if (firstRecoverable != null) {
            pendingRecoverableQueue = result.recoverableSecurityIntents.drop(1)
            activeRecoverableSender = firstRecoverable
        }
    }

    // 1. Zero-permission Photo & Video Picker (Google Play compliant)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(25)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportFiles(uris, autoHideGallery) { result ->
                handleImportResult(result)
            }
        }
    }

    // 2. Any-file Document Picker (PDFs, ZIPs, docs, audio, images, videos, etc.)
    val anyFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportFiles(uris, autoHideGallery) { result ->
                handleImportResult(result)
            }
        }
    }

    // 3. SAF Export-Back Launcher (restores original file name & type to user's chosen location)
    val exportBackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri ->
        val target = filePendingExport
        val shouldRemoveFromVault = removeAfterCustomExport
        filePendingExport = null
        removeAfterCustomExport = false
        if (destUri != null && target != null) {
            onExportFile(target, destUri) { success ->
                if (success && shouldRemoveFromVault) {
                    onDeleteFile(target)
                }
                Toast.makeText(
                    context,
                    if (success) {
                        if (shouldRemoveFromVault) "Restored '${target.displayName}' and removed from Safe!"
                        else "Exported '${target.displayName}' successfully!"
                    } else {
                        "Failed to export file."
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val filteredFiles = remember(vaultFiles, searchQuery, selectedCategory) {
        vaultFiles.filter { file ->
            val matchesCat = selectedCategory == null || file.fileCategory == selectedCategory
            val matchesQuery = searchQuery.isBlank() ||
                file.displayName.contains(searchQuery, ignoreCase = true) ||
                file.mimeType.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    val totalBytes = remember(vaultFiles) { vaultFiles.sumOf { it.sizeBytes } }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Vault Header & Controls Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(20.dp)
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
                                        text = "${vaultFiles.size} hidden file(s) • ${PrivateFileSafeHelper.formatFileSize(totalBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { showChangePinDialog = true },
                                    modifier = Modifier.testTag("vault_change_pin_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = "Change Secret PIN",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                FilledTonalButton(
                                    onClick = onLockVault,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(40.dp)
                                        .testTag("vault_lock_button")
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Lock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Stored inside private app vault (.nomedia). Only TaskPulse can view these files.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = "Auto-Hide from Gallery & Photos",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Automatically remove original file from Gallery after safe import",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = autoHideGallery,
                                        onCheckedChange = { checked ->
                                            autoHideGallery = checked
                                            PrivateFileSafeHelper.setAutoHideFromGalleryEnabled(context, checked)
                                        },
                                        modifier = Modifier.testTag("vault_auto_hide_gallery_switch")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Import Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    mediaPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("vault_import_media_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Photos & Videos", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = { anyFilePickerLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("vault_import_any_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Any File Type", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }

                        if (vaultFiles.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = { showRestoreAllDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("vault_restore_all_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Restore,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Restore All (${vaultFiles.size}) to Original Gallery / Folder",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Search & Filter Row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search hidden files by name or type...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_search_input")
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { selectedCategory = null },
                                label = { Text("All (${vaultFiles.size})", fontSize = 12.sp) }
                            )
                        }
                        items(VaultFileCategory.entries) { cat ->
                            val count = vaultFiles.count { it.fileCategory == cat.name }
                            FilterChip(
                                selected = selectedCategory == cat.name,
                                onClick = {
                                    selectedCategory = if (selectedCategory == cat.name) null else cat.name
                                },
                                label = { Text("${cat.label} ($count)", fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            if (filteredFiles.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (vaultFiles.isEmpty()) "Your Private Safe is Empty" else "No matching hidden files",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Use 'Photos & Videos' or 'Any File Type' above to hide pictures, videos, PDFs, audio, or any file privately. You can export them back anytime.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredFiles, key = { it.id }) { file ->
                    VaultFileCard(
                        vaultFile = file,
                        onPreview = { previewFile = file },
                        onRestoreToGallery = { filePendingRestore = file },
                        onExportBack = {
                            removeAfterCustomExport = false
                            filePendingExport = file
                            exportBackLauncher.launch(file.displayName)
                        },
                        onDelete = { filePendingDelete = file }
                    )
                }
            }
        }
    }

    // Preview / Details Modal
    if (previewFile != null) {
        val file = previewFile!!
        val isImage = file.fileCategory == VaultFileCategory.IMAGE.name
        val previewBitmap by produceState<Bitmap?>(initialValue = null, key1 = file.id) {
            if (isImage) {
                value = withContext(Dispatchers.IO) {
                    PrivateFileSafeHelper.decodeSampledBitmap(
                        PrivateFileSafeHelper.getStoredFile(context, file),
                        maxDim = 1200
                    )
                }
            }
        }

        AlertDialog(
            onDismissRequest = { previewFile = null },
            title = {
                Text(
                    text = file.displayName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isImage && previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = file.displayName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = categoryIcon(file.fileCategory),
                                    contentDescription = null,
                                    tint = categoryColor(file.fileCategory),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(file.fileCategory, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(file.mimeType, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    val dateStr = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
                        .format(Date(file.importedAtMillis))
                    val restoreTargetPath = PrivateFileSafeHelper.resolveRestoreRelativePath(file).trimEnd('/')
                    Text(
                        text = "Size: ${PrivateFileSafeHelper.formatFileSize(file.sizeBytes)}\nImported: $dateStr\nRestores to: $restoreTargetPath",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    previewFile = null
                    filePendingRestore = file
                }) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restore to Gallery")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        if (!PrivateFileSafeHelper.openFileExternally(context, file)) {
                            Toast.makeText(context, "No compatible app found to open this file type", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open")
                    }
                    TextButton(onClick = {
                        if (!PrivateFileSafeHelper.shareFileExternally(context, file)) {
                            Toast.makeText(context, "Unable to share this file", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Share")
                    }
                    TextButton(onClick = { previewFile = null }) {
                        Text("Close")
                    }
                }
            }
        )
    }

    // Single-File Restore to Original Gallery / Folder Confirmation Dialog
    if (filePendingRestore != null) {
        val target = filePendingRestore!!
        val destinationLabel = remember(target.id) {
            PrivateFileSafeHelper.resolveRestoreRelativePath(target).trimEnd('/')
        }
        var removeFromSafe by remember(target.id) { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { filePendingRestore = null },
            title = { Text("Restore to Original Gallery", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Restore '${target.displayName}' so it appears in your device Gallery / Photos again?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (target.originalRelativePath.isNotBlank()) "Original Path" else "Target Gallery Folder",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = destinationLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { removeFromSafe = !removeFromSafe }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = removeFromSafe,
                            onCheckedChange = { removeFromSafe = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Remove from Private Safe after restoring",
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        filePendingRestore = null
                        onRestoreToGallery(target, removeFromSafe) { outcome ->
                            when (outcome) {
                                is VaultRestoreOutcome.RestoredToGallery -> {
                                    Toast.makeText(
                                        context,
                                        "Restored '${target.displayName}' to ${outcome.destinationPath}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                is VaultRestoreOutcome.NeedsCustomExportPicker -> {
                                    Toast.makeText(context, outcome.reason, Toast.LENGTH_SHORT).show()
                                    removeAfterCustomExport = removeFromSafe
                                    filePendingExport = target
                                    exportBackLauncher.launch(target.displayName)
                                }
                                is VaultRestoreOutcome.Failed -> {
                                    Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("vault_confirm_restore_button")
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restore Now")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        filePendingRestore = null
                        removeAfterCustomExport = removeFromSafe
                        filePendingExport = target
                        exportBackLauncher.launch(target.displayName)
                    }) {
                        Text("Pick Folder")
                    }
                    TextButton(onClick = { filePendingRestore = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Batch Restore All Dialog
    if (showRestoreAllDialog) {
        var removeAllFromSafe by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showRestoreAllDialog = false },
            title = { Text("Restore All (${vaultFiles.size}) Files?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Restores all photos, videos, and files back to their original Gallery / storage folders so your device Gallery can see them again.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { removeAllFromSafe = !removeAllFromSafe }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = removeAllFromSafe,
                            onCheckedChange = { removeAllFromSafe = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Remove restored files from Private Safe",
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreAllDialog = false
                        val snapshot = vaultFiles.toList()
                        onRestoreAllToGallery(snapshot, removeAllFromSafe) { result ->
                            val msg = buildString {
                                append("Restored ${result.restoredCount} file(s) to Gallery")
                                if (result.lastDestinationPath.isNotBlank()) {
                                    append(" (${result.lastDestinationPath})")
                                }
                                if (result.fallbackExportNeeded.isNotEmpty()) {
                                    append(". ${result.fallbackExportNeeded.size} file(s) need 'Pick Folder'.")
                                }
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            val firstFallback = result.fallbackExportNeeded.firstOrNull()
                            if (firstFallback != null) {
                                removeAfterCustomExport = removeAllFromSafe
                                filePendingExport = firstFallback
                                exportBackLauncher.launch(firstFallback.displayName)
                            }
                        }
                    },
                    modifier = Modifier.testTag("vault_confirm_restore_all_button")
                ) {
                    Text("Restore All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (filePendingDelete != null) {
        val target = filePendingDelete!!
        AlertDialog(
            onDismissRequest = { filePendingDelete = null },
            title = { Text("Delete Hidden File?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Permanently delete '${target.displayName}' from your Private File Safe? This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteFile(target)
                        filePendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { filePendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Change PIN Dialog
    if (showChangePinDialog) {
        var newPinText by remember { mutableStateOf("") }
        var confirmPinText by remember { mutableStateOf("") }
        var pinErr by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showChangePinDialog = false },
            title = { Text("Change Secret Safe PIN", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newPinText,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) newPinText = it },
                        label = { Text("New PIN (4–6 digits)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                    OutlinedTextField(
                        value = confirmPinText,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirmPinText = it },
                        label = { Text("Confirm New PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                    if (pinErr != null) {
                        Text(pinErr!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    when {
                        newPinText.length < 4 -> pinErr = "PIN must be 4 to 6 digits."
                        newPinText != confirmPinText -> pinErr = "PINs do not match."
                        else -> {
                            onPinChanged(newPinText)
                            showChangePinDialog = false
                        }
                    }
                }) {
                    Text("Update PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VaultFileCard(
    vaultFile: VaultFileEntity,
    onPreview: () -> Unit,
    onRestoreToGallery: () -> Unit,
    onExportBack: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val isImage = vaultFile.fileCategory == VaultFileCategory.IMAGE.name
    val thumb by produceState<Bitmap?>(initialValue = null, key1 = vaultFile.id) {
        if (isImage) {
            value = withContext(Dispatchers.IO) {
                PrivateFileSafeHelper.decodeSampledBitmap(
                    PrivateFileSafeHelper.getStoredFile(context, vaultFile),
                    maxDim = 180
                )
            }
        }
    }

    val dateStr = remember(vaultFile.importedAtMillis) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(vaultFile.importedAtMillis))
    }
    val locationLabel = remember(vaultFile.id, vaultFile.originalRelativePath, vaultFile.fileCategory) {
        PrivateFileSafeHelper.formatRestoreLocationLabel(vaultFile)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPreview)
            .testTag("vault_file_${vaultFile.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(categoryColor(vaultFile.fileCategory).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb!!.asImageBitmap(),
                        contentDescription = vaultFile.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = categoryIcon(vaultFile.fileCategory),
                        contentDescription = null,
                        tint = categoryColor(vaultFile.fileCategory),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vaultFile.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${vaultFile.fileCategory} • ${PrivateFileSafeHelper.formatFileSize(vaultFile.sizeBytes)} • $dateStr",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = locationLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF10B981),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onRestoreToGallery,
                modifier = Modifier.testTag("vault_restore_${vaultFile.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = "Restore to Original Gallery",
                    tint = Color(0xFF10B981)
                )
            }
            IconButton(
                onClick = onExportBack,
                modifier = Modifier.testTag("vault_export_${vaultFile.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = "Export to Custom Folder",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("vault_delete_${vaultFile.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete File",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun categoryIcon(category: String) = when (category) {
    VaultFileCategory.IMAGE.name -> Icons.Default.Image
    VaultFileCategory.VIDEO.name -> Icons.Default.VideoFile
    VaultFileCategory.AUDIO.name -> Icons.Default.AudioFile
    VaultFileCategory.DOCUMENT.name -> Icons.Default.Description
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private fun categoryColor(category: String) = when (category) {
    VaultFileCategory.IMAGE.name -> Color(0xFF38BDF8)
    VaultFileCategory.VIDEO.name -> Color(0xFFF43F5E)
    VaultFileCategory.AUDIO.name -> Color(0xFF8B5CF6)
    VaultFileCategory.DOCUMENT.name -> Color(0xFF10B981)
    else -> Color(0xFFF59E0B)
}
