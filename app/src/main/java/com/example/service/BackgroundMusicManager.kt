package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

data class BackgroundMusicState(
    val enabled: Boolean = false,
    val autoPlayOnOpen: Boolean = true,
    val loop: Boolean = true,
    val pauseWhenLeavingApp: Boolean = true,
    val volume: Float = 0.45f,
    val trackName: String = "Default Soft Ambient",
    val hasCustomFile: Boolean = false,
    val isPlaying: Boolean = false
)

object BackgroundMusicManager {

    private const val PREFS_NAME = "taskpulse_bgm_prefs"
    private const val KEY_ENABLED = "bgm_enabled"
    private const val KEY_AUTO_PLAY = "bgm_auto_play_on_open"
    private const val KEY_LOOP = "bgm_loop"
    private const val KEY_PAUSE_LEAVE = "bgm_pause_on_leave"
    private const val KEY_VOLUME = "bgm_volume"
    private const val KEY_TRACK_NAME = "bgm_track_name"

    private const val BGM_DIR = "bgm"
    private const val CUSTOM_TRACK_FILENAME = "custom_bgm_track"
    private const val DEFAULT_AMBIENT_FILENAME = "default_ambient.wav"
    private const val DEFAULT_AMBIENT_NAME = "Default Soft Ambient"

    private val lock = Any()
    private var initialized = false
    private var mediaPlayer: MediaPlayer? = null

    private val _state = MutableStateFlow(BackgroundMusicState())
    val state: StateFlow<BackgroundMusicState> = _state.asStateFlow()

    fun init(context: Context): BackgroundMusicState {
        synchronized(lock) {
            if (initialized) return _state.value
            val appCtx = context.applicationContext
            val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val customFile = getCustomTrackFile(appCtx)
            val hasCustom = customFile.exists() && customFile.length() > 0L

            val loaded = BackgroundMusicState(
                enabled = prefs.getBoolean(KEY_ENABLED, false),
                autoPlayOnOpen = prefs.getBoolean(KEY_AUTO_PLAY, true),
                loop = prefs.getBoolean(KEY_LOOP, true),
                pauseWhenLeavingApp = prefs.getBoolean(KEY_PAUSE_LEAVE, true),
                volume = prefs.getFloat(KEY_VOLUME, 0.45f).coerceIn(0f, 1f),
                trackName = if (hasCustom) {
                    prefs.getString(KEY_TRACK_NAME, "Custom Music") ?: "Custom Music"
                } else {
                    DEFAULT_AMBIENT_NAME
                },
                hasCustomFile = hasCustom,
                isPlaying = mediaPlayer?.isPlaying == true
            )
            _state.value = loaded
            initialized = true
            return loaded
        }
    }

