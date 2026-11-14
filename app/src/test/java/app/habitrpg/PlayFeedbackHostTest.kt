package app.habitrpg

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.habitrpg.data.repo.Feedback
import app.habitrpg.domain.Change
import app.habitrpg.domain.Character
import app.habitrpg.domain.Engine
import app.habitrpg.domain.Event
import app.habitrpg.domain.GameState
import app.habitrpg.domain.Milestone
import app.habitrpg.domain.Task
import app.habitrpg.domain.TaskType
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayFeedbackHostTest {
    @get:Rule val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-03-02T12:00:00Z"), ZoneOffset.UTC)
    private val events = MutableSharedFlow<Feedback>(extraBufferCapacity = 8)

    private fun scored(
        title: String? = "Walk", xp: Int = 0, gold: Int = 0, health: Int = 0, undo: Change? = null,
        milestones: List<Milestone> = emptyList(), level: Int = 1, rollover: Boolean = false,
    ) = Feedback.Scored(title, xp, gold, health, undo, milestones, Character(level = level), rollover)

    private fun aChange(): Change {
        val state = GameState(tasks = mapOf("h" to Task("h", TaskType.HABIT)))
        return Engine.reduce(state, Event.HabitPlus("h"), clock).changes.single()
    }

    private fun show(rollover: StateFlow<Feedback.Scored?> = MutableStateFlow(null), consume: () -> Unit = {}, undo: (Change) -> Unit = {}) {
        compose.setContent {
            val fb = rememberPlayFeedback(events, rollover, consume, undo)
            Scaffold(snackbarHost = { SnackbarHost(fb.snackbar) }) { padding -> androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(padding)) }
            PlayDialogs(fb)
        }
    }

    @Test fun aTickShowsItsRewardsInASnackbar() {
        show()
        events.tryEmit(scored(xp = 10, gold = 4))
        compose.waitUntil { compose.onAllNodesWithTextCount("Walk: +10 XP, +4 gold") > 0 }
    }

    @Test fun undoIsOfferedAndHandsTheChangeBack() {
        var undone: Change? = null
        val change = aChange()
        show(undo = { undone = it })
        events.tryEmit(scored(xp = 10, undo = change))
        compose.waitUntil { compose.onAllNodesWithTextCount("Undo") > 0 }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil { undone != null }
        assertEquals(change, undone)
    }

    @Test fun noUndoButtonWhenThereIsNothingToTakeBack() {
        show()
        events.tryEmit(scored(health = -2, title = null, rollover = true))
        compose.waitUntil { compose.onAllNodesWithTextCount("New day: missed dailies: -2 health") > 0 }
        assertEquals(0, compose.onAllNodesWithTextCount("Undo"))
    }

    /** Regression: this result is produced while the app starts, before any screen listens, and was being dropped. */
    @Test fun aNewDayResultHeldBeforeTheScreenExistedIsShownAndConsumedOnce() {
        val held = MutableStateFlow<Feedback.Scored?>(scored(title = "Stretch", health = -2, rollover = true))
        var consumed = 0
        show(rollover = held, consume = { consumed++; held.value = null })
        compose.waitUntil { compose.onAllNodesWithTextCount("Stretch: -2 health") > 0 }
        assertEquals(1, consumed)
        assertNull(held.value)
    }

    @Test fun aHeldNewDayResultWithMilestonesAlsoShowsItsDialog() {
        val held = MutableStateFlow<Feedback.Scored?>(
            scored(title = null, health = -4, milestones = listOf(Milestone.HEALTH_ZERO), rollover = true),
        )
        show(rollover = held, consume = { held.value = null })
        compose.waitUntil { compose.onAllNodesWithTextCount("You ran out of health") > 0 }
        compose.onNodeWithText("OK").performClick()
        compose.waitUntil { compose.onAllNodesWithTextCount("You ran out of health") == 0 }
    }

    @Test fun levelUpDialogNamesTheNewLevelAndDialogsQueueOneAtATime() {
        show()
        events.tryEmit(scored(xp = 20, milestones = listOf(Milestone.LEVEL_UP, Milestone.HEALTH_ZERO), level = 4))
        compose.waitUntil { compose.onAllNodesWithTextCount("Level up!") > 0 }
        compose.onNodeWithText("You reached level 4. Your health is fully restored.").assertExists()
        assertEquals(0, compose.onAllNodesWithTextCount("You ran out of health")) // second one waits its turn
        compose.onNodeWithText("OK").performClick()
        compose.waitUntil { compose.onAllNodesWithTextCount("You ran out of health") > 0 }
    }

    @Test fun purchasesAreAnnounced() {
        show()
        events.tryEmit(Feedback.Purchased("Coffee", null, 4))
        compose.waitUntil { compose.onAllNodesWithTextCount("Bought Coffee for 4 gold") > 0 }
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
    onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
