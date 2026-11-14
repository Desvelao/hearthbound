package app.habitrpg.feature.rewards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.habitrpg.data.db.RewardEntity
import app.habitrpg.data.repo.GameRepository
import app.habitrpg.data.repo.RewardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RewardsState(val rewards: List<RewardEntity>? = null, val gold: Int = 0)

@HiltViewModel
class RewardsViewModel @Inject constructor(
    private val repo: RewardRepository,
    game: GameRepository,
) : ViewModel() {
    val state: StateFlow<RewardsState> =
        combine(repo.observe(), game.observeCharacter()) { r, c -> RewardsState(r, c.gold) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RewardsState())

    fun buy(id: String) { viewModelScope.launch { repo.buy(id) } }

    fun save(id: String?, title: String, cost: Int, notes: String) {
        if (title.isBlank()) return
        viewModelScope.launch { repo.save(id, title, cost, notes) }
    }

    fun delete(id: String) { viewModelScope.launch { repo.delete(id) } }
}
