package com.galib.step.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.galib.step.Graph
import com.galib.step.R
import com.galib.step.data.backup.BackupManager
import com.galib.step.model.StepPrefs
import com.galib.step.model.ThemeMode
import com.galib.step.service.StepTrackingService
import com.galib.step.ui.components.GoalEditor
import com.galib.step.ui.components.StepSlider
import com.galib.step.ui.components.bouncyClickable
import com.galib.step.ui.components.entrance

import com.galib.step.util.Formatters
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.core.net.toUri

class SettingsViewModel : ViewModel() {
    private val prefs = Graph.prefs

    val state: StateFlow<StepPrefs> =
        prefs.prefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StepPrefs())

    fun set(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    val p = prefs
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val prefs by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    // The health foreground-service type needs this granted at runtime,
    // otherwise startForeground throws and the service dies.
    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) runCatching { StepTrackingService.start(context) }
    }

    fun ensureActivityPermission(): Boolean {
        val granted = Build.VERSION.SDK_INT < 29 ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACTIVITY_RECOGNITION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        return granted
    }

    val exportedMsg = stringResource(R.string.backup_exported)
    val backupFailedMsg = stringResource(R.string.backup_failed)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val appContext = context.applicationContext
            scope.launch {
                BackupManager.export(appContext, uri)
                    .onSuccess { Toast.makeText(appContext, exportedMsg, Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(appContext, backupFailedMsg, Toast.LENGTH_SHORT).show() }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val appContext = context.applicationContext
            scope.launch {
                BackupManager.import(appContext, uri)
                    .onSuccess { days ->
                        Toast.makeText(
                            appContext,
                            appContext.getString(R.string.backup_imported, days),
                            Toast.LENGTH_SHORT
                        ).show()
                        Graph.appScope.launch { runCatching { Graph.repository.syncToday() } }
                    }
                    .onFailure { Toast.makeText(appContext, backupFailedMsg, Toast.LENGTH_SHORT).show() }
            }
        }
    }

    var showGoalDialog by remember { mutableStateOf(false) }
    var sliderDialog by remember { mutableStateOf<SliderDialogSpec?>(null) }



    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.entrance(0)
        )

        SettingsCard(modifier = Modifier.entrance(1)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEach { mode ->
                    ToggleButton(
                        checked = prefs.themeMode == mode,
                        onCheckedChange = { if (it) viewModel.set { viewModel.p.setThemeMode(mode) } },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                ThemeMode.DARK -> stringResource(R.string.theme_dark)
                            }
                        )
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingRow(
                    title = stringResource(R.string.wallpaper_colors),
                    subtitle = stringResource(R.string.wallpaper_colors_sub),
                    icon = Icons.Rounded.Wallpaper
                ) {
                    IconSwitch(
                        checked = prefs.dynamicColor,
                        onCheckedChange = { on -> viewModel.set { viewModel.p.setDynamicColor(on) } }
                    )
                }
            }
            SettingRow(
                title = stringResource(R.string.amoled_black),
                subtitle = stringResource(R.string.amoled_black_sub),
                icon = Icons.Rounded.Contrast
            ) {
                IconSwitch(
                    checked = prefs.amoled,
                    onCheckedChange = { on -> viewModel.set { viewModel.p.setAmoled(on) } }
                )
            }

        }

        SettingsCard(modifier = Modifier.entrance(2)) {
            if (Build.VERSION.SDK_INT >= 36) {
                SettingRow(
                    title = stringResource(R.string.live_updates),
                    subtitle = stringResource(R.string.live_updates_sub),
                    icon = Icons.Rounded.Splitscreen
                ) {
                    IconSwitch(
                        checked = prefs.liveUpdates,
                        onCheckedChange = { on ->
                            if (on) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.set { viewModel.p.setLiveUpdates(on) }
                            if (on && ensureActivityPermission()) {
                                runCatching { StepTrackingService.start(context) }
                            }
                        }
                    )
                }
            }
            SettingRow(
                title = stringResource(R.string.background_tracking),
                subtitle = stringResource(R.string.background_tracking_sub),
                icon = Icons.AutoMirrored.Rounded.DirectionsRun
            ) {
                IconSwitch(
                    checked = prefs.backgroundTracking,
                    onCheckedChange = { on ->
                        if (on && Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.set { viewModel.p.setBackgroundTracking(on) }
                        if (on) {
                            if (ensureActivityPermission()) StepTrackingService.start(context)
                        } else {
                            StepTrackingService.stop(context)
                        }
                    }
                )
            }
        }

        SettingsCard(modifier = Modifier.entrance(3)) {
            SettingRow(
                title = stringResource(R.string.daily_goal),
                subtitle = stringResource(R.string.steps_value, Formatters.steps(prefs.dailyGoal.toLong())),
                icon = Icons.Rounded.TrackChanges,
                onClick = { showGoalDialog = true }
            )
            val offLabel = stringResource(R.string.off)
            SettingRow(
                title = stringResource(R.string.weekly_goal),
                icon = Icons.Rounded.CalendarMonth,
                subtitle = if (prefs.weeklyGoal > 0)
                    stringResource(R.string.steps_value, Formatters.steps(prefs.weeklyGoal.toLong()))
                else offLabel,
                onClick = {
                    sliderDialog = SliderDialogSpec(
                        titleRes = R.string.weekly_goal,
                        value = prefs.weeklyGoal.toFloat(),
                        range = 0f..200000f,
                        step = 5000f,
                        format = { v -> if (v <= 0f) offLabel else Formatters.steps(v.toLong()) },
                        onDone = { v -> viewModel.set { viewModel.p.setWeeklyGoal(v.toInt()) } }
                    )
                }
            )
        }



        SettingsCard(modifier = Modifier.entrance(5)) {
            SettingRow(
                title = stringResource(R.string.export_backup),
                subtitle = stringResource(R.string.export_backup_sub),
                icon = Icons.Rounded.Upload,
                onClick = { exportLauncher.launch("stride-backup.json") }
            )
            SettingRow(
                title = stringResource(R.string.import_backup),
                subtitle = stringResource(R.string.import_backup_sub),
                icon = Icons.Rounded.Download,
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
            )
        }

        SettingsCard(modifier = Modifier.entrance(6)) {
            SettingRow(
                title = stringResource(R.string.app_language),
                subtitle = stringResource(R.string.app_language_sub),
                icon = Icons.Rounded.Language
            )
        }

        SettingsCard(modifier = Modifier.entrance(8)) {
            SettingRow(
                title = stringResource(R.string.about),
                subtitle = stringResource(R.string.about_sub, com.galib.step.BuildConfig.VERSION_NAME),
                icon = Icons.Rounded.Info,
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "https://github.com/galib-i/step".toUri()
                            )
                        )
                    }
                }
            )
        }

        Spacer(Modifier.height(120.dp))
    }



    if (showGoalDialog) {
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            confirmButton = {
                TextButton(onClick = { showGoalDialog = false }) { Text(stringResource(R.string.done)) }
            },
            title = { Text(stringResource(R.string.daily_goal)) },
            text = {
                GoalEditor(
                    goal = prefs.dailyGoal,
                    onGoalChange = { g -> viewModel.set { viewModel.p.setDailyGoal(g) } }
                )
            }
        )
    }

    sliderDialog?.let { spec ->
        SliderDialog(spec = spec, onDismiss = { sliderDialog = null })
    }
}

private data class SliderDialogSpec(
    val titleRes: Int,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val step: Float,
    val format: (Float) -> String,
    val onDone: (Float) -> Unit
)

@Composable
private fun SliderDialog(spec: SliderDialogSpec, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(spec.value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                spec.onDone(value)
                onDismiss()
            }) { Text(stringResource(R.string.done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(spec.titleRes)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = spec.format(value),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                StepSlider(
                    value = value,
                    onValueChange = { v -> value = (v / spec.step).toInt() * spec.step },
                    valueRange = spec.range
                )
            }
        }
    )
}

@Composable
private fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .bouncyClickable(scaleDown = 0.97f, onClick = onClick)
                else Modifier
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        icon?.let {
            Icon(
                it,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(24.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

/** Switch with the feature's icon riding inside the thumb. */
@Composable
private fun IconSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}
