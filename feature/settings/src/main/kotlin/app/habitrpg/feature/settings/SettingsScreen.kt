package app.habitrpg.feature.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.habitrpg.data.backup.ImportError
import app.habitrpg.data.repo.AppLanguage
import app.habitrpg.data.repo.ThemeMode
import app.habitrpg.feature.character.BodyPicker
import app.habitrpg.feature.character.HairPicker
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDataDeleted: () -> Unit = onBack,
    versionName: String = "",
    aboutUrl: String = "",
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    var showAbout by remember { mutableStateOf(false) }
    val s by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Permission state can change in system settings, so re-read it every time the screen comes back.
    var refresh by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back_cd)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // --- Appearance ---
            Section(stringResource(R.string.settings_section_appearance)) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = s.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(stringResource(mode.labelRes())) },
                        )
                    }
                }
                Text(stringResource(R.string.settings_character), style = MaterialTheme.typography.titleSmall)
                BodyPicker(s.body, viewModel::setBody, hairId = s.hair)
                Text(stringResource(R.string.settings_hair), style = MaterialTheme.typography.titleSmall)
                HairPicker(s.hair, viewModel::setHair, bodyId = s.body)
            }
            HorizontalDivider()

            // --- Language ---
            Section(stringResource(R.string.settings_section_language)) {
                LanguageRow(AppLanguage.fromTag(s.language), viewModel::setLanguage)
                Text(
                    stringResource(R.string.settings_language_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()

            // --- Gear ---
            Section(stringResource(R.string.settings_section_gear)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_gear_switch), Modifier.weight(1f))
                    Switch(s.gearBonuses, viewModel::setGearBonuses)
                }
                Text(
                    stringResource(R.string.settings_gear_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()

            // --- Quests and classes ---
            Section(stringResource(R.string.settings_section_quests)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_quests_switch), Modifier.weight(1f))
                    Switch(s.quests, viewModel::setQuests)
                }
                Text(
                    stringResource(R.string.settings_quests_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            Section(stringResource(R.string.settings_section_classes)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_classes_switch), Modifier.weight(1f))
                    Switch(s.classes, viewModel::setClasses)
                }
                Text(
                    stringResource(R.string.settings_classes_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()

            // --- Day start ---
            Section(stringResource(R.string.settings_section_daystart)) {
                var dragging by remember(s.dayStartHour) { mutableFloatStateOf(s.dayStartHour.toFloat()) }
                val hour = dragging.toInt()
                val dayStartDescription = stringResource(R.string.settings_daystart_cd, hourLabel(hour))
                Text(hourLabel(hour), style = MaterialTheme.typography.titleLarge)
                Slider(
                    value = dragging,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = { viewModel.setDayStartHour(dragging.toInt()) },
                    valueRange = 0f..23f,
                    steps = 22,
                    modifier = Modifier.semantics { contentDescription = dayStartDescription },
                )
                Text(
                    stringResource(R.string.settings_daystart_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()

            // --- Pause ---
            Section(stringResource(R.string.settings_section_pause)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_pause_switch), Modifier.weight(1f))
                    Switch(s.paused, viewModel::setPaused)
                }
                Text(
                    stringResource(R.string.settings_pause_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()

            // --- Reminders ---
            @Suppress("UNUSED_EXPRESSION") refresh
            Section(stringResource(R.string.settings_section_reminders)) {
                if (Build.VERSION.SDK_INT >= 33) {
                    val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    Status(stringResource(if (granted) R.string.settings_notifications_on else R.string.settings_notifications_off))
                    if (!granted) {
                        OutlinedButton(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text(stringResource(R.string.settings_allow_notifications))
                        }
                    }
                } else {
                    Status(stringResource(R.string.settings_notifications_on))
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    val exact = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
                    Status(stringResource(if (exact) R.string.settings_exact_on else R.string.settings_exact_off))
                    if (!exact) {
                        OutlinedButton(onClick = {
                            context.startSafely(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                        }) { Text(stringResource(R.string.settings_allow_exact)) }
                    }
                }
            }
            HorizontalDivider()

            // --- Tags ---
            Section(stringResource(R.string.settings_section_tags)) {
                if (tags.isEmpty()) {
                    Text(stringResource(R.string.settings_tags_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                tags.forEach { t ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { viewModel.deleteTag(t.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.settings_tag_delete_cd, t.name))
                        }
                    }
                }
                if (tags.isNotEmpty()) {
                    Text(
                        stringResource(R.string.settings_tags_hint),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()

            // --- Backup ---
            Section(stringResource(R.string.settings_section_backup)) {
                Text(
                    stringResource(R.string.settings_backup_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("habit-rpg-backup-${LocalDate.now()}.json") }) {
                        Text(stringResource(R.string.settings_backup_save))
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                        Text(stringResource(R.string.settings_backup_restore))
                    }
                }
            }
            HorizontalDivider()

            // --- Delete data ---
            Section(stringResource(R.string.settings_section_delete)) {
                Text(
                    stringResource(R.string.settings_delete_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.settings_delete_button)) }
            }
            HorizontalDivider()

            // --- Battery ---
            Section(stringResource(R.string.settings_section_battery)) {
                Text(
                    stringResource(R.string.settings_battery_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = {
                    context.startSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }) { Text(stringResource(R.string.settings_battery_open)) }
            }
            HorizontalDivider()

            // --- About ---
            Section(stringResource(R.string.settings_section_about)) {
                TextButton(onClick = { showAbout = true }) { Text(stringResource(R.string.settings_version, versionName)) }
            }
        }
    }

    if (showAbout) {
        AboutDialog(
            version = versionName,
            url = aboutUrl,
            onOpen = { context.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(aboutUrl))) },
            onDismiss = { showAbout = false },
        )
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.settings_restore_title)) },
            text = { Text(stringResource(R.string.settings_restore_text)) },
            confirmButton = { TextButton(onClick = { viewModel.import(uri); pendingImport = null }) { Text(stringResource(R.string.settings_restore_confirm)) } },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.settings_delete_title)) },
            text = { Text(stringResource(R.string.settings_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; viewModel.eraseAll(onDataDeleted) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.settings_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
    message?.let { shown ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            text = { Text(messageText(shown)) },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text(stringResource(R.string.settings_ok)) } },
        )
    }
}

