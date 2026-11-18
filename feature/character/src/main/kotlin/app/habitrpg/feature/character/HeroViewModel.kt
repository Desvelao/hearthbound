package app.habitrpg.feature.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.habitrpg.data.repo.DEFAULT_BODY
import app.habitrpg.data.repo.DEFAULT_HAIR
import app.habitrpg.data.repo.DayStats
import app.habitrpg.data.repo.GameRepository
import app.habitrpg.data.repo.HistoryData
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.data.repo.ShopRepository
import app.habitrpg.data.repo.StatsRepository
import app.habitrpg.data.repo.TaskWeek
import app.habitrpg.domain.Character
import app.habitrpg.domain.Event
import app.habitrpg.domain.Slot
import app.habitrpg.domain.Stat
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HeroUiState(
    val loaded: Boolean = false,
    val character: Character = Character(),
    val hair: String = DEFAULT_HAIR,
    val body: String = DEFAULT_BODY,
    val history: HistoryData = HistoryData(),
    val goldSpent: Int = 0,
    val week: List<DayStats> = emptyList(),
    val taskWeek: List<TaskWeek> = emptyList(),
    val owned: Set<String> = emptySet(),
    val equipped: Map<Slot, String> = emptyMap(),
    val classesOn: Boolean = true,
    val paused: Boolean = false,
    val bossActive: Boolean = false,
)

@HiltViewModel
class HeroViewModel @Inject constructor(
    private val game: GameRepository,
    stats: StatsRepository,
    private val settings: SettingsRepository,
    private val shop: ShopRepository,
) : ViewModel() {

    private val weekData = combine(stats.observeWeek(), stats.observeTaskWeek()) { week, tasks -> week to tasks }

    private val gear = combine(shop.observeOwned(), shop.observeEquipped()) { owned, worn -> owned to worn }
    private val extras = combine(weekData, gear) { week, g -> week to g }

    val state: StateFlow<HeroUiState> = combine(
        game.observeCharacter(), settings.settings, stats.observeHistory(), stats.observeGoldSpent(), extras,
        game.observeQuest(),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val (weekAndTasks, owned) = values[4] as Pair<Pair<List<DayStats>, List<TaskWeek>>, Pair<Set<String>, Map<Slot, String>>>
        val s = values[1] as app.habitrpg.data.repo.Settings
        val quest = values[5] as app.habitrpg.domain.Quest?
        HeroUiState(
            true, values[0] as Character, s.hair, s.body, values[2] as HistoryData, values[3] as Int,
            weekAndTasks.first, weekAndTasks.second, owned.first, owned.second,
            classesOn = s.classes,
            paused = s.paused,
            bossActive = s.quests && quest?.outcome == app.habitrpg.domain.QuestOutcome.ACTIVE,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeroUiState())

    fun resume() { viewModelScope.launch { settings.setPaused(false) } }
    fun setHair(id: String) { viewModelScope.launch { settings.setHair(id) } }
    fun setBody(id: String) { viewModelScope.launch { settings.setBody(id) } }
    fun equip(id: String) { viewModelScope.launch { shop.equip(id) } }
    fun unequip(slot: Slot) { viewModelScope.launch { shop.unequip(slot) } }

    // Class actions. Skills announce themselves through PlayFeedback.
    fun chooseClass(id: String) { viewModelScope.launch { game.dispatch(Event.ChooseClass(id)) } }
    fun spendStat(stat: Stat) { viewModelScope.launch { game.dispatch(Event.SpendStat(stat)) } }
    fun cast(skillId: String) { viewModelScope.launch { game.dispatch(Event.CastSkill(skillId)) } }
    fun respec() { viewModelScope.launch { game.dispatch(Event.Respec) } }
}
