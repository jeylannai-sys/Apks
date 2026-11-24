package com.example.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import org.json.JSONObject

enum class SupportReportType(val label: String) {
    PROBLEM("Report a Problem"),
    SUGGESTION("Suggestion")
}

data class GoogleReporterProfile(
    val name: String = "",
    val email: String = "",
    val isFromGoogle: Boolean = false
)

sealed class SupportSendOutcome {
    data object LaunchedEmailApp : SupportSendOutcome()
    data object CopiedToClipboardFallback : SupportSendOutcome()
    data class Failed(val reason: String) : SupportSendOutcome()
}

object SupportReportHelper {

    const val SUPPORT_EMAIL = "jeylann.ai@gmail.com"
    const val GMAIL_PACKAGE_NAME = "com.google.android.gm"
    private const val TAG = "SupportReportHelper"
    private const val PREFS_NAME = "taskpulse_support_prefs"
    private const val KEY_GOOGLE_NAME = "support_google_name"
    private const val KEY_GOOGLE_EMAIL = "support_google_email"
    private const val KEY_IS_FROM_GOOGLE = "support_is_from_google"

    fun loadCachedProfile(context: Context): GoogleReporterProfile {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return GoogleReporterProfile(
            name = prefs.getString(KEY_GOOGLE_NAME, "") ?: "",
            email = prefs.getString(KEY_GOOGLE_EMAIL, "") ?: "",
            isFromGoogle = prefs.getBoolean(KEY_IS_FROM_GOOGLE, false)
        )
    }

