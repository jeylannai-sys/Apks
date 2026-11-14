package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryInfo(
    val name: String,
    val icon: ImageVector,
    val color: Color,
    val defaultBudget: Double
)

object ExpenseCategories {
    val list = listOf(
        CategoryInfo("Food & Dining", Icons.Default.LocalCafe, Color(0xFFF97316), 400.0),
        CategoryInfo("Groceries", Icons.Default.ShoppingBag, Color(0xFF10B981), 350.0),
        CategoryInfo("Housing & Rent", Icons.Default.Home, Color(0xFF6366F1), 1200.0),
        CategoryInfo("Utilities & Bills", Icons.Default.Receipt, Color(0xFF06B6D4), 200.0),
        CategoryInfo("Transportation", Icons.Default.DirectionsCar, Color(0xFF3B82F6), 180.0),
        CategoryInfo("Entertainment", Icons.Default.Movie, Color(0xFFEC4899), 150.0),
        CategoryInfo("Health & Fitness", Icons.Default.FitnessCenter, Color(0xFF14B8A6), 120.0),
        CategoryInfo("Education", Icons.Default.School, Color(0xFF8B5CF6), 100.0),
        CategoryInfo("Salary & Work", Icons.Default.Work, Color(0xFF22C55E), 0.0),
        CategoryInfo("Savings & Invest", Icons.Default.AccountBalance, Color(0xFFEAB308), 300.0)
    )

    fun getCategory(name: String): CategoryInfo {
        return list.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: CategoryInfo(name, Icons.Default.Receipt, Color(0xFF94A3B8), 100.0)
    }
}
