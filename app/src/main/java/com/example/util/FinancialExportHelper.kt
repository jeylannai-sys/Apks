package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.ExpenseEntity
import com.example.model.AppCurrency
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FinancialExportHelper {

    /**
     * Generates a CSV file containing all financial transactions and triggers an Android Share Intent.
     */
    fun exportTransactionsCsv(
        context: Context,
        transactions: List<ExpenseEntity>,
        currency: AppCurrency
    ): File? {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(exportDir, "TaskPulse_Transactions_$timeStamp.csv")

            FileWriter(file).use { writer ->
                // CSV Header
                writer.append("ID,Date,Time,Type,Category,Amount,Currency,Note\n")

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

                for (tx in transactions) {
                    val dateStr = dateFormat.format(Date(tx.dateMillis))
                    val timeStr = timeFormat.format(Date(tx.dateMillis))
                    val typeStr = if (tx.isExpense) "Expense" else "Income"
                    val cleanCategory = escapeCsv(tx.category)
                    val cleanNote = escapeCsv(tx.note.ifBlank { tx.title })
                    val amountStr = String.format(Locale.US, "%.2f", tx.amount)

                    writer.append("${tx.id},$dateStr,$timeStr,$typeStr,$cleanCategory,$amountStr,${currency.code},$cleanNote\n")
                }
                writer.flush()
            }
            file
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates a comprehensive Text/Statement Summary for sharing or printing.
     */
    fun generateStatementSummary(
        transactions: List<ExpenseEntity>,
        currency: AppCurrency
    ): String {
        val totalIncome = transactions.filter { !it.isExpense }.sumOf { it.amount }
        val totalExpenses = transactions.filter { it.isExpense }.sumOf { it.amount }
        val netSavings = totalIncome - totalExpenses
        val savingsRate = if (totalIncome > 0) (netSavings / totalIncome * 100).coerceAtLeast(0.0) else 0.0

        val categoryGroups = transactions.filter { it.isExpense }.groupBy { it.category }
            .mapValues { it.value.sumOf { tx -> tx.amount } }
            .toList()
            .sortedByDescending { it.second }

        val sb = StringBuilder()
        sb.append("═══════════════════════════════════════\n")
        sb.append("   TASKPULSE FINANCIAL STATEMENT\n")
        val nowStr = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date())
        sb.append("   Generated: $nowStr\n")
        sb.append("   Currency: ${currency.displayName} (${currency.symbol} ${currency.code})\n")
        sb.append("═══════════════════════════════════════\n\n")

        sb.append("SUMMARY TOTALS:\n")
        sb.append("• Total Income:     +${currency.format(totalIncome)}\n")
        sb.append("• Total Expenses:   -${currency.format(totalExpenses)}\n")
        val sign = if (netSavings >= 0) "+" else ""
        sb.append("• Net Savings:      $sign${currency.format(netSavings)} (${String.format(Locale.US, "%.1f", savingsRate)}%)\n\n")

        sb.append("EXPENSES BY CATEGORY:\n")
        if (categoryGroups.isEmpty()) {
            sb.append("  (No expenses logged yet)\n")
        } else {
            categoryGroups.forEachIndexed { index, (cat, amount) ->
                val pct = if (totalExpenses > 0) (amount / totalExpenses * 100) else 0.0
                sb.append("  ${index + 1}. $cat: ${currency.format(amount)} (${String.format(Locale.US, "%.1f", pct)}%)\n")
            }
        }

        sb.append("\nRECENT TRANSACTIONS (${transactions.size} total entries):\n")
        val recentFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        transactions.take(15).forEach { tx ->
            val signStr = if (tx.isExpense) "-" else "+"
            val dateStr = recentFormat.format(Date(tx.dateMillis))
            sb.append("• [$dateStr] ${tx.title} (${tx.category}): $signStr${currency.format(tx.amount)}\n")
        }

        sb.append("\n═══════════════════════════════════════\n")
        sb.append("Exported from TaskPulse App • Personal Productivity Hub\n")

        return sb.toString()
    }

    /**
     * Triggers standard Android share intent with FileProvider for CSV.
     */
    fun shareCsvFile(context: Context, file: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "TaskPulse Financial Statement (${file.name})")
                putExtra(Intent.EXTRA_TEXT, "Here is your exported TaskPulse financial transactions CSV spreadsheet.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Financial Statement via"))
        } catch (_: Exception) {}
    }

    /**
     * Shares statement summary as plain text.
     */
    fun shareStatementText(context: Context, summaryText: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "TaskPulse Financial Statement Summary")
                putExtra(Intent.EXTRA_TEXT, summaryText)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Statement Summary via"))
        } catch (_: Exception) {}
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