    fun saveCachedProfile(context: Context, profile: GoogleReporterProfile) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GOOGLE_NAME, profile.name.trim())
            .putString(KEY_GOOGLE_EMAIL, profile.email.trim())
            .putBoolean(KEY_IS_FROM_GOOGLE, profile.isFromGoogle)
            .apply()
    }

    fun parseGoogleIdTokenClaims(idToken: String): Pair<String, String> {
        return try {
            val parts = idToken.split('.')
            if (parts.size < 2) return "" to ""
            val payloadBytes = Base64.decode(
                parts[1],
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            val json = JSONObject(String(payloadBytes, Charsets.UTF_8))
            val name = json.optString("name", "").trim()
            val email = json.optString("email", "").trim()
            name to email
        } catch (_: Exception) {
            "" to ""
        }
    }

    private fun extractProfileFromGoogleCredential(cred: GoogleIdTokenCredential): GoogleReporterProfile {
        val (jwtName, jwtEmail) = parseGoogleIdTokenClaims(cred.idToken)
        val combinedName = listOfNotNull(cred.givenName, cred.familyName)
            .joinToString(" ")
            .trim()
        val email = cred.id.trim().ifBlank { jwtEmail }
        val resolvedName = cred.displayName?.trim()?.takeIf { it.isNotBlank() }
            ?: combinedName.takeIf { it.isNotBlank() }
            ?: jwtName.takeIf { it.isNotBlank() }
            ?: email.substringBefore('@').replace('.', ' ').trim()

        return GoogleReporterProfile(
            name = resolvedName,
            email = email,
            isFromGoogle = email.isNotBlank()
        )
    }

    suspend fun attemptSilentGoogleProfileLookup(context: Context): GoogleReporterProfile? {
        val activity = context.findActivity() ?: return null
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return null
        }
        if (clientId.isBlank()) return null

        return try {
            val credentialManager = CredentialManager.create(activity)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(activity, request)
            val cred = result.credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
                extractProfileFromGoogleCredential(googleCred).also { profile ->
                    if (profile.email.isNotBlank()) {
                        saveCachedProfile(context, profile)
                    }
                }
            } else {
                null
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Silent Google profile lookup cancelled: ${e.message}")
            null
        } catch (e: Exception) {
            Log.d(TAG, "Silent Google profile lookup unavailable: ${e.message}")
            null
        }
    }

    suspend fun launchInteractiveGoogleProfilePicker(context: Context): Result<GoogleReporterProfile> {
        val activity = context.findActivity()
            ?: return Result.failure(IllegalStateException("Activity context unavailable"))
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return Result.failure(IllegalStateException("Google Sign-In client ID is not configured"))
        }

        return try {
            val credentialManager = CredentialManager.create(activity)
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            val result = credentialManager.getCredential(activity, request)
            val cred = result.credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
                val profile = extractProfileFromGoogleCredential(googleCred)
                saveCachedProfile(context, profile)
                Result.success(profile)
            } else {
                Result.failure(IllegalStateException("Unexpected Google credential type"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google account picker cancelled: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.w(TAG, "Google account picker failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun buildSupportEmailSubject(type: SupportReportType, reporterName: String): String {
        val cleanName = reporterName.replace(Regex("[\\r\\n]+"), " ").trim().ifBlank { "TaskPulse User" }
        return "[TaskPulse ${type.label}] from $cleanName"
    }

    fun buildSupportEmailBody(
        type: SupportReportType,
        reporterName: String,
        reporterEmail: String,
        message: String,
        appVersion: String = "1.0",
        androidVersion: String = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
        deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    ): String {
        return buildString {
            appendLine("Type: ${type.label}")
            appendLine("Name: ${reporterName.trim().ifBlank { "Not provided" }}")
            appendLine("Email: ${reporterEmail.trim().ifBlank { "Not provided" }}")
            appendLine()
            appendLine("Message:")
            appendLine(message.trim())
            appendLine()
            appendLine("---")
            appendLine("App: TaskPulse v$appVersion")
            appendLine("Device: $deviceModel (Android $androidVersion)")
        }
    }

    fun buildSupportEmailIntent(
        type: SupportReportType,
        reporterName: String,
        reporterEmail: String,
        message: String
    ): Intent {
        val subject = buildSupportEmailSubject(type, reporterName)
        val body = buildSupportEmailBody(type, reporterName, reporterEmail, message)
        return Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$SUPPORT_EMAIL")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
    }

    fun buildGmailDirectComposeIntent(
        type: SupportReportType,
        reporterName: String,
        reporterEmail: String,
        message: String
    ): Intent {
        val subject = buildSupportEmailSubject(type, reporterName)
        val body = buildSupportEmailBody(type, reporterName, reporterEmail, message)
        return Intent(Intent.ACTION_SEND).apply {
            this.type = "text/plain"
            setPackage(GMAIL_PACKAGE_NAME)
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
    }

    fun sendSupportEmail(
        context: Context,
        type: SupportReportType,
        reporterName: String,
        reporterEmail: String,
        message: String
    ): SupportSendOutcome {
        val subject = buildSupportEmailSubject(type, reporterName)
        val body = buildSupportEmailBody(type, reporterName, reporterEmail, message)
        val needsNewTask = context.findActivity() == null

        // 1. Prefer opening Gmail compose directly with recipient, subject, and message pasted in
        val gmailSendIntent = buildGmailDirectComposeIntent(type, reporterName, reporterEmail, message).apply {
            if (needsNewTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            if (gmailSendIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(gmailSendIntent)
                return SupportSendOutcome.LaunchedEmailApp
            }
        } catch (_: Exception) {}

        val gmailSendToIntent = buildSupportEmailIntent(type, reporterName, reporterEmail, message).apply {
            setPackage(GMAIL_PACKAGE_NAME)
            if (needsNewTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            if (gmailSendToIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(gmailSendToIntent)
                return SupportSendOutcome.LaunchedEmailApp
            }
        } catch (_: Exception) {}

        // 2. Fallback to any installed email client via ACTION_SENDTO
        val sendToIntent = buildSupportEmailIntent(type, reporterName, reporterEmail, message)
        try {
            if (sendToIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(
                    Intent.createChooser(sendToIntent, "Send to $SUPPORT_EMAIL").apply {
                        if (needsNewTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                return SupportSendOutcome.LaunchedEmailApp
            }
        } catch (_: Exception) {}

        // 3. Fallback to ACTION_SEND message/rfc822
        val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
            this.type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            if (fallbackIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(
                    Intent.createChooser(fallbackIntent, "Send to $SUPPORT_EMAIL").apply {
                        if (needsNewTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                return SupportSendOutcome.LaunchedEmailApp
            }
        } catch (_: Exception) {}

        // 4. Last resort when no email client is installed on device/emulator: copy report to clipboard
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipText = "To: $SUPPORT_EMAIL\nSubject: $subject\n\n$body"
            clipboard?.setPrimaryClip(ClipData.newPlainText("TaskPulse Support Report", clipText))
            SupportSendOutcome.CopiedToClipboardFallback
        } catch (e: Exception) {
            SupportSendOutcome.Failed(e.message ?: "Unable to open Gmail compose.")
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
