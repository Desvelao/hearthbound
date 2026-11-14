package app.habitrpg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.habitrpg.data.repo.GameRepository
import app.habitrpg.data.repo.Settings
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.domain.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(
    private val game: GameRepository,
    settings: SettingsRepository,
) : ViewModel() {
    /** `null` until the first read, so the app can wait instead of flashing onboarding for a moment. */
    val settings: StateFlow<Settings?> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Runs the day rollover. Safe to call on every start: it only acts when a new day has begun. */
    suspend fun rollover() {
        game.dispatch(Event.Rollover)
    }
}
