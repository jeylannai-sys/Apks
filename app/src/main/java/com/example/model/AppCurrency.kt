package com.example.model

import java.util.Locale

enum class AppCurrency(
    val code: String,
    val symbol: String,
    val displayName: String,
    val flag: String
) {
    PHP("PHP", "₱", "Philippine Peso", "🇵🇭"),
    USD("USD", "$", "US Dollar", "🇺🇸"),
    HKD("HKD", "HK$", "Hong Kong Dollar", "🇭🇰"),
    EUR("EUR", "€", "Euro", "🇪🇺"),
    GBP("GBP", "£", "British Pound", "🇬🇧"),
    JPY("JPY", "¥", "Japanese Yen", "🇯🇵"),
    SGD("SGD", "S$", "Singapore Dollar", "🇸🇬"),
    CAD("CAD", "CA$", "Canadian Dollar", "🇨🇦"),
    AUD("AUD", "A$", "Australian Dollar", "🇦🇺"),
    INR("INR", "₹", "Indian Rupee", "🇮🇳"),
    KRW("KRW", "₩", "South Korean Won", "🇰🇷"),
    AED("AED", "AED", "UAE Dirham", "🇦🇪");

    fun format(amount: Double): String {
        return when (this) {
            JPY, KRW -> String.format(Locale.US, "%s%,.0f", symbol, amount)
            else -> String.format(Locale.US, "%s%,.2f", symbol, amount)
        }
    }

    fun formatCompact(amount: Double): String {
        return when (this) {
            JPY, KRW -> String.format(Locale.US, "%s%,.0f", symbol, amount)
            else -> String.format(Locale.US, "%s%,.0f", symbol, amount)
        }
    }

    fun formatMasked(amount: Double, hidden: Boolean, compact: Boolean = false): String {
        val formatted = if (compact) formatCompact(amount) else format(amount)
        return mask(formatted, hidden)
    }

    fun mask(formatted: String, hidden: Boolean): String {
        if (!hidden) return formatted
        val trimmed = formatted.trim()
        val isNegative = trimmed.startsWith("-") || trimmed.removePrefix(symbol).startsWith("-")
        val isPositive = trimmed.startsWith("+")
        val prefix = when {
            isNegative -> "-"
            isPositive -> "+"
            else -> ""
        }
        return "$prefix$symbol$MASKED_DIGITS"
    }

    fun maskedOnly(): String = "$symbol$MASKED_DIGITS"

    companion object {
        const val MASKED_DIGITS = "******"
        const val MASKED_AMOUNT = "******"

        fun fromCode(code: String?): AppCurrency {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: PHP
        }

        fun mask(formatted: String, hidden: Boolean): String {
            if (!hidden) return formatted
            val trimmed = formatted.trim()
            val prefix = when {
                trimmed.startsWith("-") -> "-"
                trimmed.startsWith("+") -> "+"
                else -> ""
            }
            val unsigned = trimmed.removePrefix("-").removePrefix("+")
            val detectedSymbol = entries
                .sortedByDescending { it.symbol.length }
                .firstOrNull { unsigned.startsWith(it.symbol) }
                ?.symbol
                ?: ""
            val afterSymbol = unsigned.removePrefix(detectedSymbol)
            val innerNegative = if (prefix.isEmpty() && afterSymbol.startsWith("-")) "-" else ""
            return "$prefix$innerNegative$detectedSymbol$MASKED_DIGITS"
        }
    }
}
