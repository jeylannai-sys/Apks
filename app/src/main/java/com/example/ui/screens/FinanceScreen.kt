package com.example.ui.screens

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.BudgetEntity
import com.example.data.ExpenseEntity
import com.example.model.AppCurrency
import com.example.model.ExpenseCategories
import com.example.service.BudgetThresholdAlert
import com.example.service.BudgetThresholdState
import com.example.service.ExtractedReceipt
import com.example.service.ReceiptScanUiState
import com.example.service.ReceiptScannerService
import kotlin.math.roundToInt
import com.example.ui.components.BudgetTrendChartsCard
import com.example.ui.components.QuickCalculatorCard
import com.example.ui.components.SmartGoalSuggestion
import com.example.ui.components.SmartGoalSuggestionsCard
import com.example.util.FinancialExportHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(
    transactions: List<ExpenseEntity>,
    budgets: List<BudgetEntity>,
    currency: AppCurrency,
    hideAmounts: Boolean = false,
    onToggleHideAmounts: () -> Unit = {},
    receiptScanState: ReceiptScanUiState = ReceiptScanUiState.Idle,
    onScanReceipt: (Bitmap) -> Unit = {},
    onResetReceiptScan: () -> Unit = {},
    onCurrencyChanged: (AppCurrency) -> Unit,
    onSaveExpense: (ExpenseEntity) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit,
    onSetBudget: (String, Double) -> Unit,
    onAdoptGoal: (SmartGoalSuggestion) -> Unit = {},
    budgetThresholdState: BudgetThresholdState = BudgetThresholdState(),
    activeBudgetAlerts: List<BudgetThresholdAlert> = emptyList(),
    onToggleBudgetAlerts: (Boolean) -> Unit = {},
    onSetDefaultBudgetThreshold: (Int) -> Unit = {},
    onSetCategoryBudgetThreshold: (String, Int?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<ExpenseEntity?>(null) }
    var showReceiptScannerSheet by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showThresholdSettingsDialog by remember { mutableStateOf(false) }
    var prefilledAmount by remember { mutableStateOf<Double?>(null) }
    var prefilledTitle by remember { mutableStateOf<String?>(null) }
    var prefilledCategory by remember { mutableStateOf<String?>(null) }
    var prefilledDateMillis by remember { mutableStateOf<Long?>(null) }
    var prefilledNote by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf("All") }
    var editingBudgetCategory by remember { mutableStateOf<String?>(null) }
    var newBudgetLimitText by remember { mutableStateOf("") }
    var editingCategoryThreshold by remember { mutableStateOf<Int?>(null) }

    val monthlySummary = remember(transactions, budgets, selectedFilter) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val startOfMonth = cal.timeInMillis

        val thisMonth = transactions.filter { it.dateMillis >= startOfMonth }
        val income = thisMonth.filter { !it.isExpense }.sumOf { it.amount }
        val expense = thisMonth.filter { it.isExpense }.sumOf { it.amount }
        val filtered = transactions.filter {
            when (selectedFilter) {
                "Expenses" -> it.isExpense
                "Income" -> !it.isExpense
                else -> true
            }
        }
        val bMap = budgets.associate { it.category to it.monthlyLimit }
        val spentMap = thisMonth.filter { it.isExpense }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
        Triple(Triple(income, expense, income - expense), filtered, Pair(bMap, spentMap))
    }

    val totalIncome = monthlySummary.first.first
    val totalExpense = monthlySummary.first.second
    val netSavings = monthlySummary.first.third
    val filteredTransactions = monthlySummary.second
    val budgetMap = monthlySummary.third.first
    val spentByCategory = monthlySummary.third.second

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTransaction = null
                    prefilledAmount = null
                    prefilledTitle = null
                    prefilledCategory = null
                    prefilledDateMillis = null
                    prefilledNote = null
                    showAddDialog = true
                },
                modifier = Modifier.testTag("add_transaction_fab"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Transaction")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Overview Balance Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Net Cash Flow (This Month)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Show / Hide Income & Expenses Privacy Toggle
                                IconButton(
                                    onClick = onToggleHideAmounts,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("toggle_finance_privacy_button")
                                ) {
                                    Icon(
                                        imageVector = if (hideAmounts) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (hideAmounts) "Show Income & Expenses" else "Hide Income & Expenses",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Currency Switcher Chip
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .clickable { showCurrencyDialog = true }
                                        .testTag("currency_selector_chip")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${currency.flag} ${currency.code} (${currency.symbol})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Change Currency",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = currency.mask(currency.format(netSavings), hideAmounts),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (netSavings >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("finance_net_cash_flow_text")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Income / Expense Dual Pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Income Pill
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(text = "Income", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = currency.mask(currency.formatCompact(totalIncome), hideAmounts),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF10B981),
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.testTag("finance_income_total_text")
                                        )
                                    }
                                }
                            }

                            // Expense Pill
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF43F5E).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = null,
                                            tint = Color(0xFFF43F5E),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(text = "Expenses", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = currency.mask(currency.formatCompact(totalExpense), hideAmounts),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFFF43F5E),
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.testTag("finance_expense_total_text")
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Action Row with Camera Receipt Scanner & Export Statement
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    onResetReceiptScan()
                                    showReceiptScannerSheet = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("scan_receipt_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan Receipt", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = { showExportDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("export_statement_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Interactive Budget Trend & Spending Velocity Charts
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    BudgetTrendChartsCard(
                        transactions = transactions,
                        budgets = budgets,
                        currency = currency,
                        hideAmounts = hideAmounts
                    )
                }
            }

            // Smart Goal Recommendations with 1-Tap Adoption
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SmartGoalSuggestionsCard(
                        transactions = transactions,
                        budgets = budgets,
                        currency = currency,
                        hideAmounts = hideAmounts,
                        onAdoptGoal = onAdoptGoal
                    )
                }
            }

            // Quick Calculator embedded within the same dashboard
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    QuickCalculatorCard(
                        currency = currency,
                        onApplyToExpense = { calculatedAmount ->
                            prefilledAmount = calculatedAmount
                            showAddDialog = true
                        }
                    )
                }
            }

            // Category Budgets Tracking & Budget Threshold Alerts Section
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val totalCategoryBudget = budgets.sumOf { it.monthlyLimit.coerceAtLeast(0.0) }
                        Column {
                            Text(
                                text = "Category Budgets",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Total monthly budget: ${currency.mask(currency.formatCompact(totalCategoryBudget), hideAmounts)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("category_budgets_total_label")
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (budgetThresholdState.alertsEnabled) {
                                Color(0xFFF59E0B).copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .clickable { showThresholdSettingsDialog = true }
                                .testTag("budget_threshold_settings_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Budget Threshold Settings",
                                    tint = if (budgetThresholdState.alertsEnabled) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (budgetThresholdState.alertsEnabled) {
                                        "Alert @ ${budgetThresholdState.defaultThresholdPercent}%"
                                    } else {
                                        "Alerts Off"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (budgetThresholdState.alertsEnabled) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (budgetThresholdState.alertsEnabled && activeBudgetAlerts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("budget_threshold_alerts_banner"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${activeBudgetAlerts.size} Budget Threshold Alert(s) Active",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                activeBudgetAlerts.take(3).forEach { alert ->
                                    Text(
                                        text = "• ${alert.category}: ${alert.percentUsed.toInt()}% used (${currency.mask(currency.formatCompact(alert.spent), hideAmounts)} / ${currency.mask(currency.formatCompact(alert.monthlyLimit), hideAmounts)} • ≥${alert.thresholdPercent}% alert)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    ExpenseCategories.list.filter { it.name != "Salary & Work" }.forEach { catInfo ->
                        val limit = (budgetMap[catInfo.name] ?: 0.0).coerceAtLeast(0.0)
                        val spent = spentByCategory[catInfo.name] ?: 0.0
                        val catThreshold = budgetThresholdState.thresholdFor(catInfo.name)
                        val progress = when {
                            limit > 0.0 -> (spent / limit).toFloat().coerceIn(0f, 1f)
                            spent > 0.0 -> 1f
                            else -> 0f
                        }
                        val isOverBudget = spent > limit
                        val isThresholdCrossed = limit > 0.0 && spent >= limit * (catThreshold / 100.0) - 0.001 && !isOverBudget
                        val usedPct = if (limit > 0.0) ((spent / limit) * 100.0).toInt() else 0

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(catInfo.color.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(imageVector = catInfo.icon, contentDescription = null, tint = catInfo.color, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(text = catInfo.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            Text(
                                                text = if (limit <= 0.0) {
                                                    "No budget set • Alert ≥$catThreshold%"
                                                } else {
                                                    "Alert ≥$catThreshold% • $usedPct% used"
                                                },
                                                fontSize = 10.sp,
                                                color = when {
                                                    isOverBudget -> MaterialTheme.colorScheme.error
                                                    isThresholdCrossed -> Color(0xFFF59E0B)
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clickable {
                                                editingBudgetCategory = catInfo.name
                                                newBudgetLimitText = if (limit % 1.0 == 0.0) {
                                                    limit.toInt().toString()
                                                } else {
                                                    limit.toString()
                                                }
                                                editingCategoryThreshold = budgetThresholdState.categoryThresholds[catInfo.name]
                                            }
                                            .testTag("edit_budget_${catInfo.name}")
                                    ) {
                                        Text(
                                            text = "${currency.mask(currency.formatCompact(spent), hideAmounts)} / ${currency.mask(currency.formatCompact(limit), hideAmounts)}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = when {
                                                isOverBudget -> MaterialTheme.colorScheme.error
                                                isThresholdCrossed -> Color(0xFFF59E0B)
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Budget", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = when {
                                        isOverBudget -> MaterialTheme.colorScheme.error
                                        isThresholdCrossed -> Color(0xFFF59E0B)
                                        else -> catInfo.color
                                    },
                                    trackColor = MaterialTheme.colorScheme.surface,
                                    strokeCap = StrokeCap.Round
                                )

                                if (isOverBudget) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (limit > 0.0) {
                                            "Over budget by ${currency.mask(currency.format(spent - limit), hideAmounts)} ($usedPct% used)"
                                        } else {
                                            "Over ${currency.mask(currency.formatCompact(0.0), hideAmounts)} budget by ${currency.mask(currency.format(spent), hideAmounts)}"
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else if (isThresholdCrossed && budgetThresholdState.alertsEnabled) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Threshold reached ($usedPct% ≥ $catThreshold% alert level)",
                                        fontSize = 11.sp,
                                        color = Color(0xFFF59E0B),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Recent Transactions Section
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Recent Transactions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Hold any transaction to rename or re-edit",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("All", "Expenses", "Income").forEach { filter ->
                                FilterChip(
                                    selected = selectedFilter == filter,
                                    onClick = { selectedFilter = filter },
                                    label = { Text(filter, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            }

            items(filteredTransactions, key = { it.id }) { item ->
                TransactionRowItem(
                    transaction = item,
                    currency = currency,
                    hideAmounts = hideAmounts,
                    onEdit = {
                        editingTransaction = item
                        prefilledAmount = null
                        prefilledTitle = null
                        prefilledCategory = null
                        prefilledDateMillis = null
                        prefilledNote = null
                        showAddDialog = true
                    },
                    onDelete = { onDeleteExpense(item) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        }
    }

    // Financial CSV & Statement Export Dialog
    if (showExportDialog) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Statement", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose your export format to download, view, or share your financial records:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExportDialog = false
                                val file = FinancialExportHelper.exportTransactionsCsv(context, transactions, currency)
                                if (file != null) {
                                    FinancialExportHelper.shareCsvFile(context, file)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("CSV Spreadsheet (.csv)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Open in Excel, Google Sheets, or Numbers", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExportDialog = false
                                val summary = FinancialExportHelper.generateStatementSummary(transactions, currency)
                                FinancialExportHelper.shareStatementText(context, summary)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF38BDF8).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Formatted Statement Summary", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Share text report with totals & category breakdown", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Currency Switcher Modal Dialog
    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = {
                Text(
                    text = "Select Currency",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(AppCurrency.entries) { curr ->
                        val isSelected = currency == curr
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable {
                                    onCurrencyChanged(curr)
                                    showCurrencyDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = curr.flag, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "${curr.displayName} (${curr.code})",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Symbol: ${curr.symbol}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Budget Threshold Alert Global Settings Dialog
    if (showThresholdSettingsDialog) {
        var sliderPercent by remember(budgetThresholdState.defaultThresholdPercent) {
            mutableFloatStateOf(budgetThresholdState.defaultThresholdPercent.toFloat())
        }
        AlertDialog(
            onDismissRequest = { showThresholdSettingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFF59E0B))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Budget Threshold Alerts", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Trigger Alert Notifications", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                text = "Notify when category spending crosses threshold % of monthly limit",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = budgetThresholdState.alertsEnabled,
                            onCheckedChange = onToggleBudgetAlerts,
                            modifier = Modifier.testTag("budget_alerts_enabled_switch")
                        )
                    }

                    Text(
                        text = "Default Alert Threshold: ${sliderPercent.roundToInt()}%",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(50, 75, 80, 90, 95)) { preset ->
                            FilterChip(
                                selected = sliderPercent.roundToInt() == preset,
                                onClick = { sliderPercent = preset.toFloat() },
                                label = { Text("$preset%", fontSize = 11.sp) }
                            )
                        }
                    }

                    Slider(
                        value = sliderPercent,
                        onValueChange = { sliderPercent = it },
                        valueRange = 20f..100f,
                        modifier = Modifier.testTag("budget_threshold_slider")
                    )

                    Text(
                        text = "You can also set a custom threshold % per category by tapping any category budget.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSetDefaultBudgetThreshold(sliderPercent.roundToInt())
                    showThresholdSettingsDialog = false
                }) {
                    Text("Save Threshold")
                }
            },
            dismissButton = {
                TextButton(onClick = { showThresholdSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Budget Limit & Category Threshold Dialog
    if (editingBudgetCategory != null) {
        val targetCat = editingBudgetCategory!!
        var budgetInputError by remember(targetCat) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { editingBudgetCategory = null },
            title = { Text("Budget & Alert: $targetCat") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newBudgetLimitText,
                        onValueChange = {
                            newBudgetLimitText = it
                            budgetInputError = null
                        },
                        label = { Text("Monthly Budget (${currency.symbol})") },
                        placeholder = { Text("0") },
                        prefix = { Text("${currency.symbol} ") },
                        singleLine = true,
                        isError = budgetInputError != null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_budget_limit_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Set 0 to clear this category's budget",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                newBudgetLimitText = "0"
                                budgetInputError = null
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("reset_budget_zero_button")
                        ) {
                            Text("Set to 0", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (budgetInputError != null) {
                        Text(
                            text = budgetInputError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "Category Alert Threshold:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = editingCategoryThreshold == null,
                                onClick = { editingCategoryThreshold = null },
                                label = {
                                    Text("Default (${budgetThresholdState.defaultThresholdPercent}%)", fontSize = 11.sp)
                                }
                            )
                        }
                        items(listOf(50, 75, 80, 90, 100)) { pct ->
                            FilterChip(
                                selected = editingCategoryThreshold == pct,
                                onClick = { editingCategoryThreshold = pct },
                                label = { Text("$pct%", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newBudgetLimitText.trim()
                        val newLimit = if (trimmed.isEmpty()) 0.0 else trimmed.toDoubleOrNull()
                        if (newLimit == null || newLimit < 0.0) {
                            budgetInputError = "Enter a valid budget amount (0 or higher)."
                            return@Button
                        }
                        onSetBudget(targetCat, newLimit)
                        onSetCategoryBudgetThreshold(targetCat, editingCategoryThreshold)
                        editingBudgetCategory = null
                    },
                    modifier = Modifier.testTag("save_budget_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingBudgetCategory = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add / Edit Transaction Bottom Sheet
    if (showAddDialog) {
        AddTransactionBottomSheet(
            currency = currency,
            editingTransaction = editingTransaction,
            prefilledAmount = prefilledAmount,
            prefilledTitle = prefilledTitle,
            prefilledCategory = prefilledCategory,
            prefilledDateMillis = prefilledDateMillis,
            prefilledNote = prefilledNote,
            onLaunchReceiptScanner = {
                showAddDialog = false
                editingTransaction = null
                onResetReceiptScan()
                showReceiptScannerSheet = true
            },
            onDismiss = {
                showAddDialog = false
                editingTransaction = null
            },
            onSave = { saved ->
                val wasEditing = editingTransaction != null
                onSaveExpense(saved)
                showAddDialog = false
                editingTransaction = null
                if (wasEditing) {
                    Toast.makeText(context, "Transaction updated!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Camera Receipt Scanner Auto-Extract Bottom Sheet
    if (showReceiptScannerSheet) {
        ReceiptScannerBottomSheet(
            currency = currency,
            scanState = receiptScanState,
            onScanReceipt = onScanReceipt,
            onEditExtracted = { extracted ->
                prefilledTitle = extracted.merchant
                prefilledAmount = extracted.totalAmount
                prefilledCategory = extracted.category
                prefilledDateMillis = ReceiptScannerService.parseDateIsoToMillis(extracted.dateIso)
                prefilledNote = extracted.notes
                showReceiptScannerSheet = false
                showAddDialog = true
            },
            onSaveExtracted = { extracted ->
                onSaveExpense(
                    ExpenseEntity(
                        title = extracted.merchant,
                        amount = extracted.totalAmount,
                        category = extracted.category,
                        dateMillis = ReceiptScannerService.parseDateIsoToMillis(extracted.dateIso),
                        isExpense = true,
                        note = extracted.notes
                    )
                )
                showReceiptScannerSheet = false
                Toast.makeText(context, "Receipt expense saved!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showReceiptScannerSheet = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRowItem(
    transaction: ExpenseEntity,
    currency: AppCurrency,
    hideAmounts: Boolean = false,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val catInfo = ExpenseCategories.getCategory(transaction.category)
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onEdit,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEdit()
                },
                onLongClickLabel = "Rename or edit transaction"
            )
            .testTag("transaction_item_${transaction.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(catInfo.color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = catInfo.icon, contentDescription = null, tint = catInfo.color, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${transaction.category} • ${dateFormat.format(transaction.dateMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = currency.mask((if (transaction.isExpense) "-" else "+") + currency.format(transaction.amount), hideAmounts),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = if (transaction.isExpense) Color(0xFFF43F5E) else Color(0xFF10B981)
                )

                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScannerBottomSheet(
    currency: AppCurrency,
    scanState: ReceiptScanUiState,
    onScanReceipt: (Bitmap) -> Unit,
    onEditExtracted: (ExtractedReceipt) -> Unit,
    onSaveExtracted: (ExtractedReceipt) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }

    suspend fun decodeUriBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > 1600 || bounds.outHeight / sampleSize > 1600) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (_: Exception) {
            null
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCaptureFile
        if (success && file != null && file.exists()) {
            scope.launch {
                val uri = Uri.fromFile(file)
                val bmp = decodeUriBitmap(uri)
                if (bmp != null) {
                    previewBitmap = bmp
                    onScanReceipt(bmp)
                } else {
                    Toast.makeText(context, "Could not read captured receipt photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = decodeUriBitmap(uri)
                if (bmp != null) {
                    previewBitmap = bmp
                    onScanReceipt(bmp)
                } else {
                    Toast.makeText(context, "Unable to decode selected receipt image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun launchCameraCapture() {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val captureFile = File(exportDir, "receipt_capture_${System.currentTimeMillis()}.jpg")
        pendingCaptureFile = captureFile
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", captureFile)
        takePictureLauncher.launch(uri)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Camera Receipt Scanner",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Text(
                text = "Snap a photo or choose a receipt image. Merchant, grand total, date, and expense category are extracted automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = "Receipt Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("No receipt photo selected yet", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { launchCameraCapture() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("receipt_take_photo_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Camera", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("receipt_pick_photo_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gallery", fontWeight = FontWeight.Bold)
                }
            }

            when (scanState) {
                is ReceiptScanUiState.Idle -> Unit
                is ReceiptScanUiState.Scanning -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Auto-extracting merchant, total & category...",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                is ReceiptScanUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Extraction Failed",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = scanState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp
                            )
                            if (previewBitmap != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = { onScanReceipt(previewBitmap!!) }) {
                                    Text("Retry Extraction")
                                }
                            }
                        }
                    }
                }
                is ReceiptScanUiState.Success -> {
                    val receipt = scanState.receipt
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("receipt_extracted_result_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Extracted Receipt Details",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp
                            )
                            Text("Merchant: ${receipt.merchant}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                text = "Total: ${currency.format(receipt.totalAmount)}",
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 18.sp,
                                color = Color(0xFFF43F5E)
                            )
                            Text("Category: ${receipt.category}", fontSize = 12.sp)
                            if (receipt.dateIso.isNotBlank()) {
                                Text("Date: ${receipt.dateIso}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (receipt.notes.isNotBlank()) {
                                Text("Items: ${receipt.notes}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onEditExtracted(receipt) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("receipt_edit_extracted_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Edit Entry", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onSaveExtracted(receipt) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("receipt_save_extracted_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save Expense", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionBottomSheet(
    currency: AppCurrency,
    editingTransaction: ExpenseEntity? = null,
    prefilledAmount: Double?,
    prefilledTitle: String? = null,
    prefilledCategory: String? = null,
    prefilledDateMillis: Long? = null,
    prefilledNote: String? = null,
    onLaunchReceiptScanner: () -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (ExpenseEntity) -> Unit
) {
    val context = LocalContext.current
    val initialTitle = editingTransaction?.title ?: prefilledTitle ?: ""
    val initialAmount = editingTransaction?.amount ?: prefilledAmount
    val initialCategory = editingTransaction?.category ?: prefilledCategory ?: "Food & Dining"
    val initialDateMillis = editingTransaction?.dateMillis ?: prefilledDateMillis
    val initialNote = editingTransaction?.note ?: prefilledNote ?: ""

    var title by remember(editingTransaction?.id, prefilledTitle) { mutableStateOf(initialTitle) }
    var amountText by remember(editingTransaction?.id, prefilledAmount) {
        mutableStateOf(
            if (initialAmount != null && initialAmount > 0) String.format(Locale.US, "%.2f", initialAmount) else ""
        )
    }
    var isExpense by remember(editingTransaction?.id) {
        mutableStateOf(editingTransaction?.isExpense ?: true)
    }
    var selectedCategory by remember(editingTransaction?.id, prefilledCategory) {
        mutableStateOf(initialCategory)
    }
    var note by remember(editingTransaction?.id, prefilledNote) { mutableStateOf(initialNote) }

    val cal = remember(editingTransaction?.id, initialDateMillis) {
        Calendar.getInstance().apply {
            if (initialDateMillis != null) timeInMillis = initialDateMillis
        }
    }
    var dateMillis by remember(editingTransaction?.id, initialDateMillis) {
        mutableStateOf(cal.timeInMillis)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        editingTransaction != null && isExpense -> "Edit / Rename Expense"
                        editingTransaction != null && !isExpense -> "Edit / Rename Income"
                        isExpense -> "Add Expense"
                        else -> "Add Income"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            if (editingTransaction == null) {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onLaunchReceiptScanner,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("sheet_scan_receipt_button")
                ) {
                    Icon(imageVector = Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto-Fill from Camera Receipt Scanner", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Income / Expense Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        isExpense = true
                        if (selectedCategory == "Salary & Work") {
                            selectedCategory = "Food & Dining"
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isExpense) Color(0xFFF43F5E) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isExpense) Color.White else MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Expense")
                }

                Button(
                    onClick = {
                        isExpense = false
                        selectedCategory = "Salary & Work"
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isExpense) Color(0xFF10B981) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (!isExpense) Color.White else MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Income")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount (${currency.code}) *") },
                placeholder = { Text("0.00") },
                prefix = { Text("${currency.symbol} ") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transaction_amount_input"),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Description / Merchant *") },
                placeholder = { Text("e.g. Supermarket, Gas station") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transaction_title_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                placeholder = { Text("Optional memo or details") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transaction_note_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(text = "Category", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ExpenseCategories.list) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat.name,
                        onClick = { selectedCategory = cat.name },
                        label = { Text(cat.name) },
                        leadingIcon = {
                            Icon(imageVector = cat.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Date Picker button
            val dateFormat = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())
            OutlinedButton(
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            cal.set(Calendar.YEAR, y)
                            cal.set(Calendar.MONTH, m)
                            cal.set(Calendar.DAY_OF_MONTH, d)
                            dateMillis = cal.timeInMillis
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = dateFormat.format(cal.time))
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount != null && amount > 0 && title.isNotBlank()) {
                        onSave(
                            ExpenseEntity(
                                id = editingTransaction?.id ?: 0L,
                                title = title.trim(),
                                amount = amount,
                                category = selectedCategory,
                                dateMillis = dateMillis,
                                isExpense = isExpense,
                                note = note.trim()
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_transaction_button"),
                shape = RoundedCornerShape(14.dp),
                enabled = title.isNotBlank() && (amountText.toDoubleOrNull() ?: 0.0) > 0.0
            ) {
                Text(
                    text = if (editingTransaction != null) "Save Changes" else "Save Entry",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
