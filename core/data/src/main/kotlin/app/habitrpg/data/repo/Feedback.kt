package app.habitrpg.data.repo

import app.habitrpg.domain.Change
import app.habitrpg.domain.Character
import app.habitrpg.domain.Milestone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** What the player should be told after something changed. The UI turns these into snackbars and dialogs. */
sealed interface Feedback {
    data class Scored(
        val title: String?,
        val xp: Int,
        val gold: Int,
        val health: Int,
        /** Non-null when the user can take it back (a single plus, minus or tick). */
        val undo: Change?,
        val milestones: List<Milestone>,
        val characterAfter: Character,
        val fromRollover: Boolean,
        /** Boss hp removed by this score, zero when none. */
        val bossDamage: Int = 0,
    ) : Feedback

    /** Something was bought: a reward the player wrote ([title]) or a shop item ([itemId], named by `Names.item`). */
    data class Purchased(val title: String?, val itemId: String?, val cost: Int) : Feedback

    /** A class skill was cast. */
    data class Cast(val skillId: String, val milestones: List<Milestone>, val level: Int) : Feedback
}

/** Hot stream with no replay: feedback only matters to a screen that is on display. */
@Singleton
class PlayFeedback @Inject constructor() {
    private val _events = MutableSharedFlow<Feedback>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<Feedback> = _events.asSharedFlow()

    private val _rollover = MutableStateFlow<Feedback.Scored?>(null)

    /**
     * The result of a new-day rollover. Unlike ticks it usually happens while the app is starting, before any
     * screen is collecting, so it is held until a screen shows it and calls [consumeRollover].
     */
    val rollover: StateFlow<Feedback.Scored?> = _rollover.asStateFlow()

    fun consumeRollover() { _rollover.value = null }

    fun emit(feedback: Feedback) {
        if (feedback is Feedback.Scored && feedback.fromRollover) {
            val earlier = _rollover.value
            // Two rollovers before anyone looked: add them up so no dialog is lost.
            _rollover.value = if (earlier == null) feedback else feedback.copy(
                xp = earlier.xp + feedback.xp, gold = earlier.gold + feedback.gold, health = earlier.health + feedback.health,
                milestones = earlier.milestones + feedback.milestones, bossDamage = earlier.bossDamage + feedback.bossDamage,
            )
        } else {
            _events.tryEmit(feedback)
        }
    }
}
