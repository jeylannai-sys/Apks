package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BudgetEntity
import com.example.data.ExpenseEntity
import com.example.model.AppCurrency
import java.util.Locale

data class SmartGoalSuggestion(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val targetBenefit: String,
    val icon: ImageVector,
    val accentColor: Color,
    val actionType: SmartGoalActionType,
    val defaultTargetValue: Double = 0.0
)

enum class SmartGoalActionType {
    SET_FOOD_BUDGET,
    SET_SAVINGS_TARGET,
    CREATE_HABIT_WATER,
    CREATE_HABIT_POMODORO,
    SET_SHOPPING_BUDGET
}

/**
 * Generates personalized, contextual smart goal suggestions based on active spending and budgets.
 */
fun generateSmartGoalSuggestions(
    transactions: List<ExpenseEntity>,
    budgets: List<BudgetEntity>,
    currency: AppCurrency,
    hideAmounts: Boolean = false
): List<SmartGoalSuggestion> {
    val suggestions = mutableListOf<SmartGoalSuggestion>()

    val totalExpenses = transactions.filter { it.isExpense }.sumOf { it.amount }
    val totalIncome = transactions.filter { !it.isExpense }.sumOf { it.amount }
    val foodExpenses = transactions.filter { it.isExpense && it.category.contains("Food", ignoreCase = true) }.sumOf { it.amount }
    val shoppingExpenses = transactions.filter { it.isExpense && it.category.contains("Shopping", ignoreCase = true) }.sumOf { it.amount }

    // 1. Food / Dining Optimization Goal
    val foodBudget = budgets.find { it.category.contains("Food", ignoreCase = true) }?.monthlyLimit ?: 0.0
    val suggestedFoodCap = if (foodExpenses > 0) (foodExpenses * 0.85).coerceAtLeast(150.0) else 350.0
    suggestions.add(
        SmartGoalSuggestion(
            id = "goal_food_cap",
            title = "Cap Dining & Food Spend",
            category = "Financial Discipline",
            description = "Trim monthly dining out by 15% to build discretionary savings without sacrificing nutrition.",
            targetBenefit = "Save approx ${currency.mask(currency.format(if (foodExpenses > 0) foodExpenses * 0.15 else 50.0), hideAmounts)}/month",
            icon = Icons.Default.Savings,
            accentColor = Color(0xFF10B981),
            actionType = SmartGoalActionType.SET_FOOD_BUDGET,
            defaultTargetValue = suggestedFoodCap
        )
    )

    // 2. 50/30/20 Wealth Builder Goal
    val targetSavings = if (totalIncome > 0) totalIncome * 0.20 else 500.0
    suggestions.add(
        SmartGoalSuggestion(
            id = "goal_50_30_20",
            title = "50/30/20 Wealth Builder",
            category = "Budgeting Framework",
            description = "Allocate 20% of net monthly income directly toward emergency cushion and index reserves.",
            targetBenefit = "Target Monthly Reserve: ${currency.mask(currency.format(targetSavings), hideAmounts)}",
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            accentColor = Color(0xFF3B82F6),
            actionType = SmartGoalActionType.SET_SAVINGS_TARGET,
            defaultTargetValue = targetSavings
        )
    )

    // 3. Shopping & Impulse Buffer Goal
    val shoppingCap = if (shoppingExpenses > 0) (shoppingExpenses * 0.8).coerceAtLeast(100.0) else 200.0
    suggestions.add(
        SmartGoalSuggestion(
            id = "goal_shopping_limit",
            title = "Smart Shopping Ceiling",
            category = "Impulse Control",
            description = "Enforce a 48-hour cooling rule on non-essential online retail to curb impulse buying.",
            targetBenefit = "Prevents ${currency.mask(currency.format(shoppingCap * 0.25), hideAmounts)} in impulsive cart checkouts",
            icon = Icons.Default.Shield,
            accentColor = Color(0xFF8B5CF6),
            actionType = SmartGoalActionType.SET_SHOPPING_BUDGET,
            defaultTargetValue = shoppingCap
        )
    )

    // 4. Daily Hydration & Energy Habit Goal
    suggestions.add(
        SmartGoalSuggestion(
            id = "goal_water_habit",
            title = "Daily 2.5L Hydration Habit",
            category = "Health & Cognition",
            description = "Boost mental sharpness and metabolism by sustaining a daily hydration streak.",
            targetBenefit = "7-Day Consistency Streak + 2500ml/day",
            icon = Icons.Default.ElectricBolt,
            accentColor = Color(0xFF0EA5E9),
            actionType = SmartGoalActionType.CREATE_HABIT_WATER
        )
    )

    // 5. Deep Work 4x Pomodoro Goal
    suggestions.add(
        SmartGoalSuggestion(
            id = "goal_pomodoro_habit",
            title = "Daily 4x Focus Blocks",
            category = "Peak Productivity",
            description = "Commit to four uninterrupted 25-minute Pomodoro sessions on top-priority tasks every day.",
            targetBenefit = "100 minutes of pure distraction-free flow",
            icon = Icons.Default.AutoAwesome,
            accentColor = Color(0xFFF97316),
            actionType = SmartGoalActionType.CREATE_HABIT_POMODORO
        )
    )

    return suggestions
}

/**
 * Interactive Smart Goal Suggestions Component with 1-tap adoption.
 */
@Composable
fun SmartGoalSuggestionsCard(
    transactions: List<ExpenseEntity>,
    budgets: List<BudgetEntity>,
    currency: AppCurrency,
    onAdoptGoal: (SmartGoalSuggestion) -> Unit,
    hideAmounts: Boolean = false,
    modifier: Modifier = Modifier
) {
    val suggestions = remember(transactions, budgets, currency, hideAmounts) {
        generateSmartGoalSuggestions(transactions, budgets, currency, hideAmounts)
    }

    var adoptedGoals by remember { mutableStateOf(setOf<String>()) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("smart_goal_suggestions_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Smart Goal Recommendations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Automated financial & productivity benchmarks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Suggestions List
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                suggestions.forEach { goal ->
                    val isAdopted = adoptedGoals.contains(goal.id)

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isAdopted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (isAdopted) goal.accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(goal.accentColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = goal.icon,
                                            contentDescription = null,
                                            tint = goal.accentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = goal.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = goal.category.uppercase(Locale.getDefault()),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = goal.accentColor,
                                            letterSpacing = 0.8.sp
                                        )
                                    }
                                }

                                if (isAdopted) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.2f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Adopted",
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "ADOPTED",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF10B981)
                                            )
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            adoptedGoals = adoptedGoals + goal.id
                                            onAdoptGoal(goal)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = goal.accentColor),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text(text = "Adopt Goal", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = goal.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "💡 ${goal.targetBenefit}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
