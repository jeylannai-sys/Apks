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

    companion object {
        fun fromCode(code: String?): AppCurrency {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: PHP
        }
    }
}
