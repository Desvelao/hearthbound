package app.habitrpg.feature.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.habitrpg.data.repo.GameRepository
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.data.repo.ShopRepository
import app.habitrpg.domain.Character
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the always-visible header needs: the character and how it looks, including worn gear. */
@HiltViewModel
class CharacterViewModel @Inject constructor(game: GameRepository, settings: SettingsRepository, shop: ShopRepository) : ViewModel() {
    val character: StateFlow<Character> =
        game.observeCharacter().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Character())
    val look: StateFlow<AvatarLook> =
        combine(settings.settings, shop.observeEquipped()) { s, worn -> AvatarLook.forHair(s.hair, s.body, worn) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AvatarLook())
    private val settingsRepo = settings
    /** Pause mode on: the header shows a banner that can resume the game. */
    val paused: StateFlow<Boolean> =
        settings.settings.map { it.paused }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun resume() { viewModelScope.launch { settingsRepo.setPaused(false) } }
    /** The mana bar only shows while the classes switch is on. */
    val manaOn: StateFlow<Boolean> =
        settings.settings.map { it.classes }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
}
