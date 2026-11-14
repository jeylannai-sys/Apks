package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.SplashLogoScreen
import com.example.ui.screens.ClockScreen
import com.example.ui.screens.DiagramPlanningScreen
import com.example.ui.screens.FinanceScreen
import com.example.ui.screens.HealthFitnessScreen
import com.example.ui.screens.MilestonesScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode

enum class AppScreen(
    val title: String,
    val navLabel: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    TASKS("Tasks", "Tasks", Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle, "nav_tasks"),
    DIAGRAM("Flowcharts & Plans", "Plans", Icons.Filled.AccountTree, Icons.Outlined.AccountTree, "nav_diagram"),
    FINANCE("Finance & Budget", "Finance", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, "nav_finance"),
    HEALTH("Health & Fitness", "Health", Icons.Filled.FitnessCenter, Icons.Outlined.FitnessCenter, "nav_health"),
    CLOCK("Clock & Alarms", "Clock", Icons.Filled.Alarm, Icons.Outlined.Alarm, "nav_clock"),
    REPORTS("Monthly Reports", "Reports", Icons.Filled.BarChart, Icons.Outlined.BarChart, "nav_reports"),
    SETTINGS("Settings", "Settings", Icons.Filled.Tune, Icons.Outlined.Tune, "nav_settings"),
    MILESTONES("User Milestones", "Milestones", Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents, "nav_milestones")
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            MyApplicationTheme(themeMode = themeMode) {
                val context = LocalContext.current

                // Check if launched directly from a specific Home Screen widget
                val widgetTarget = remember(intent) {
                    intent?.getStringExtra("EXTRA_SCREEN")?.let { screenName ->
                        try { AppScreen.valueOf(screenName) } catch (_: Exception) { null }
                    }
                }
                var currentScreenName by rememberSaveable {
                    mutableStateOf((widgetTarget ?: AppScreen.TASKS).name)
                }
                val currentScreen = remember(currentScreenName) {
                    try { AppScreen.valueOf(currentScreenName) } catch (_: Exception) { AppScreen.TASKS }
                }
                fun navigateTo(screen: AppScreen) {
                    currentScreenName = screen.name
                }

                var showSplash by rememberSaveable { mutableStateOf(widgetTarget == null) }

                // Request notification permission on Android 13+ (API 33+)
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {}

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                // Predictive-back-friendly BackHandler: disabled on root TASKS screen so system back-to-home works seamlessly
                BackHandler(enabled = !showSplash && currentScreen != AppScreen.TASKS) {
                    if (currentScreen == AppScreen.MILESTONES) {
                        navigateTo(AppScreen.SETTINGS)
                    } else {
                        navigateTo(AppScreen.TASKS)
                    }
                }

                val tasks by viewModel.tasks.collectAsStateWithLifecycle()
                val habitsWithStats by viewModel.habitsWithStats.collectAsStateWithLifecycle()
                val allHabitCompletions by viewModel.allHabitCompletions.collectAsStateWithLifecycle()

                val alarms by viewModel.alarms.collectAsStateWithLifecycle()
                val transactions by viewModel.transactions.collectAsStateWithLifecycle()
                val budgets by viewModel.budgets.collectAsStateWithLifecycle()
                val currency by viewModel.currency.collectAsStateWithLifecycle()
                val calorieLogs by viewModel.calorieLogs.collectAsStateWithLifecycle()
                val workoutLogs by viewModel.workoutLogs.collectAsStateWithLifecycle()
                val dailyHealth by viewModel.dailyHealth.collectAsStateWithLifecycle()

                val diagrams by viewModel.diagrams.collectAsStateWithLifecycle()
                val selectedDiagramId by viewModel.selectedDiagramId.collectAsStateWithLifecycle()
                val diagramNodes by viewModel.currentDiagramNodes.collectAsStateWithLifecycle()
                val diagramConnections by viewModel.currentDiagramConnections.collectAsStateWithLifecycle()
                val currentDiagram = remember(diagrams, selectedDiagramId) {
                    diagrams.find { it.id == selectedDiagramId } ?: diagrams.firstOrNull()
                }

                val milestones by viewModel.milestones.collectAsStateWithLifecycle()

                LaunchedEffect(currentScreen) {
                    viewModel.triggerMilestonesEvaluation()
                }

                val primaryNavScreens = remember {
                    listOf(
                        AppScreen.TASKS,
                        AppScreen.DIAGRAM,
                        AppScreen.FINANCE,
                        AppScreen.HEALTH,
                        AppScreen.CLOCK,
                        AppScreen.REPORTS
                    )
                }

                if (showSplash) {
                    SplashLogoScreen(
                        onDismiss = { showSplash = false }
                    )
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val useNavigationRail = maxWidth >= 600.dp

                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            contentWindowInsets = WindowInsets.safeDrawing,
                            topBar = {
                                CenterAlignedTopAppBar(
                                    title = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = currentScreen.selectedIcon,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = currentScreen.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 17.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    },
                                    actions = {
                                        IconButton(
                                            onClick = {
                                                val nextMode = if (themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
                                                viewModel.setThemeMode(nextMode)
                                            },
                                            modifier = Modifier.testTag("top_theme_toggle_button")
                                        ) {
                                            Icon(
                                                imageVector = if (themeMode == ThemeMode.DARK) Icons.Default.LightMode else Icons.Default.DarkMode,
                                                contentDescription = if (themeMode == ThemeMode.DARK) "Switch to Light Mode" else "Switch to Dark Mode",
                                                tint = if (themeMode == ThemeMode.DARK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                navigateTo(
                                                    if (currentScreen == AppScreen.SETTINGS) AppScreen.TASKS else AppScreen.SETTINGS
                                                )
                                            },
                                            modifier = Modifier.testTag("top_settings_button")
                                        ) {
                                            Icon(
                                                imageVector = if (currentScreen == AppScreen.SETTINGS) Icons.Default.CheckCircle else Icons.Default.Settings,
                                                contentDescription = "Settings",
                                                tint = if (currentScreen == AppScreen.SETTINGS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                            },
                            bottomBar = {
                                if (!useNavigationRail) {
                                    NavigationBar(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 6.dp,
                                        windowInsets = WindowInsets.navigationBars
                                    ) {
                                        primaryNavScreens.forEach { screen ->
                                            val isSelected = currentScreen == screen
                                            NavigationBarItem(
                                                selected = isSelected,
                                                onClick = { navigateTo(screen) },
                                                icon = {
                                                    Icon(
                                                        imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                                        contentDescription = screen.title
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        text = screen.navLabel,
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Ellipsis,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                colors = NavigationBarItemDefaults.colors(
                                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                modifier = Modifier.testTag(screen.testTag)
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                if (useNavigationRail) {
                                    NavigationRail(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .windowInsetsPadding(WindowInsets.navigationBars)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Spacer(modifier = Modifier.weight(0.1f))
                                        primaryNavScreens.forEach { screen ->
                                            val isSelected = currentScreen == screen
                                            NavigationRailItem(
                                                selected = isSelected,
                                                onClick = { navigateTo(screen) },
                                                icon = {
                                                    Icon(
                                                        imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                                        contentDescription = screen.title
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        text = screen.navLabel,
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Ellipsis,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                colors = NavigationRailItemDefaults.colors(
                                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                modifier = Modifier.testTag(screen.testTag)
                                            )
                                        }
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                    VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                }

                                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                    AnimatedContent(
                                        targetState = currentScreen,
                                        transitionSpec = {
                                            fadeIn(animationSpec = tween(180)) togetherWith
                                                fadeOut(animationSpec = tween(120))
                                        },
                                        label = "screen_transition"
                                    ) { targetScreen ->
                                        when (targetScreen) {
                                            AppScreen.TASKS -> TasksScreen(
                                                tasks = tasks,
                                                onSaveTask = { viewModel.saveTask(it) },
                                                onToggleComplete = { id, comp -> viewModel.toggleTaskComplete(id, comp) },
                                                onDeleteTask = { viewModel.deleteTask(it) },
                                                habitsWithStats = habitsWithStats,
                                                allHabitCompletions = allHabitCompletions,
                                                onToggleHabit = { viewModel.toggleHabitToday(it) },
                                                onSaveHabit = { viewModel.saveHabit(it) },
                                                onDeleteHabit = { viewModel.deleteHabit(it) }
                                            )

                                            AppScreen.DIAGRAM -> DiagramPlanningScreen(
                                                diagrams = diagrams,
                                                currentDiagram = currentDiagram,
                                                nodes = diagramNodes,
                                                connections = diagramConnections,
                                                onSelectDiagram = { viewModel.selectDiagram(it) },
                                                onCreateDiagram = { title, style -> viewModel.createDiagram(title, style) },
                                                onUpdateDiagramStyle = { diagram, style -> viewModel.updateDiagramStyle(diagram, style) },
                                                onDeleteDiagram = { viewModel.deleteDiagram(it) },
                                                onSaveNode = { viewModel.saveNode(it) },
                                                onDeleteNode = { viewModel.deleteNode(it) },
                                                onNodeMoved = { id, x, y -> viewModel.moveNode(id, x, y) },
                                                onSaveConnection = { viewModel.saveConnection(it) },
                                                onDeleteConnection = { viewModel.deleteConnection(it) },
                                                onAutoArrangeNodes = { viewModel.autoArrangeDiagram(it) },
                                                onLoadTemplate = { viewModel.loadDiagramTemplate(it) },
                                                onConvertToTask = { viewModel.saveTask(it) }
                                            )

                                            AppScreen.FINANCE -> FinanceScreen(
                                                transactions = transactions,
                                                budgets = budgets,
                                                currency = currency,
                                                onCurrencyChanged = { viewModel.setCurrency(it) },
                                                onSaveExpense = { viewModel.saveExpense(it) },
                                                onDeleteExpense = { viewModel.deleteExpense(it) },
                                                onSetBudget = { cat, limit -> viewModel.setBudget(cat, limit) },
                                                onAdoptGoal = { viewModel.adoptSmartGoal(it) }
                                            )

                                            AppScreen.HEALTH -> HealthFitnessScreen(
                                                calorieLogs = calorieLogs,
                                                workoutLogs = workoutLogs,
                                                dailyHealth = dailyHealth,
                                                onSaveCalorieLog = { viewModel.saveCalorieLog(it) },
                                                onDeleteCalorieLog = { viewModel.deleteCalorieLog(it) },
                                                onSaveWorkout = { viewModel.saveWorkoutLog(it) },
                                                onDeleteWorkout = { viewModel.deleteWorkoutLog(it) },
                                                onAddWater = { viewModel.addWaterIntake(it) }
                                            )

                                            AppScreen.CLOCK -> ClockScreen(
                                                alarms = alarms,
                                                onSaveAlarm = { viewModel.saveAlarm(it) },
                                                onToggleAlarm = { id, enabled -> viewModel.toggleAlarm(id, enabled) },
                                                onDeleteAlarm = { viewModel.deleteAlarm(it) }
                                            )

                                            AppScreen.REPORTS -> ReportsScreen(
                                                transactions = transactions,
                                                budgets = budgets,
                                                currency = currency,
                                                repository = viewModel.repository
                                            )

                                            AppScreen.SETTINGS -> SettingsScreen(
                                                currentTheme = themeMode,
                                                onThemeChanged = { viewModel.setThemeMode(it) },
                                                currency = currency,
                                                onCurrencyChanged = { viewModel.setCurrency(it) },
                                                onOpenMilestones = { navigateTo(AppScreen.MILESTONES) }
                                            )

                                            AppScreen.MILESTONES -> MilestonesScreen(
                                                milestones = milestones
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
