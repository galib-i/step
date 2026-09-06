package com.galib.step.ui.screens.dashboard

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.galib.step.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.galib.step.Graph
import com.galib.step.data.db.DailySummaryEntity
import com.galib.step.model.DailyStats
import com.galib.step.model.StepPrefs
import com.galib.step.ui.components.AnimatedCounter
import com.galib.step.ui.components.GoalEditor
import com.galib.step.ui.components.entrance
import com.galib.step.util.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalLocale

data class DashboardUiState(
    val stats: DailyStats = DailyStats.empty(),
    val prefs: StepPrefs = StepPrefs(),
    val weekSteps: Long = 0,
    val yesterdaySteps: Long = 0,
    val sevenDayAvg: Long = 0
)

class DashboardViewModel : ViewModel() {
    private val repo = Graph.repository
    private val prefs = Graph.prefs

    val isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<DashboardUiState> = combine(
        repo.observeToday(),
        prefs.prefs,
        repo.observeRange(LocalDate.now().minusDays(8), LocalDate.now())
    ) { stats, p, recent ->
        val yesterday = LocalDate.now().minusDays(1).toEpochDay()
        val prev7 = recent.filter { it.epochDay < LocalDate.now().toEpochDay() }
        val avg = if (prev7.isEmpty()) 0L else prev7.sumOf { it.steps } / prev7.size
        DashboardUiState(
            stats = stats,
            prefs = p,
            weekSteps = computeWeekSteps(recent),
            yesterdaySteps = recent.firstOrNull { it.epochDay == yesterday }?.steps ?: 0L,
            sevenDayAvg = avg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    init {
        refresh()
    }

    private fun computeWeekSteps(summaries: List<DailySummaryEntity>, today: LocalDate = LocalDate.now()): Long {
        val monday = today.minusDays(((today.dayOfWeek.value + 6) % 7).toLong())
        val from = monday.toEpochDay()
        val to = from + 6
        return summaries.filter { it.epochDay in from..to }.sumOf { it.steps }
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            runCatching { repo.sync() }
            delay(350.milliseconds)
            isRefreshing.value = false
        }
    }

    fun setGoal(goal: Int) {
        viewModelScope.launch { prefs.setDailyGoal(goal) }
    }

    fun setWeeklyGoal(goal: Int) {
        viewModelScope.launch { prefs.setWeeklyGoal(goal) }
    }

    fun stopBackgroundTracking(context: Context) {
        com.galib.step.service.StepTrackingService.stop(context)
        viewModelScope.launch { prefs.setBackgroundTracking(false) }
    }

    fun startBackgroundTracking(context: Context) {
        runCatching { com.galib.step.service.StepTrackingService.start(context) }
        viewModelScope.launch { prefs.setBackgroundTracking(true) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    var showGoalSheet by remember { mutableStateOf(false) }
    @Suppress("DEPRECATION")
    val goalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    val activityRecognitionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startBackgroundTracking(context)
    }

    var hasPermission by remember {
        mutableStateOf(
            android.os.Build.VERSION.SDK_INT < 29 ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = android.os.Build.VERSION.SDK_INT < 29 ||
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.ACTIVITY_RECOGNITION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun ensureActivityPermission(): Boolean {
        val granted = android.os.Build.VERSION.SDK_INT < 29 ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            activityRecognitionLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
        }
        return granted
    }

    // Haptic tick every 1,000 steps
    var lastBucket by remember { mutableIntStateOf(-1) }
    LaunchedEffect(state.stats.steps) {
        val bucket = (state.stats.steps / 1000).toInt()
        if (lastBucket in 0 until bucket) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        lastBucket = bucket
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            DashboardHeader(
                onEditGoal = { showGoalSheet = true },
                modifier = Modifier.entrance(0)
            )



            HeroCard(
                stats = state.stats,
                isTracking = state.prefs.backgroundTracking,
                hasPermission = hasPermission,
                disableAnimations = state.prefs.disableAnimations,
                onToggleTracking = {
                    if (state.prefs.backgroundTracking) {
                        viewModel.stopBackgroundTracking(context)
                    } else {
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (ensureActivityPermission()) {
                            viewModel.startBackgroundTracking(context)
                        }
                    }
                },
                modifier = Modifier.entrance(1)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallInfoCard(
                    title = stringResource(R.string.yesterday),
                    value = Formatters.steps(state.yesterdaySteps),
                    modifier = Modifier.weight(1f).entrance(5)
                )
                SmallInfoCard(
                    title = stringResource(R.string.seven_day_avg),
                    value = Formatters.steps(state.sevenDayAvg),
                    modifier = Modifier.weight(1f).entrance(6)
                )
            }

            if (state.prefs.weeklyGoal > 0) {
                WeeklyGoalCard(
                    weekSteps = state.weekSteps,
                    weeklyGoal = state.prefs.weeklyGoal,
                    disableAnimations = state.prefs.disableAnimations,
                    modifier = Modifier.entrance(7)
                )
            }


        }
    }

    if (showGoalSheet) {
        ModalBottomSheet(
            onDismissRequest = { showGoalSheet = false },
            sheetState = goalSheetState
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                var selectedGoalType by remember { mutableStateOf("Daily") }

                @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.targets),
                        style = MaterialTheme.typography.headlineSmall
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.height(32.dp)) {
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            onClick = { selectedGoalType = "Daily" },
                            selected = selectedGoalType == "Daily",
                            icon = {}
                        ) {
                            Text("Daily", style = MaterialTheme.typography.labelMedium)
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            onClick = { selectedGoalType = "Weekly" },
                            selected = selectedGoalType == "Weekly",
                            icon = {}
                        ) {
                            Text("Weekly", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                if (selectedGoalType == "Daily") {
                    GoalEditor(
                        goal = state.prefs.dailyGoal,
                        onGoalChange = { viewModel.setGoal(it) },
                        keyboardTrigger = goalSheetState.currentValue == androidx.compose.material3.SheetValue.Expanded
                    )
                } else {
                    GoalEditor(
                        goal = state.prefs.weeklyGoal,
                        onGoalChange = { viewModel.setWeeklyGoal(it) },
                        keyboardTrigger = goalSheetState.currentValue == androidx.compose.material3.SheetValue.Expanded
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    onEditGoal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(R.string.nav_today),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM", LocalLocale.current.platformLocale)),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        IconButton(
            onClick = onEditGoal,
            modifier = Modifier.offset(y = 10.dp)
        ) {
            Icon(
                Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.edit_goal),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroCard(
    stats: DailyStats,
    isTracking: Boolean,
    hasPermission: Boolean,
    onToggleTracking: () -> Unit,
    modifier: Modifier = Modifier,
    disableAnimations: Boolean = false
) {
    val progress by animateFloatAsState(
        targetValue = stats.progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessVeryLow
        ),
        label = "heroProgress"
    )
    val goalHit = stats.goal > 0 && stats.steps >= stats.goal

    val shownSteps by animateIntAsState(
        targetValue = stats.steps.toInt(),
        animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
        label = "stepCount"
    )

    val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "breatheAmp"
    )

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val density = LocalDensity.current
                    val ringStroke = remember(density) {
                        with(density) { Stroke(width = 13.dp.toPx(), cap = StrokeCap.Round) }
                    }
                    val trackStroke = remember(density) {
                        with(density) { Stroke(width = 13.dp.toPx(), cap = StrokeCap.Round) }
                    }
                    CircularWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(258.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        stroke = ringStroke,
                        trackStroke = trackStroke,
                        wavelength = 38.dp,
                        amplitude = { if (goalHit) 1f else breathe },
                        waveSpeed = if (disableAnimations) 0.dp else 5.dp
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedCounter(
                            value = shownSteps.toLong(),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.of_steps_goal, Formatters.steps(stats.goal.toLong())),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = when {
                        goalHit -> stringResource(R.string.goal_crushed)
                        else -> stringResource(
                            R.string.steps_to_go,
                            Formatters.steps((stats.goal - stats.steps).coerceAtLeast(0))
                        )
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                
                androidx.compose.material3.FilledTonalButton(
                    onClick = onToggleTracking,
                    shape = RoundedCornerShape(16.dp),
                    enabled = isTracking || hasPermission,
                    modifier = Modifier.fillMaxWidth(0.6f).height(56.dp)
                ) {
                    Icon(
                        imageVector = if (isTracking) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = if (isTracking) "Stop Tracking" else "Start Tracking",
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.size(12.dp, 0.dp))
                    Text(
                        text = if (isTracking) "STOP" else "START",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallInfoCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WeeklyGoalCard(weekSteps: Long, weeklyGoal: Int, modifier: Modifier = Modifier, disableAnimations: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.this_week),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${Formatters.compactSteps(weekSteps)} / ${Formatters.compactSteps(weeklyGoal.toLong())}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            val density = LocalDensity.current
            val barStroke = remember(density) {
                with(density) { Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round) }
            }
            LinearWavyProgressIndicator(
                progress = { (weekSteps.toFloat() / weeklyGoal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(26.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                stroke = barStroke,
                trackStroke = barStroke,
                amplitude = { 1f },
                wavelength = 32.dp,
                waveSpeed = if (disableAnimations) 0.dp else 5.dp
            )
        }
    }
}