    private fun savePrefs(context: Context, state: BackgroundMusicState) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ENABLED, state.enabled)
            .putBoolean(KEY_AUTO_PLAY, state.autoPlayOnOpen)
            .putBoolean(KEY_LOOP, state.loop)
            .putBoolean(KEY_PAUSE_LEAVE, state.pauseWhenLeavingApp)
            .putFloat(KEY_VOLUME, state.volume)
            .putString(KEY_TRACK_NAME, state.trackName)
            .apply()
    }

    private fun getBgmDir(context: Context): File {
        val dir = File(context.applicationContext.filesDir, BGM_DIR)
        if (!dir.exists()) dir.mkdirs()
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) {
            try { nomedia.createNewFile() } catch (_: Exception) {}
        }
        return dir
    }

    fun getCustomTrackFile(context: Context): File {
        return File(getBgmDir(context), CUSTOM_TRACK_FILENAME)
    }

    private fun ensureDefaultAmbientWav(context: Context): File {
        val file = File(getBgmDir(context), DEFAULT_AMBIENT_FILENAME)
        if (file.exists() && file.length() > 44L) return file

        // Generate a warm, peaceful 6-second seamless ambient chord loop (16-bit mono PCM WAV)
        val sampleRate = 22050
        val durationSec = 6
        val totalSamples = sampleRate * durationSec
        val pcm = ShortArray(totalSamples)
        // Cmaj9 soft chord frequencies: C4, E4, G4, D5
        val freqs = doubleArrayOf(261.63, 329.63, 392.00, 587.33)
        val fadeSamples = (sampleRate * 0.25).toInt()

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            var mix = 0.0
            for ((idx, f) in freqs.withIndex()) {
                val slowPulse = 0.7 + 0.3 * sin(2.0 * PI * (0.1666 * (idx + 1)) * t)
                mix += sin(2.0 * PI * f * t) * slowPulse
            }
            mix /= freqs.size

            val env = when {
                i < fadeSamples -> i.toDouble() / fadeSamples
                i > totalSamples - fadeSamples -> (totalSamples - i).toDouble() / fadeSamples
                else -> 1.0
            }
            pcm[i] = (mix * env * 9500.0).toInt().toShort()
        }

        val dataSize = pcm.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM chunk size
            putShort(1) // AudioFormat = PCM
            putShort(1) // Mono
            putInt(sampleRate)
            putInt(sampleRate * 2) // ByteRate
            putShort(2) // BlockAlign
            putShort(16) // BitsPerSample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()

        val outBytes = ByteArrayOutputStream(44 + dataSize)
        outBytes.write(header)
        val sampleBuf = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { sampleBuf.putShort(it) }
        outBytes.write(sampleBuf.array())

        FileOutputStream(file).use { fos ->
            fos.write(outBytes.toByteArray())
            fos.fd.sync()
        }
        return file
    }

    fun importMusicFile(context: Context, uri: Uri): Result<String> {
        val appCtx = context.applicationContext
        init(appCtx)
        return try {
            val resolver = appCtx.contentResolver
            var displayName = "Custom Track"
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val queried = cursor.getString(idx)
                        if (!queried.isNullOrBlank()) displayName = queried
                    }
                }
            }

            val dir = getBgmDir(appCtx)
            val tempFile = File(dir, "tmp_import_${System.currentTimeMillis()}")
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output, bufferSize = 16 * 1024)
                    output.fd.sync()
                }
            } ?: return Result.failure(IllegalStateException("Could not read selected audio file"))

            if (!tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                return Result.failure(IllegalStateException("Selected audio file is empty"))
            }

            // Validate that Android MediaPlayer can decode this audio file before committing
            val probe = MediaPlayer()
            try {
                probe.setDataSource(tempFile.absolutePath)
                probe.prepare()
            } catch (e: Exception) {
                tempFile.delete()
                return Result.failure(IllegalArgumentException("Unsupported audio format", e))
            } finally {
                try { probe.release() } catch (_: Exception) {}
            }

            synchronized(lock) {
                releasePlayerInternal()
                val dest = getCustomTrackFile(appCtx)
                if (dest.exists()) dest.delete()
                if (!tempFile.renameTo(dest)) {
                    tempFile.copyTo(dest, overwrite = true)
                    tempFile.delete()
                }

                val updated = _state.value.copy(
                    enabled = true,
                    hasCustomFile = true,
                    trackName = displayName
                )
                _state.value = updated
                savePrefs(appCtx, updated)
            }

            play(appCtx)
            Result.success(displayName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun clearCustomTrack(context: Context) {
        val appCtx = context.applicationContext
        init(appCtx)
        val wasEnabledAndPlaying = synchronized(lock) {
            val shouldRestartDefault = _state.value.enabled && _state.value.isPlaying
            releasePlayerInternal()
            val customFile = getCustomTrackFile(appCtx)
            if (customFile.exists()) customFile.delete()
            val updated = _state.value.copy(
                hasCustomFile = false,
                trackName = DEFAULT_AMBIENT_NAME,
                isPlaying = false
            )
            _state.value = updated
            savePrefs(appCtx, updated)
            shouldRestartDefault
        }
        if (wasEnabledAndPlaying) {
            play(appCtx)
        }
    }

    fun play(context: Context): Boolean {
        val appCtx = context.applicationContext
        init(appCtx)
        synchronized(lock) {
            val current = _state.value
            val trackFile = if (current.hasCustomFile && getCustomTrackFile(appCtx).exists()) {
                getCustomTrackFile(appCtx)
            } else {
                ensureDefaultAmbientWav(appCtx)
            }

            val existing = mediaPlayer
            if (existing != null) {
                try {
                    if (!existing.isPlaying) {
                        existing.isLooping = current.loop
                        existing.setVolume(current.volume, current.volume)
                        existing.start()
                    }
                    _state.value = _state.value.copy(isPlaying = true)
                    return true
                } catch (_: Exception) {
                    releasePlayerInternal()
                }
            }

            return try {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setDataSource(trackFile.absolutePath)
                    isLooping = current.loop
                    setVolume(current.volume, current.volume)
                    setOnCompletionListener {
                        synchronized(lock) {
                            if (!_state.value.loop) {
                                _state.value = _state.value.copy(isPlaying = false)
                            }
                        }
                    }
                    prepare()
                    start()
                }
                mediaPlayer = player
                _state.value = _state.value.copy(isPlaying = true)
                true
            } catch (_: Exception) {
                releasePlayerInternal()
                false
            }
        }
    }

    fun pause() {
        synchronized(lock) {
            try {
                mediaPlayer?.let {
                    if (it.isPlaying) it.pause()
                }
            } catch (_: Exception) {}
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    fun stop() {
        synchronized(lock) {
            releasePlayerInternal()
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    fun togglePlayPause(context: Context) {
        init(context)
        if (_state.value.isPlaying) {
            pause()
        } else {
            if (!_state.value.enabled) {
                setEnabled(context, true)
            } else {
                play(context)
            }
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val appCtx = context.applicationContext
        init(appCtx)
        synchronized(lock) {
            val updated = _state.value.copy(enabled = enabled)
            _state.value = updated
            savePrefs(appCtx, updated)
        }
        if (enabled) {
            play(appCtx)
        } else {
            stop()
        }
    }

    fun setAutoPlayOnOpen(context: Context, autoPlay: Boolean) {
        val appCtx = context.applicationContext
        init(appCtx)
        synchronized(lock) {
            val updated = _state.value.copy(autoPlayOnOpen = autoPlay)
            _state.value = updated
            savePrefs(appCtx, updated)
        }
    }

    fun setLoop(context: Context, loop: Boolean) {
        val appCtx = context.applicationContext
        init(appCtx)
        synchronized(lock) {
            val updated = _state.value.copy(loop = loop)
            _state.value = updated
            savePrefs(appCtx, updated)
            try {
                mediaPlayer?.isLooping = loop
            } catch (_: Exception) {}
        }
    }

    fun setPauseWhenLeavingApp(context: Context, pauseOnLeave: Boolean) {
        val appCtx = context.applicationContext
        init(appCtx)
        synchronized(lock) {
            val updated = _state.value.copy(pauseWhenLeavingApp = pauseOnLeave)
            _state.value = updated
            savePrefs(appCtx, updated)
        }
    }

    fun setVolume(context: Context, volume: Float) {
        val appCtx = context.applicationContext
        init(appCtx)
        val clamped = volume.coerceIn(0f, 1f)
        synchronized(lock) {
            val updated = _state.value.copy(volume = clamped)
            _state.value = updated
            savePrefs(appCtx, updated)
            try {
                mediaPlayer?.setVolume(clamped, clamped)
            } catch (_: Exception) {}
        }
    }

    fun onAppForegrounded(context: Context) {
        val current = init(context)
        if (current.enabled && current.autoPlayOnOpen && !current.isPlaying) {
            play(context)
        }
    }

    fun onAppBackgrounded() {
        if (_state.value.pauseWhenLeavingApp && _state.value.isPlaying) {
            pause()
        }
    }

    private fun releasePlayerInternal() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
    }
}
