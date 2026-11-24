package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BudgetEntity
import com.example.data.ExpenseEntity
import com.example.model.AppCurrency
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class TrendChartMode(val label: String) {
    DAILY_BARS("Daily Expenses"),
    CUMULATIVE_VELOCITY("Spending Velocity"),
    CATEGORY_ALLOCATION("Categories")
}

data class ProjectedMonthEndSummary(
    val totalBudgetLimit: Double,
    val hasBudget: Boolean,
    val dailyAvgSpent: Double,
    val projectedTotal: Double,
    val projectedDelta: Double
)

fun computeProjectedMonthEndSummary(
    budgets: List<BudgetEntity>,
    totalSpentSoFar: Double,
    currentDayOfMonth: Int,
    daysInMonth: Int
): ProjectedMonthEndSummary {
    val totalBudgetLimit = budgets.sumOf { it.monthlyLimit.coerceAtLeast(0.0) }
    val hasBudget = totalBudgetLimit > 0.0
    val dailyAvgSpent = if (currentDayOfMonth > 0) totalSpentSoFar / currentDayOfMonth else 0.0
    val projectedTotal = dailyAvgSpent * daysInMonth
    val projectedDelta = projectedTotal - totalBudgetLimit
    return ProjectedMonthEndSummary(
        totalBudgetLimit = totalBudgetLimit,
        hasBudget = hasBudget,
        dailyAvgSpent = dailyAvgSpent,
        projectedTotal = projectedTotal,
        projectedDelta = projectedDelta
    )
}

/**
 * Interactive Budget Trend Charts component providing spending velocity, daily bars, and category breakdown.
 */
