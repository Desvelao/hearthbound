package app.habitrpg.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.habitrpg.data.backup.BackupRepository
import app.habitrpg.data.backup.ImportError
import app.habitrpg.data.backup.ImportResult
import app.habitrpg.data.db.TagEntity
import app.habitrpg.data.repo.AppLanguage
import app.habitrpg.data.repo.GameRepository
import app.habitrpg.data.repo.Settings
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.data.repo.TagRepository
import app.habitrpg.data.repo.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val game: GameRepository,
    private val tagRepo: TagRepository,
    private val backup: BackupRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val state: StateFlow<Settings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    val tags: StateFlow<List<TagEntity>> =
        tagRepo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<SettingsMessage?>(null)
    /** The outcome of the last export or import, for the screen to show once (it turns it into text in the right language). */
    val message: StateFlow<SettingsMessage?> = _message.asStateFlow()
    fun dismissMessage() { _message.value = null }

    /** Goes through the game repository so the current day is re-anchored and nothing is scored by accident. */
    fun setDayStartHour(hour: Int) { viewModelScope.launch { game.setDayStartHour(hour) } }

    fun setPaused(paused: Boolean) { viewModelScope.launch { settings.setPaused(paused) } }
    fun setGearBonuses(on: Boolean) { viewModelScope.launch { settings.setGearBonuses(on) } }
    fun setQuests(on: Boolean) { viewModelScope.launch { settings.setQuests(on) } }
    fun setClasses(on: Boolean) { viewModelScope.launch { settings.setClasses(on) } }
    fun setLanguage(language: AppLanguage) { viewModelScope.launch { settings.setLanguage(language) } }
    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { settings.setThemeMode(mode) } }
    fun setHair(id: String) { viewModelScope.launch { settings.setHair(id) } }
    fun setBody(id: String) { viewModelScope.launch { settings.setBody(id) } }
    fun deleteTag(id: String) { viewModelScope.launch { tagRepo.delete(id) } }

    fun export(uri: Uri) {
        viewModelScope.launch {
            _message.value = try {
                val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("no output stream")
                out.use { backup.export(it) }
                SettingsMessage.BackupSaved
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SettingsMessage.BackupFailed
            }
        }
    }

    /** Wipes everything, then calls [onDone] (the app goes back to onboarding). */
    fun eraseAll(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                backup.eraseAll()
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = SettingsMessage.DeleteFailed
            }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _message.value = try {
                val input = context.contentResolver.openInputStream(uri) ?: error("no input stream")
                when (val r = input.use { backup.import(it) }) {
                    is ImportResult.Success -> SettingsMessage.Restored(r.tasks, r.rewards, r.events)
                    is ImportResult.Failure -> SettingsMessage.ImportFailed(r.error)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SettingsMessage.FileOpenFailed
            }
        }
    }
}

/** What the settings screen tells the player after a backup action. Never text: the screen picks the words. */
sealed interface SettingsMessage {
    data object BackupSaved : SettingsMessage
    data object BackupFailed : SettingsMessage
    data object DeleteFailed : SettingsMessage
    data object FileOpenFailed : SettingsMessage
    data class Restored(val tasks: Int, val rewards: Int, val events: Int) : SettingsMessage
    data class ImportFailed(val error: ImportError) : SettingsMessage
}
