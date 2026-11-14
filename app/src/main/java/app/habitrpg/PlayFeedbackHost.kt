package app.habitrpg

import app.habitrpg.data.Names
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import android.content.res.Resources
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.habitrpg.data.repo.Feedback
import app.habitrpg.domain.Change
import app.habitrpg.domain.Milestone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal data class Gain(val id: Long, val xp: Int, val gold: Int)

internal sealed interface PlayDialog {
    data class LevelUp(val level: Int) : PlayDialog
    data object HealthZero : PlayDialog
    data object QuestWon : PlayDialog
    data object QuestEscaped : PlayDialog
}

/** What the player is currently being told: the snackbar, the floating gain and any queued dialogs. */
@Stable
class PlayFeedbackState internal constructor(val snackbar: SnackbarHostState, private val resourcesProvider: () -> Resources) {
    /** The strings of the language in use right now, read when a message is shown rather than when the state is made. */
    internal val resources: Resources get() = resourcesProvider()
    internal var gain by mutableStateOf<Gain?>(null)
    internal var dialogs by mutableStateOf(listOf<PlayDialog>())
    internal fun dismissDialog() { dialogs = dialogs.drop(1) }
}

/**
 * Shows [feedback] as it arrives, and the new-day result held in [rollover] as soon as there is a screen to show it.
 * The held result is shown from its own coroutine: consuming it changes the effect's key, which would otherwise
 * cancel the effect before the snackbar and dialogs appear (that bug dropped the new-day message on a real phone).
 */
@Composable
fun rememberPlayFeedback(
    feedback: Flow<Feedback>,
    rollover: StateFlow<Feedback.Scored?>,
    consumeRollover: () -> Unit,
    onUndo: (Change) -> Unit,
): PlayFeedbackState {
    val context by rememberUpdatedState(LocalContext.current)
    val state = remember { PlayFeedbackState(SnackbarHostState()) { context.resources } }
    val undo by rememberUpdatedState(onUndo)
    val consume by rememberUpdatedState(consumeRollover)
    val scope = rememberCoroutineScope()

    LaunchedEffect(feedback) {
        // collectLatest: a new tick replaces the previous snackbar instead of queueing behind it.
        feedback.collectLatest { state.show(it) { c -> undo(c) } }
    }
    val pending by rollover.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let { held ->
            consume()
            scope.launch { state.show(held) { c -> undo(c) } }
        }
    }
    return state
}

internal suspend fun PlayFeedbackState.show(f: Feedback, onUndo: (Change) -> Unit) {
    when (f) {
        is Feedback.Scored -> {
            if (f.xp > 0 || f.gold > 0) gain = Gain(System.nanoTime(), f.xp, f.gold)
            dialogs = dialogs + f.milestones.map {
                when (it) {
                    Milestone.LEVEL_UP -> PlayDialog.LevelUp(f.characterAfter.level)
                    Milestone.HEALTH_ZERO -> PlayDialog.HealthZero
                    Milestone.QUEST_WON -> PlayDialog.QuestWon
                    Milestone.QUEST_ESCAPED -> PlayDialog.QuestEscaped
                }
            }
            val result = snackbar.showSnackbar(
                message = scoredMessage(f, resources),
                actionLabel = if (f.undo != null) resources.getString(R.string.app_undo) else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) f.undo?.let(onUndo)
        }
        is Feedback.Purchased -> {
            val name = f.title ?: Names.item(f.itemId)?.let(resources::getString) ?: ""
            snackbar.showSnackbar(resources.getString(R.string.app_bought, name, f.cost), duration = SnackbarDuration.Short)
        }
        is Feedback.Cast -> {
            if (Milestone.QUEST_WON in f.milestones) dialogs = dialogs + PlayDialog.QuestWon
            if (Milestone.LEVEL_UP in f.milestones) dialogs = dialogs + PlayDialog.LevelUp(f.level)
            val skill = Names.skill(f.skillId)?.let(resources::getString) ?: ""
            snackbar.showSnackbar(resources.getString(R.string.app_cast, skill), duration = SnackbarDuration.Short)
        }
    }
}

internal fun scoredMessage(f: Feedback.Scored, res: Resources): String {
    val parts = listOfNotNull(
        res.getString(R.string.app_msg_boss_damage, f.bossDamage).takeIf { f.bossDamage > 0 },
        res.getString(R.string.app_gain_xp, f.xp).takeIf { f.xp > 0 },
        res.getString(R.string.app_gain_gold, f.gold).takeIf { f.gold > 0 },
        res.getString(R.string.app_msg_health, f.health).takeIf { f.health < 0 },
    ).joinToString(", ")
    val what = f.title ?: res.getString(if (f.fromRollover) R.string.app_msg_new_day else R.string.app_msg_update)
    return if (parts.isEmpty()) what else res.getString(R.string.app_msg_with_parts, what, parts)
}

/** The level-up and zero-health dialogs, one at a time. */
@Composable
fun PlayDialogs(state: PlayFeedbackState) {
    val d = state.dialogs.firstOrNull() ?: return
    AlertDialog(
        onDismissRequest = state::dismissDialog,
        title = {
            Text(
                stringResource(
                    when (d) {
                        is PlayDialog.LevelUp -> R.string.app_dialog_levelup_title
                        PlayDialog.HealthZero -> R.string.app_dialog_healthzero_title
                        PlayDialog.QuestWon -> R.string.app_dialog_questwon_title
                        PlayDialog.QuestEscaped -> R.string.app_dialog_questescaped_title
                    },
                ),
            )
        },
        text = {
            Text(
                when (d) {
                    is PlayDialog.LevelUp -> stringResource(R.string.app_dialog_levelup_text, d.level)
                    PlayDialog.HealthZero -> stringResource(R.string.app_dialog_healthzero_text)
                    PlayDialog.QuestWon -> stringResource(R.string.app_dialog_questwon_text)
                    PlayDialog.QuestEscaped -> stringResource(R.string.app_dialog_questescaped_text)
                },
            )
        },
        confirmButton = { TextButton(onClick = state::dismissDialog) { Text(stringResource(R.string.app_ok)) } },
    )
}