@Composable
internal fun AboutDialog(version: String, url: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_section_about)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_version, version), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.settings_about_text))
                if (url.isNotBlank()) TextButton(onClick = onOpen) { Text(url) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) } },
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun Status(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge)

private fun ThemeMode.labelRes() = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

@Composable
private fun messageText(m: SettingsMessage): String = when (m) {
    SettingsMessage.BackupSaved -> stringResource(R.string.settings_msg_backup_saved)
    SettingsMessage.BackupFailed -> stringResource(R.string.settings_msg_backup_failed)
    SettingsMessage.DeleteFailed -> stringResource(R.string.settings_msg_delete_failed)
    SettingsMessage.FileOpenFailed -> stringResource(R.string.settings_msg_file_open_failed)
    is SettingsMessage.Restored -> stringResource(
        R.string.settings_msg_restored,
        pluralStringResource(R.plurals.settings_n_tasks, m.tasks, m.tasks),
        pluralStringResource(R.plurals.settings_n_rewards, m.rewards, m.rewards),
        pluralStringResource(R.plurals.settings_n_entries, m.events, m.events),
    )
    is SettingsMessage.ImportFailed -> stringResource(
        when (m.error) {
            ImportError.TOO_LARGE -> R.string.settings_err_too_large
            ImportError.UNREADABLE -> R.string.settings_err_unreadable
            ImportError.NOT_A_BACKUP -> R.string.settings_err_not_a_backup
            ImportError.INVALID_VERSION -> R.string.settings_err_invalid_version
            ImportError.NEWER_VERSION -> R.string.settings_err_newer_version
            ImportError.UNKNOWN_TASK -> R.string.settings_err_unknown_task
            ImportError.INVALID_SCHEDULE -> R.string.settings_err_invalid_schedule
            ImportError.INVALID_DUE_DATE -> R.string.settings_err_invalid_due_date
            ImportError.INVALID_REMINDER -> R.string.settings_err_invalid_reminder
            ImportError.INVALID_CHARACTER -> R.string.settings_err_invalid_character
            ImportError.INVALID_DATE -> R.string.settings_err_invalid_date
            ImportError.INCONSISTENT -> R.string.settings_err_inconsistent
        },
    )
}

private fun hourLabel(hour: Int) = "%02d:00".format(hour)

/** Settings screens differ between phone makers; never crash when one is missing. */
private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        // nothing to open on this device
    }
}
