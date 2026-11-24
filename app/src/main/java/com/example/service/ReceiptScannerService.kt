package com.example.service

import android.graphics.Bitmap
import android.util.Log
import com.example.model.ExpenseCategories
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

data class ExtractedReceipt(
    val merchant: String,
    val totalAmount: Double,
    val category: String,
    val dateIso: String,
    val notes: String
)

sealed interface ReceiptScanUiState {
    data object Idle : ReceiptScanUiState
    data object Scanning : ReceiptScanUiState
    data class Success(val receipt: ExtractedReceipt) : ReceiptScanUiState
    data class Error(val message: String) : ReceiptScanUiState
}

class ReceiptScannerService {

    companion object {
        private const val TAG = "ReceiptScannerService"
        private const val MODEL_NAME = "gemini-3.8-flash"

        fun parseReceiptJson(jsonString: String): ExtractedReceipt {
            val json = JSONObject(jsonString)
            val merchant = json.optString("merchant", "Receipt Purchase").trim()
                .ifBlank { "Receipt Purchase" }
            val totalAmount = json.optDouble("totalAmount", 0.0)
                .takeIf { !it.isNaN() && it >= 0.0 } ?: 0.0
            val rawCategory = json.optString("category", "Food & Dining").trim()
            val matchedCategory = ExpenseCategories.list
                .firstOrNull { it.name.equals(rawCategory, ignoreCase = true) }?.name
                ?: "Food & Dining"
            val dateIso = json.optString("dateIso", "").trim()
            val notes = json.optString("notes", "").trim()

            return ExtractedReceipt(
                merchant = merchant,
                totalAmount = totalAmount,
                category = matchedCategory,
                dateIso = dateIso,
                notes = notes
            )
        }

        fun parseDateIsoToMillis(dateIso: String): Long {
            if (dateIso.isBlank()) return System.currentTimeMillis()
            return try {
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
                fmt.parse(dateIso)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    private val receiptSchema = Schema.obj(
        properties = mapOf(
            "merchant" to Schema.string(description = "Store, restaurant, or merchant name on the receipt"),
            "totalAmount" to Schema.double(description = "Final grand total paid on the receipt as a positive number"),
            "category" to Schema.enumeration(
                values = ExpenseCategories.list.map { it.name },
                description = "Best matching expense category from the allowed list"
            ),
            "dateIso" to Schema.string(description = "Receipt date in YYYY-MM-DD format if visible, else empty string"),
            "notes" to Schema.string(description = "Short comma-separated summary of main items purchased on the receipt")
        ),
        optionalProperties = listOf("dateIso", "notes")
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = receiptSchema
            }
        )
    }

    suspend fun extractReceipt(bitmap: Bitmap): ReceiptScanUiState {
        return try {
            val scaledBitmap = withContext(Dispatchers.Default) {
                scaleBitmapForUpload(bitmap, maxDim = 1600)
            }

            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(scaledBitmap)
                        text(
                            "Extract the receipt details: merchant name, grand total amount paid, " +
                                "best expense category, date in YYYY-MM-DD format if printed, and a concise " +
                                "summary of key purchased items in notes."
                        )
                    }
                )
            }

            val text = response.text
            if (text.isNullOrBlank()) {
                ReceiptScanUiState.Error("The AI model returned an empty receipt response. Please try a clearer photo.")
            } else {
                val parsed = parseReceiptJson(text)
                if (parsed.totalAmount <= 0.0) {
                    ReceiptScanUiState.Error("Could not detect a valid total amount from this receipt. Try capturing the total clearly.")
                } else {
                    ReceiptScanUiState.Success(parsed)
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Receipt extraction timed out", e)
            ReceiptScanUiState.Error("Receipt scan timed out after 60 seconds. Please check your connection and try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Receipt extraction failed", e)
            ReceiptScanUiState.Error(e.localizedMessage ?: "Failed to extract receipt details.")
        }
    }

    private fun scaleBitmapForUpload(source: Bitmap, maxDim: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxDim && h <= maxDim) return source
        val ratio = maxDim.toFloat() / maxOf(w, h).toFloat()
        val newW = (w * ratio).toInt().coerceAtLeast(1)
        val newH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }
}
