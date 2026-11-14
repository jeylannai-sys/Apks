package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat
import java.util.Locale

import com.example.model.AppCurrency

@Composable
fun QuickCalculatorCard(
    modifier: Modifier = Modifier,
    currency: AppCurrency = AppCurrency.PHP,
    onApplyToExpense: ((Double) -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(false) }
    var expression by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("0.00") }
    var evaluatedValue by remember { mutableStateOf(0.0) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("calculator_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = "Calculator",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Quick Finance Calculator",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isExpanded) "Adjust expenses & split totals" else "Tap to calculate or adjust budget",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.testTag("calculator_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Calculator"
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    // Display screen
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = if (expression.isEmpty()) "0" else expression,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${currency.symbol}$resultText",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick percentage adjustment presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickPercentChip(label = "+5% Tax", onClick = {
                            if (evaluatedValue > 0) {
                                evaluatedValue *= 1.05
                                resultText = String.format(Locale.US, "%.2f", evaluatedValue)
                                expression = "($expression) * 1.05"
                            }
                        })
                        QuickPercentChip(label = "+10% Tip", onClick = {
                            if (evaluatedValue > 0) {
                                evaluatedValue *= 1.10
                                resultText = String.format(Locale.US, "%.2f", evaluatedValue)
                                expression = "($expression) * 1.10"
                            }
                        })
                        QuickPercentChip(label = "+15% Tip", onClick = {
                            if (evaluatedValue > 0) {
                                evaluatedValue *= 1.15
                                resultText = String.format(Locale.US, "%.2f", evaluatedValue)
                                expression = "($expression) * 1.15"
                            }
                        })
                        QuickPercentChip(label = "÷2 Split", onClick = {
                            if (evaluatedValue > 0) {
                                evaluatedValue /= 2
                                resultText = String.format(Locale.US, "%.2f", evaluatedValue)
                                expression = "($expression) / 2"
                            }
                        })
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Keypad grid
                    val buttons = listOf(
                        listOf("C", "⌫", "%", "÷"),
                        listOf("7", "8", "9", "×"),
                        listOf("4", "5", "6", "-"),
                        listOf("1", "2", "3", "+"),
                        listOf("0", "00", ".", "=")
                    )

                    buttons.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { char ->
                                CalcButton(
                                    symbol = char,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        when (char) {
                                            "C" -> {
                                                expression = ""
                                                resultText = "0.00"
                                                evaluatedValue = 0.0
                                            }
                                            "⌫" -> {
                                                if (expression.isNotEmpty()) {
                                                    expression = expression.dropLast(1)
                                                    evaluatedValue = evaluateSimple(expression)
                                                    resultText = String.format(Locale.US, "%.2f", evaluatedValue)
                                                }
                                            }
                                            "=" -> {
                                                evaluatedValue = evaluateSimple(expression)
                                                resultText = String.format(Locale.US, "%.2f", evaluatedValue)
                                                expression = resultText
                                            }
                                            "%" -> {
                                                evaluatedValue = evaluateSimple(expression) / 100.0
                                                resultText = String.format(Locale.US, "%.2f", evaluatedValue)
                                                expression = resultText
                                            }
                                            else -> {
                                                expression += char
                                                evaluatedValue = evaluateSimple(expression)
                                                resultText = String.format(Locale.US, "%.2f", evaluatedValue)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    if (onApplyToExpense != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (evaluatedValue > 0) {
                                    onApplyToExpense(evaluatedValue)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("apply_to_expense_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Use ${currency.symbol}$resultText as Expense",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPercentChip(
    label: String,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CalcButton(
    symbol: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isOperator = symbol in listOf("÷", "×", "-", "+", "=")
    val isAction = symbol in listOf("C", "⌫", "%")

    val containerColor = when {
        symbol == "=" -> MaterialTheme.colorScheme.primary
        isOperator -> MaterialTheme.colorScheme.primaryContainer
        isAction -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surface
    }

    val contentColor = when {
        symbol == "=" -> MaterialTheme.colorScheme.onPrimary
        isOperator -> MaterialTheme.colorScheme.onPrimaryContainer
        isAction -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        if (symbol == "⌫") {
            Icon(imageVector = Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace", modifier = Modifier.size(16.dp))
        } else {
            Text(
                text = symbol,
                fontSize = if (isOperator || isAction) 16.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun evaluateSimple(raw: String): Double {
    if (raw.isBlank()) return 0.0
    val cleaned = raw.replace("×", "*").replace("÷", "/")
    return try {
        // Simple token parser for numbers & operators
        val tokens = mutableListOf<String>()
        var currentNum = StringBuilder()
        for (ch in cleaned) {
            if (ch in "+-*/") {
                if (currentNum.isNotEmpty()) {
                    tokens.add(currentNum.toString())
                    currentNum = StringBuilder()
                }
                tokens.add(ch.toString())
            } else if (ch.isDigit() || ch == '.') {
                currentNum.append(ch)
            }
        }
        if (currentNum.isNotEmpty()) {
            tokens.add(currentNum.toString())
        }

        if (tokens.isEmpty()) return 0.0

        // Handle * and /
        val intermediateTokens = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (token == "*" || token == "/") {
                val prev = intermediateTokens.removeAt(intermediateTokens.size - 1).toDoubleOrNull() ?: 0.0
                val next = if (i + 1 < tokens.size) tokens[i + 1].toDoubleOrNull() ?: 1.0 else 1.0
                val res = if (token == "*") prev * next else if (next != 0.0) prev / next else 0.0
                intermediateTokens.add(res.toString())
                i += 2
            } else {
                intermediateTokens.add(token)
                i++
            }
        }

        // Handle + and -
        var result = intermediateTokens.firstOrNull()?.toDoubleOrNull() ?: 0.0
        var op = ""
        for (j in 1 until intermediateTokens.size) {
            val t = intermediateTokens[j]
            if (t == "+" || t == "-") {
                op = t
            } else {
                val valNum = t.toDoubleOrNull() ?: 0.0
                if (op == "+") result += valNum
                else if (op == "-") result -= valNum
            }
        }
        result
    } catch (_: Exception) {
        0.0
    }
}
