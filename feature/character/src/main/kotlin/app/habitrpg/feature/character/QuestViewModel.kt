package app.habitrpg.feature.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.habitrpg.data.db.QuestHistoryEntity
import app.habitrpg.data.repo.GameRepository
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.domain.Event
import app.habitrpg.domain.GameDay
import app.habitrpg.domain.Quest
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QuestUiState(
    val loaded: Boolean = false,
    val questsOn: Boolean = true,
    val quest: Quest? = null,
    /** Game days left including today; zero for a finished quest. */
    val daysLeft: Int = 0,
    val history: List<QuestHistoryEntity> = emptyList(),
)

@HiltViewModel
class QuestViewModel @Inject constructor(
    private val game: GameRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<QuestUiState> = combine(
        game.observeQuest(), game.observeQuestHistory(), settings.settings,
    ) { quest, history, s ->
        val today = GameDay.today(clock, s.dayStartHour)
        val left = quest?.let { ChronoUnit.DAYS.between(today, it.deadlineDate).toInt() + 1 }?.coerceAtLeast(0) ?: 0
        QuestUiState(true, s.quests, quest, left, history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuestUiState())

    fun start(bossId: String) { viewModelScope.launch { game.dispatch(Event.StartQuest(bossId)) } }
    fun dismiss() { viewModelScope.launch { game.dispatch(Event.DismissQuest) } }
}
