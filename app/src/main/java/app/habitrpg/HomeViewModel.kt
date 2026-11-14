package app.habitrpg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.habitrpg.data.repo.Feedback
import app.habitrpg.data.repo.GameRepository
import app.habitrpg.data.repo.PlayFeedback
import app.habitrpg.domain.Change
import app.habitrpg.domain.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    feedback: PlayFeedback,
    private val game: GameRepository,
) : ViewModel() {
    private val playFeedback = feedback
    val feedback: SharedFlow<Feedback> = feedback.events

    /** The new-day result, held until this screen shows it. */
    val rolloverFeedback: StateFlow<Feedback.Scored?> = feedback.rollover
    fun consumeRollover() = playFeedback.consumeRollover()

    fun undo(change: Change) {
        viewModelScope.launch { game.dispatch(Event.Undo(change)) }
    }
}
