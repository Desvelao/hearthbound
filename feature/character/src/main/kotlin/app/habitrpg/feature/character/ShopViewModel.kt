package app.habitrpg.feature.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.habitrpg.data.repo.GameRepository
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.data.repo.ShopRepository
import app.habitrpg.domain.Slot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShopState(
    val loaded: Boolean = false,
    val gold: Int = 0,
    val owned: Set<String> = emptySet(),
    val equipped: Map<Slot, String> = emptyMap(),
    /** False when the "gear bonuses" setting is off: gear is then only for show. */
    val bonusesOn: Boolean = true,
    val hair: String? = null,
    val body: String? = null,
)

@HiltViewModel
class ShopViewModel @Inject constructor(
    game: GameRepository,
    private val shop: ShopRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<ShopState> = combine(
        game.observeCharacter(), shop.observeOwned(), shop.observeEquipped(), settings.settings,
    ) { c, owned, worn, s -> ShopState(true, c.gold, owned, worn, s.gearBonuses, s.hair, s.body) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShopState())

    // Buying announces itself through PlayFeedback, so the screen needs no result.
    fun buy(id: String) { viewModelScope.launch { shop.buy(id) } }
    fun equip(id: String) { viewModelScope.launch { shop.equip(id) } }
    fun unequip(slot: Slot) { viewModelScope.launch { shop.unequip(slot) } }
}
