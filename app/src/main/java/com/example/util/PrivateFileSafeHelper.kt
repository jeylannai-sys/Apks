package com.example.util

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.data.VaultFileCategory
import com.example.data.VaultFileEntity
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

sealed class VaultRestoreOutcome {
    data class RestoredToGallery(
        val restoredUri: Uri,
        val destinationPath: String,
        val removedFromVault: Boolean
    ) : VaultRestoreOutcome()

    data class NeedsCustomExportPicker(val reason: String) : VaultRestoreOutcome()
    data class Failed(val message: String) : VaultRestoreOutcome()
}

data class VaultBatchRestoreResult(
    val restoredCount: Int,
    val fallbackExportNeeded: List<VaultFileEntity>,
    val failedCount: Int,
    val lastDestinationPath: String
)

data class VaultImportResult(
    val importedCount: Int,
    val autoHiddenFromOriginCount: Int,
    val pendingMediaStoreDeleteUris: List<Uri>,
    val recoverableSecurityIntents: List<android.content.IntentSender>
)

object PrivateFileSafeHelper {

    private const val PREFS_NAME = "taskpulse_private_safe_prefs"
    private const val KEY_PIN_HASH = "vault_pin_sha256"
    private const val KEY_AUTO_HIDE_GALLERY = "vault_auto_hide_gallery"
    private const val VAULT_DIR_NAME = "private_safe"

    fun hasPin(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getString(KEY_PIN_HASH, null).isNullOrEmpty()
    }