@Composable
fun BudgetTrendChartsCard(
    transactions: List<ExpenseEntity>,
    budgets: List<BudgetEntity>,
    currency: AppCurrency,
    hideAmounts: Boolean = false,
    modifier: Modifier = Modifier
) {
    var selectedMode by remember { mutableStateOf(TrendChartMode.DAILY_BARS) }
    val calendar = remember { Calendar.getInstance() }
    val daysInMonth = remember { calendar.getActualMaximum(Calendar.DAY_OF_MONTH) }
    val currentDayOfMonth = remember { calendar.get(Calendar.DAY_OF_MONTH) }

    // Filter to current month expenses
    val currentMonthExpenses = remember(transactions) {
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)
        transactions.filter {
            it.isExpense && run {
                val tCal = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
                tCal.get(Calendar.MONTH) == currentMonth && tCal.get(Calendar.YEAR) == currentYear
            }
        }
    }

    val totalSpentSoFar = remember(currentMonthExpenses) {
        currentMonthExpenses.sumOf { it.amount }
    }

    val summary = remember(budgets, totalSpentSoFar, currentDayOfMonth, daysInMonth) {
        computeProjectedMonthEndSummary(
            budgets = budgets,
            totalSpentSoFar = totalSpentSoFar,
            currentDayOfMonth = currentDayOfMonth,
            daysInMonth = daysInMonth
        )
    }
    val totalBudgetLimit = summary.totalBudgetLimit
    val expectedSpentByNow = if (summary.hasBudget && daysInMonth > 0) {
        (totalBudgetLimit / daysInMonth) * currentDayOfMonth
    } else {
        0.0
    }
    val isAheadOfPace = if (summary.hasBudget) totalSpentSoFar > expectedSpentByNow else totalSpentSoFar > 0.0
    val projectedTotal = summary.projectedTotal
    val projectedDelta = summary.projectedDelta

    // Past 7 Days data points
    val dailyData = remember(transactions) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dayLabelFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val c = Calendar.getInstance()
        val list = mutableListOf<Triple<String, String, Double>>() // dateStr, label, amount

        for (i in 6 downTo 0) {
            val checkCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            val dStr = dateFormat.format(checkCal.time)
            val lbl = dayLabelFormat.format(checkCal.time)
            val daySum = transactions.filter {
                it.isExpense && dateFormat.format(Date(it.dateMillis)) == dStr
            }.sumOf { it.amount }
            list.add(Triple(dStr, lbl, daySum))
        }
        list
    }

    // Cumulative month velocity points (days 1..currentDayOfMonth)
    val cumulativeVelocity = remember(currentMonthExpenses, currentDayOfMonth) {
        val list = mutableListOf<Pair<Int, Double>>()
        var runningTotal = 0.0
        val cal = Calendar.getInstance()

        for (day in 1..currentDayOfMonth) {
            val daySum = currentMonthExpenses.filter {
                cal.timeInMillis = it.dateMillis
                cal.get(Calendar.DAY_OF_MONTH) == day
            }.sumOf { it.amount }
            runningTotal += daySum
            list.add(Pair(day, runningTotal))
        }
        list
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("budget_trend_charts_card"),
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
                            .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueryStats,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Budget Trend & Velocity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                !summary.hasBudget && totalSpentSoFar <= 0.0 -> "Pace: No Monthly Budget Set"
                                !summary.hasBudget -> "Pace: Spending Above ${currency.format(0.0)} Budget"
                                isAheadOfPace -> "Pace: High Spend Velocity"
                                else -> "Pace: On Track Under Budget"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                !summary.hasBudget && totalSpentSoFar <= 0.0 -> MaterialTheme.colorScheme.onSurfaceVariant
                                isAheadOfPace -> Color(0xFFF97316)
                                else -> Color(0xFF10B981)
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Chart Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TrendChartMode.values().forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { selectedMode = mode },
                        label = { Text(mode.label, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (selectedMode) {
                TrendChartMode.DAILY_BARS -> {
                    DailyBarsChart(
                        dailyData = dailyData,
                        currency = currency,
                        hideAmounts = hideAmounts
                    )
                }
                TrendChartMode.CUMULATIVE_VELOCITY -> {
                    CumulativeVelocityChart(
                        cumulativeVelocity = cumulativeVelocity,
                        daysInMonth = daysInMonth,
                        totalBudgetLimit = totalBudgetLimit,
                        currency = currency,
                        hideAmounts = hideAmounts
                    )
                }
                TrendChartMode.CATEGORY_ALLOCATION -> {
                    CategoryAllocationBreakdown(
                        currentMonthExpenses = currentMonthExpenses,
                        budgets = budgets,
                        currency = currency,
                        hideAmounts = hideAmounts
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Projected Month-End Burn Rate Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PROJECTED MONTH-END SPEND",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currency.mask(currency.format(projectedTotal), hideAmounts),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "vs ${currency.mask(currency.format(totalBudgetLimit), hideAmounts)} total budget",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val showNeutralNoBudget = !summary.hasBudget && projectedTotal <= 0.0
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            showNeutralNoBudget -> MaterialTheme.colorScheme.surfaceVariant
                            projectedDelta > 0 -> Color(0xFFEF4444).copy(alpha = 0.15f)
                            else -> Color(0xFF10B981).copy(alpha = 0.15f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!showNeutralNoBudget) {
                                Icon(
                                    imageVector = if (projectedDelta > 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = null,
                                    tint = if (projectedDelta > 0) Color(0xFFEF4444) else Color(0xFF10B981),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = when {
                                    showNeutralNoBudget -> "No budget set"
                                    hideAmounts && projectedDelta > 0 -> "+${currency.maskedOnly()} over"
                                    hideAmounts -> "${currency.maskedOnly()} saved"
                                    projectedDelta > 0 -> "+${currency.format(projectedDelta)} over"
                                    else -> "${currency.format(-projectedDelta)} saved"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    showNeutralNoBudget -> MaterialTheme.colorScheme.onSurfaceVariant
                                    projectedDelta > 0 -> Color(0xFFEF4444)
                                    else -> Color(0xFF10B981)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 7-Day Expense Bar Chart with animated bars and tooltips.
 */
@Composable
private fun DailyBarsChart(
    dailyData: List<Triple<String, String, Double>>,
    currency: AppCurrency,
    hideAmounts: Boolean = false
) {
    val maxDaily = remember(dailyData) {
        val m = dailyData.maxOfOrNull { it.third } ?: 100.0
        if (m > 0) m * 1.25 else 100.0
    }

    var selectedIndex by remember { mutableIntStateOf(-1) }

    Column {
        if (selectedIndex in dailyData.indices) {
            val item = dailyData[selectedIndex]
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = "${item.second} (${item.first}): ${currency.mask(currency.format(item.third), hideAmounts)} spent",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
                val barCount = dailyData.size
                if (barCount == 0) return@Canvas

                val slotWidth = size.width / barCount
                val barWidth = slotWidth * 0.45f

                // Draw baseline grid line
                drawLine(
                    color = Color(0xFF334155).copy(alpha = 0.4f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )

                dailyData.forEachIndexed { i, triple ->
                    val amount = triple.third
                    val barHeight = ((amount / maxDaily) * size.height).toFloat().coerceIn(4.dp.toPx(), size.height)
                    val left = i * slotWidth + (slotWidth - barWidth) / 2f
                    val top = size.height - barHeight

                    val isSelected = selectedIndex == i
                    val barColor = if (isSelected) Color(0xFF38BDF8)
                    else if (amount > 0) Color(0xFF10B981)
                    else Color(0xFF334155).copy(alpha = 0.3f)

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
        }

        // Day Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            dailyData.forEachIndexed { index, triple ->
                Text(
                    text = triple.second,
                    fontSize = 10.sp,
                    fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { selectedIndex = index }
                )
            }
        }
    }
}

/**
 * Cumulative Spending Velocity Chart comparing actual spending against linear target pace.
 */
@Composable
private fun CumulativeVelocityChart(
    cumulativeVelocity: List<Pair<Int, Double>>,
    daysInMonth: Int,
    totalBudgetLimit: Double,
    currency: AppCurrency,
    hideAmounts: Boolean = false
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(115.dp)) {
                if (cumulativeVelocity.isEmpty() || daysInMonth == 0) return@Canvas

                val maxVal = maxOf(totalBudgetLimit * 1.15, (cumulativeVelocity.lastOrNull()?.second ?: 0.0) * 1.15)
                if (maxVal <= 0) return@Canvas

                // 1. Draw Target Budget Reference Line (Linear Pace)
                val pacePath = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(size.width, (size.height - (totalBudgetLimit / maxVal * size.height)).toFloat())
                }
                drawPath(
                    path = pacePath,
                    color = Color(0xFF64748B).copy(alpha = 0.6f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                )

                // 2. Draw Actual Cumulative Spending Curve
                val actualPath = Path()
                cumulativeVelocity.forEachIndexed { index, (day, cumulativeSpent) ->
                    val x = (day.toFloat() / daysInMonth.toFloat()) * size.width
                    val y = (size.height - (cumulativeSpent / maxVal * size.height)).toFloat()
                    if (index == 0) actualPath.moveTo(x, y) else actualPath.lineTo(x, y)
                }

                drawPath(
                    path = actualPath,
                    color = Color(0xFF10B981),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw current point pulse
                val lastPoint = cumulativeVelocity.lastOrNull()
                if (lastPoint != null) {
                    val px = (lastPoint.first.toFloat() / daysInMonth.toFloat()) * size.width
                    val py = (size.height - (lastPoint.second / maxVal * size.height)).toFloat()
                    drawCircle(color = Color(0xFF10B981), radius = 5.dp.toPx(), center = Offset(px, py))
                    drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(px, py))
                }
            }
        }

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Actual Spend", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(14.dp).height(2.dp).background(Color(0xFF64748B)))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Planned Pace (${currency.mask(currency.format(totalBudgetLimit), hideAmounts)})",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Category Allocation Breakdown list with visual progress bars.
 */
@Composable
private fun CategoryAllocationBreakdown(
    currentMonthExpenses: List<ExpenseEntity>,
    budgets: List<BudgetEntity>,
    currency: AppCurrency,
    hideAmounts: Boolean = false
) {
    val totalExpense = remember(currentMonthExpenses) { currentMonthExpenses.sumOf { it.amount } }
    val categoryTotals = remember(currentMonthExpenses) {
        currentMonthExpenses.groupBy { it.category }
            .mapValues { it.value.sumOf { tx -> tx.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    val categoryColors = listOf(
        Color(0xFF10B981),
        Color(0xFF3B82F6),
        Color(0xFFF97316),
        Color(0xFF8B5CF6),
        Color(0xFFEC4899),
        Color(0xFF0EA5E9)
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (categoryTotals.isEmpty()) {
            Text(
                text = "No expenses recorded this month yet.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            categoryTotals.take(4).forEachIndexed { index, (cat, amount) ->
                val pct = if (totalExpense > 0) (amount / totalExpense).toFloat() else 0f
                val color = categoryColors.getOrElse(index) { Color(0xFF10B981) }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = cat, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = "${currency.mask(currency.format(amount), hideAmounts)} (${(pct * 100).toInt()}%)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(pct)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(color)
                        )
                    }
                }
            }
        }
    }
}
