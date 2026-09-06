package com.galib.step.ui.screens.settings

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.net.toUri
import com.galib.step.Graph
import com.galib.step.R
import com.galib.step.data.backup.BackupManager
import com.galib.step.model.StepPrefs
import com.galib.step.model.ThemeMode
import com.galib.step.ui.components.bouncyClickable
import com.galib.step.ui.components.entrance
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
                            appContext.resources.getQuantityString(R.plurals.backup_imported, days, days),
                            Toast.LENGTH_SHORT
                        ).show()
                        Graph.appScope.launch { runCatching { Graph.repository.syncToday() } }
                    }
                    .onFailure { Toast.makeText(appContext, backupFailedMsg, Toast.LENGTH_SHORT).show() }
            }
        }
    }

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 18.dp, vertical = 8.dp)) {
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
                    title = stringResource(R.string.wallpaper_colours),
                    subtitle = stringResource(R.string.wallpaper_colours_sub),
                    icon = Icons.Rounded.Wallpaper
                ) {
                    Switch(
                        checked = prefs.dynamicColour,
                        onCheckedChange = { on -> viewModel.set { viewModel.p.setDynamicColor(on) } }
                    )
                }
            }
            if (prefs.themeMode != ThemeMode.LIGHT) {
                SettingRow(
                    title = stringResource(R.string.amoled_black),
                    subtitle = stringResource(R.string.amoled_black_sub),
                    icon = Icons.Rounded.Contrast
                ) {
                    Switch(
                        checked = prefs.amoled,
                        onCheckedChange = { on -> viewModel.set { viewModel.p.setAmoled(on) } }
                    )
                }
            }
            SettingRow(
                title = stringResource(R.string.disable_animations),
                subtitle = stringResource(R.string.disable_animations_sub),
                icon = Icons.Rounded.Animation
            ) {
                Switch(
                    checked = prefs.disableAnimations,
                    onCheckedChange = { on -> viewModel.set { viewModel.p.setDisableAnimations(on) } }
                )
            }
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
}

@Composable
private fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        content()
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
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .let { m ->
                if (onClick != null) {
                    m.clip(RoundedCornerShape(14.dp))
                     .bouncyClickable(scaleDown = 0.97f, onClick = onClick)
                } else m
            }
            .padding(horizontal = 18.dp, vertical = 14.dp),
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