    fun setPin(context: Context, pin: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PIN_HASH, hashPin(pin)).apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return stored == hashPin(pin)
    }

    fun isAutoHideFromGalleryEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_HIDE_GALLERY, true)
    }

    fun setAutoHideFromGalleryEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_HIDE_GALLERY, enabled).apply()
    }

    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(("tp_vault_$pin").toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun getVaultDir(context: Context): File {
        val dir = File(context.filesDir, VAULT_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) {
            try {
                nomedia.createNewFile()
            } catch (_: Exception) {}
        }
        return dir
    }

    fun getStoredFile(context: Context, vaultFile: VaultFileEntity): File {
        return File(getVaultDir(context), vaultFile.storedFileName)
    }

    fun resolveCategory(mimeType: String, displayName: String): VaultFileCategory {
        val lowerMime = mimeType.lowercase(Locale.ROOT)
        val ext = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            lowerMime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp") ->
                VaultFileCategory.IMAGE
            lowerMime.startsWith("video/") || ext in setOf("mp4", "mkv", "mov", "avi", "webm", "3gp") ->
                VaultFileCategory.VIDEO
            lowerMime.startsWith("audio/") || ext in setOf("mp3", "wav", "m4a", "aac", "ogg", "flac") ->
                VaultFileCategory.AUDIO
            lowerMime.startsWith("text/") ||
                lowerMime == "application/pdf" ||
                lowerMime.contains("word") ||
                lowerMime.contains("excel") ||
                lowerMime.contains("spreadsheet") ||
                lowerMime.contains("presentation") ||
                lowerMime.contains("document") ||
                ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "md", "json") ->
                VaultFileCategory.DOCUMENT
            else -> VaultFileCategory.OTHER
        }
    }

    fun importUriToVault(context: Context, uri: Uri): VaultFileEntity? {
        return try {
            val resolver = context.contentResolver
            var displayName = "hidden_file_${System.currentTimeMillis()}"
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val queried = cursor.getString(idx)
                        if (!queried.isNullOrBlank()) {
                            displayName = queried
                        }
                    }
                }
            }

            val detectedMime = resolver.getType(uri)
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                )
                ?: "application/octet-stream"

            val id = UUID.randomUUID().toString()
            val storedFileName = "$id.vault"
            val destFile = File(getVaultDir(context), storedFileName)

            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, bufferSize = 16 * 1024)
                    output.fd.sync()
                }
            } ?: return null

            if (!destFile.exists() || destFile.length() == 0L) {
                destFile.delete()
                return null
            }

            val category = resolveCategory(detectedMime, displayName)
            val resolvedMediaUri = resolveMediaStoreContentUri(context, uri, detectedMime)
            val originalPath = extractOriginalRelativePath(context, uri, resolvedMediaUri, category)
            VaultFileEntity(
                id = id,
                displayName = displayName,
                storedFileName = storedFileName,
                mimeType = detectedMime,
                fileCategory = category.name,
                sizeBytes = destFile.length(),
                importedAtMillis = System.currentTimeMillis(),
                originalRelativePath = originalPath,
                originalUriString = (resolvedMediaUri ?: uri).toString()
            )
        } catch (_: Exception) {
            null
        }
    }

    fun normalizeRelativePath(raw: String): String {
        val cleaned = raw.trim()
            .replace('\\', '/')
            .trim('/')
            .replace(Regex("/+"), "/")
        return if (cleaned.isEmpty()) "" else "$cleaned/"
    }

    fun extractOriginalRelativePath(
        context: Context,
        sourceUri: Uri,
        resolvedMediaUri: Uri?,
        category: VaultFileCategory
    ): String {
        // 1. SAF ExternalStorage provider (e.g. primary:DCIM/Camera/IMG_1234.jpg)
        try {
            if (sourceUri.authority == "com.android.externalstorage.documents") {
                val segs = sourceUri.pathSegments
                val docIdx = segs.indexOf("document")
                val docId = when {
                    DocumentsContract.isDocumentUri(context, sourceUri) ->
                        DocumentsContract.getDocumentId(sourceUri)
                    docIdx >= 0 && docIdx + 1 < segs.size ->
                        segs[docIdx + 1]
                    else -> ""
                }
                val subPath = docId.substringAfter(":", "")
                val parent = subPath.substringBeforeLast("/", "")
                val normalized = normalizeRelativePath(parent)
                if (normalized.isNotEmpty()) return normalized
            }
        } catch (_: Exception) {}

        // 2. Direct file:// URI path
        if (sourceUri.scheme == "file") {
            val parent = sourceUri.path?.substringBeforeLast("/", "").orEmpty()
            val rel = extractRelativeFromAbsolutePath(parent)
            if (rel.isNotEmpty()) return rel
        }

        // 3. Query MediaStore metadata (RELATIVE_PATH, BUCKET_DISPLAY_NAME, or _data)
        val resolver = context.contentResolver
        val candidates = listOfNotNull(resolvedMediaUri, sourceUri).distinct()
        for (candidate in candidates) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.query(
                        candidate,
                        arrayOf(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
                        ),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val relIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                            if (relIdx >= 0) {
                                val rel = normalizeRelativePath(cursor.getString(relIdx).orEmpty())
                                if (rel.isNotEmpty()) return rel
                            }
                            val bucketIdx = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                            if (bucketIdx >= 0) {
                                val mapped = mapBucketToRelativePath(cursor.getString(bucketIdx).orEmpty(), category)
                                if (mapped.isNotEmpty()) return mapped
                            }
                        }
                    }
                }

                @Suppress("DEPRECATION")
                val dataCol = MediaStore.MediaColumns.DATA
                resolver.query(candidate, arrayOf(dataCol), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(dataCol)
                        if (idx >= 0) {
                            val fullPath = cursor.getString(idx).orEmpty()
                            val parent = fullPath.substringBeforeLast("/", "")
                            val rel = extractRelativeFromAbsolutePath(parent)
                            if (rel.isNotEmpty()) return rel
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return ""
    }

    private fun extractRelativeFromAbsolutePath(parentDir: String): String {
        if (parentDir.isBlank()) return ""
        val markers = listOf("/storage/emulated/0/", "/sdcard/")
        for (marker in markers) {
            val idx = parentDir.indexOf(marker)
            if (idx >= 0) {
                return normalizeRelativePath(parentDir.substring(idx + marker.length))
            }
        }
        return ""
    }

    private fun mapBucketToRelativePath(bucketName: String, category: VaultFileCategory): String {
        val clean = bucketName.trim().replace("/", "_")
        if (clean.isEmpty()) return ""
        if (clean.equals("Camera", ignoreCase = true) &&
            (category == VaultFileCategory.IMAGE || category == VaultFileCategory.VIDEO)
        ) {
            return "${Environment.DIRECTORY_DCIM}/Camera/"
        }
        return when (category) {
            VaultFileCategory.IMAGE -> "${Environment.DIRECTORY_PICTURES}/$clean/"
            VaultFileCategory.VIDEO -> "${Environment.DIRECTORY_MOVIES}/$clean/"
            VaultFileCategory.AUDIO -> "${Environment.DIRECTORY_MUSIC}/$clean/"
            else -> "${Environment.DIRECTORY_DOWNLOADS}/$clean/"
        }
    }

    fun resolveRestoreRelativePath(vaultFile: VaultFileEntity): String {
        val category = resolveCategory(vaultFile.mimeType, vaultFile.displayName)
        val orig = normalizeRelativePath(vaultFile.originalRelativePath)

        return when (category) {
            VaultFileCategory.IMAGE -> {
                when {
                    orig.startsWith("dcim/", ignoreCase = true) ->
                        "${Environment.DIRECTORY_DCIM}/${orig.substring(5)}"
                    orig.startsWith("pictures/", ignoreCase = true) ->
                        "${Environment.DIRECTORY_PICTURES}/${orig.substring(9)}"
                    orig.isNotEmpty() -> {
                        val leaf = orig.trimEnd('/').substringAfterLast('/')
                        if (leaf.isNotEmpty()) "${Environment.DIRECTORY_PICTURES}/$leaf/"
                        else "${Environment.DIRECTORY_PICTURES}/TaskPulse Restored/"
                    }
                    else -> "${Environment.DIRECTORY_PICTURES}/TaskPulse Restored/"
                }
            }
            VaultFileCategory.VIDEO -> {
                when {
                    orig.startsWith("dcim/", ignoreCase = true) ->
                        "${Environment.DIRECTORY_DCIM}/${orig.substring(5)}"
                    orig.startsWith("movies/", ignoreCase = true) ->
                        "${Environment.DIRECTORY_MOVIES}/${orig.substring(7)}"
                    orig.startsWith("pictures/", ignoreCase = true) ->
                        "${Environment.DIRECTORY_PICTURES}/${orig.substring(9)}"
                    orig.isNotEmpty() -> {
                        val leaf = orig.trimEnd('/').substringAfterLast('/')
                        if (leaf.isNotEmpty()) "${Environment.DIRECTORY_MOVIES}/$leaf/"
                        else "${Environment.DIRECTORY_MOVIES}/TaskPulse Restored/"
                    }
                    else -> "${Environment.DIRECTORY_MOVIES}/TaskPulse Restored/"
                }
            }
            VaultFileCategory.AUDIO -> {
                val allowedAudioRoots = listOf("music/", "audiobooks/", "podcasts/", "alarms/", "notifications/", "ringtones/", "recordings/")
                val matchedRoot = allowedAudioRoots.firstOrNull { orig.startsWith(it, ignoreCase = true) }
                when {
                    matchedRoot != null -> orig
                    orig.isNotEmpty() -> "${Environment.DIRECTORY_MUSIC}/${orig.trimEnd('/').substringAfterLast('/')}/"
                    else -> "${Environment.DIRECTORY_MUSIC}/TaskPulse Restored/"
                }
            }
            VaultFileCategory.DOCUMENT, VaultFileCategory.OTHER -> {
                when {
                    orig.startsWith("download/", ignoreCase = true) ||
                        orig.startsWith("downloads/", ignoreCase = true) ->
                        "${Environment.DIRECTORY_DOWNLOADS}/${orig.substringAfter('/')}"
                    orig.startsWith("documents/", ignoreCase = true) ->
                        "${Environment.DIRECTORY_DOCUMENTS}/${orig.substring(10)}"
                    else -> "${Environment.DIRECTORY_DOWNLOADS}/TaskPulse Restored/"
                }
            }
        }
    }

    fun formatRestoreLocationLabel(vaultFile: VaultFileEntity): String {
        val rel = resolveRestoreRelativePath(vaultFile).trimEnd('/')
        return if (vaultFile.originalRelativePath.isNotBlank()) {
            "Original: $rel"
        } else {
            "Gallery: $rel"
        }
    }

    sealed class OriginHideOutcome {
        data object RemovedSilently : OriginHideOutcome()
        data class NeedsMediaStoreDeleteRequest(val mediaUri: Uri) : OriginHideOutcome()
        data class NeedsRecoverableSecurityIntent(val intentSender: android.content.IntentSender) : OriginHideOutcome()
        data object NotRemovable : OriginHideOutcome()
    }

    /**
     * Attempts to hide/remove the original source file from shared storage/Gallery after it has been
     * safely verified inside TaskPulse's private `.nomedia` vault.
     */
    fun tryHideOriginalFromGallery(context: Context, sourceUri: Uri, mimeType: String): OriginHideOutcome {
        val resolver = context.contentResolver

        // 1. SAF Document URI deletion (often succeeds silently for Downloads / External Storage / Files)
        try {
            if (DocumentsContract.isDocumentUri(context, sourceUri)) {
                if (DocumentsContract.deleteDocument(resolver, sourceUri)) {
                    return OriginHideOutcome.RemovedSilently
                }
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                return OriginHideOutcome.NeedsRecoverableSecurityIntent(e.userAction.actionIntent.intentSender)
            }
        } catch (_: Exception) {}

        // 2. Direct ContentResolver delete (works when app holds write permission or on Android 9 writable URIs)
        val resolvedMediaUri = resolveMediaStoreContentUri(context, sourceUri, mimeType)
        val deleteCandidates = listOfNotNull(sourceUri, resolvedMediaUri).distinct()
        for (candidate in deleteCandidates) {
            try {
                if (resolver.delete(candidate, null, null) > 0) {
                    return OriginHideOutcome.RemovedSilently
                }
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    return OriginHideOutcome.NeedsRecoverableSecurityIntent(e.userAction.actionIntent.intentSender)
                }
            } catch (_: Exception) {}
        }

        // 3. On Android 11+ (API 30+), queue MediaStore.createDeleteRequest for 1-tap system Gallery removal
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && resolvedMediaUri != null) {
            return OriginHideOutcome.NeedsMediaStoreDeleteRequest(resolvedMediaUri)
        }

        return OriginHideOutcome.NotRemovable
    }

    /**
     * Resolves a Gallery/MediaStore content URI from SAF `com.android.providers.media.documents` or
     * Photo Picker URIs so Android's system delete request can remove the original from Gallery.
     */
    fun resolveMediaStoreContentUri(context: Context, uri: Uri, mimeType: String): Uri? {
        val authority = uri.authority ?: ""
        if (authority == MediaStore.AUTHORITY && uri.pathSegments.contains("external")) {
            return uri
        }

        if (authority == "com.android.providers.media.documents" && DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val parts = docId.split(":")
            if (parts.size == 2) {
                val id = parts[1].toLongOrNull() ?: return null
                val collection = when (parts[0]) {
                    "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    "document" -> MediaStore.Files.getContentUri("external")
                    else -> null
                }
                return collection?.let { ContentUris.withAppendedId(it, id) }
            }
        }

        // Android Photo Picker URIs often end with /media/<numeric_id>
        val lastSeg = uri.lastPathSegment?.toLongOrNull()
        if (lastSeg != null && (authority == "media" || uri.toString().contains("media/picker"))) {
            val lowerMime = mimeType.lowercase(Locale.ROOT)
            val collection = when {
                lowerMime.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                lowerMime.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Files.getContentUri("external")
            }
            return ContentUris.withAppendedId(collection, lastSeg)
        }

        return null
    }

    fun buildMediaStoreDeletePendingIntent(context: Context, uris: List<Uri>): PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || uris.isEmpty()) return null
        return try {
            MediaStore.createDeleteRequest(context.contentResolver, uris)
        } catch (_: Exception) {
            // Try filtering individually in case one URI is a cloud-only Photo Picker item
            val validUris = uris.filter { candidate ->
                try {
                    MediaStore.createDeleteRequest(context.contentResolver, listOf(candidate))
                    true
                } catch (_: Exception) {
                    false
                }
            }
            if (validUris.isNotEmpty()) {
                try {
                    MediaStore.createDeleteRequest(context.contentResolver, validUris)
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }

    fun exportVaultFileToUri(context: Context, vaultFile: VaultFileEntity, destinationUri: Uri): Boolean {
        return try {
            val source = getStoredFile(context, vaultFile)
            if (!source.exists()) return false
            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                source.inputStream().use { input ->
                    input.copyTo(output, bufferSize = 16 * 1024)
                }
            } ?: return false
            true
        } catch (_: Exception) {
            false
        }
    }

    fun restoreVaultFileToGallery(context: Context, vaultFile: VaultFileEntity): VaultRestoreOutcome {
        val source = getStoredFile(context, vaultFile)
        if (!source.exists()) {
            return VaultRestoreOutcome.Failed("Stored vault file could not be found.")
        }

        val category = resolveCategory(vaultFile.mimeType, vaultFile.displayName)
        val safeDisplayName = ensureFileExtension(vaultFile.displayName, vaultFile.mimeType)
        val mimeType = vaultFile.mimeType.ifBlank {
            when (category) {
                VaultFileCategory.IMAGE -> "image/jpeg"
                VaultFileCategory.VIDEO -> "video/mp4"
                VaultFileCategory.AUDIO -> "audio/mpeg"
                else -> "application/octet-stream"
            }
        }
        val targetRelativePath = resolveRestoreRelativePath(vaultFile)
        val (collectionUri, fallbackRelativePath) = when (category) {
            VaultFileCategory.IMAGE -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                uri to "${Environment.DIRECTORY_PICTURES}/"
            }
            VaultFileCategory.VIDEO -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                uri to "${Environment.DIRECTORY_MOVIES}/"
            }
            VaultFileCategory.AUDIO -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                uri to "${Environment.DIRECTORY_MUSIC}/"
            }
            VaultFileCategory.DOCUMENT, VaultFileCategory.OTHER -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                        "${Environment.DIRECTORY_DOWNLOADS}/"
                } else {
                    return VaultRestoreOutcome.NeedsCustomExportPicker(
                        "Choose where to save '${vaultFile.displayName}' on Android 9."
                    )
                }
            }
        }

        val resolver = context.contentResolver
        val nowSeconds = System.currentTimeMillis() / 1000L

        fun buildValues(relPath: String): ContentValues {
            return ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeDisplayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.DATE_ADDED, nowSeconds)
                put(MediaStore.MediaColumns.DATE_MODIFIED, nowSeconds)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
        }

        var usedRelPath = targetRelativePath
        val insertedUri: Uri? = try {
            resolver.insert(collectionUri, buildValues(usedRelPath))
        } catch (_: IllegalArgumentException) {
            usedRelPath = fallbackRelativePath
            try {
                resolver.insert(collectionUri, buildValues(usedRelPath))
            } catch (_: Exception) {
                null
            }
        } catch (_: SecurityException) {
            return VaultRestoreOutcome.NeedsCustomExportPicker(
                "Select a destination folder to restore '${vaultFile.displayName}'."
            )
        } catch (_: Exception) {
            null
        }

        if (insertedUri == null) {
            return VaultRestoreOutcome.NeedsCustomExportPicker(
                "Could not insert directly into MediaStore; pick a destination folder."
            )
        }

        return try {
            var copiedBytes = 0L
            resolver.openOutputStream(insertedUri)?.use { output ->
                source.inputStream().use { input ->
                    copiedBytes = input.copyTo(output, bufferSize = 16 * 1024)
                }
                output.flush()
            } ?: run {
                try { resolver.delete(insertedUri, null, null) } catch (_: Exception) {}
                return VaultRestoreOutcome.Failed("Failed to open destination stream in Gallery.")
            }

            if (source.length() > 0L && copiedBytes == 0L) {
                try { resolver.delete(insertedUri, null, null) } catch (_: Exception) {}
                return VaultRestoreOutcome.Failed("Restored file had 0 bytes; kept safe inside vault.")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publishValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(insertedUri, publishValues, null, null)
            }

            // Trigger media scanner if physical file path is available so Gallery apps refresh immediately
            try {
                @Suppress("DEPRECATION")
                val dataCol = MediaStore.MediaColumns.DATA
                resolver.query(insertedUri, arrayOf(dataCol), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(dataCol)
                        if (idx >= 0) {
                            val filePath = cursor.getString(idx)
                            if (!filePath.isNullOrBlank()) {
                                MediaScannerConnection.scanFile(
                                    context.applicationContext,
                                    arrayOf(filePath),
                                    arrayOf(mimeType),
                                    null
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            VaultRestoreOutcome.RestoredToGallery(
                restoredUri = insertedUri,
                destinationPath = usedRelPath.trimEnd('/'),
                removedFromVault = false
            )
        } catch (_: SecurityException) {
            try { resolver.delete(insertedUri, null, null) } catch (_: Exception) {}
            VaultRestoreOutcome.NeedsCustomExportPicker(
                "Select a destination folder to restore '${vaultFile.displayName}'."
            )
        } catch (e: Exception) {
            try { resolver.delete(insertedUri, null, null) } catch (_: Exception) {}
            VaultRestoreOutcome.Failed(e.message ?: "Restore to Gallery failed.")
        }
    }

    private fun ensureFileExtension(displayName: String, mimeType: String): String {
        if (displayName.contains('.') && displayName.substringAfterLast('.').length in 1..6) {
            return displayName
        }
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        return if (!ext.isNullOrBlank()) "$displayName.$ext" else displayName
    }

    fun createShareOrOpenUri(context: Context, vaultFile: VaultFileEntity): Uri? {
        return try {
            val source = getStoredFile(context, vaultFile)
            if (!source.exists()) return null

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeName = vaultFile.displayName.replace(Regex("[^a-zA-Z0-9._ -]"), "_")
            val tempCopy = File(exportDir, safeName)
            source.inputStream().use { input ->
                FileOutputStream(tempCopy).use { output ->
                    input.copyTo(output, bufferSize = 16 * 1024)
                }
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempCopy)
        } catch (_: Exception) {
            null
        }
    }

    fun openFileExternally(context: Context, vaultFile: VaultFileEntity): Boolean {
        val uri = createShareOrOpenUri(context, vaultFile) ?: return false
        return try {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, vaultFile.mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(viewIntent, "Open with"))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun shareFileExternally(context: Context, vaultFile: VaultFileEntity): Boolean {
        val uri = createShareOrOpenUri(context, vaultFile) ?: return false
        return try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = vaultFile.mimeType.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, vaultFile.displayName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export / Share File"))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun deleteFromDisk(context: Context, vaultFile: VaultFileEntity): Boolean {
        return try {
            val file = getStoredFile(context, vaultFile)
            if (file.exists()) file.delete() else true
        } catch (_: Exception) {
            false
        }
    }

    fun decodeSampledBitmap(file: File, maxDim: Int = 1024): Bitmap? {
        return try {
            if (!file.exists()) return null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var sampleSize = 1
            while (opts.outWidth / sampleSize > maxDim || opts.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        } catch (_: Exception) {
            null
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
