package app.habitrpg

import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.habitrpg.domain.TaskType
import app.habitrpg.feature.character.CharacterHeader
import app.habitrpg.feature.character.HeroScreen
import app.habitrpg.feature.character.QuestScreen
import app.habitrpg.feature.character.ShopScreen
import app.habitrpg.feature.rewards.RewardsScreen
import app.habitrpg.feature.tasks.TaskListScreen
import app.habitrpg.feature.tasks.today.TodayScreen
import kotlin.math.roundToInt

/** The four places the bottom bar leads to. */
private enum class Destination(@StringRes val labelRes: Int, val icon: ImageVector) {
    TODAY(R.string.app_nav_today, Icons.Default.DateRange),
    TASKS(R.string.app_nav_tasks, Icons.AutoMirrored.Filled.List),
    REWARDS(R.string.app_nav_rewards, Icons.Default.ShoppingCart),
    HERO(R.string.app_nav_hero, Icons.Default.Person),
}

@StringRes
private fun TaskType.tabLabelRes() = when (this) {
    TaskType.HABIT -> R.string.app_tab_habits
    TaskType.DAILY -> R.string.app_tab_dailies
    TaskType.TODO -> R.string.app_tab_todos
}

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onAdd: (TaskType) -> Unit,
    onEdit: (TaskType, String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    var destination by rememberSaveable { mutableStateOf(Destination.TODAY) }
    var taskType by rememberSaveable { mutableStateOf(TaskType.HABIT) }
    // Rewards sub-tabs: 0 Rewards, 1 Shop, 2 Quests.
    var rewardsTab by rememberSaveable { mutableStateOf(0) }
    val feedback = rememberPlayFeedback(
        feedback = viewModel.feedback,
        rollover = viewModel.rolloverFeedback,
        consumeRollover = viewModel::consumeRollover,
        onUndo = viewModel::undo,
    )
    val onHero = destination == Destination.HERO

    Scaffold(
        // The character strip shows what every tick does to the bars. The Hero page has the whole character instead.
        topBar = { if (!onHero) CharacterHeader(onClick = { destination = Destination.HERO }) },
        snackbarHost = { SnackbarHost(feedback.snackbar) },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { d ->
                    NavigationBarItem(
                        selected = destination == d,
                        onClick = { destination = d },
                        icon = { Icon(d.icon, contentDescription = null) },
                        label = { Text(stringResource(d.labelRes)) },
                    )
                }
            }
        },
        // The Hero scene paints under the status bar; every other page sits below it.
        contentWindowInsets = if (onHero) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (destination) {
                Destination.TODAY -> TodayScreen(onEdit = onEdit)
                Destination.TASKS -> Column {
                    PrimaryTabRow(selectedTabIndex = taskType.ordinal) {
                        TaskType.entries.forEach { type ->
                            Tab(selected = taskType == type, onClick = { taskType = type }, text = { Text(stringResource(type.tabLabelRes())) })
                        }
                    }
                    TaskListScreen(type = taskType, onAdd = { onAdd(taskType) }, onEdit = { onEdit(taskType, it) })
                }
                Destination.REWARDS -> Column {
                    PrimaryTabRow(selectedTabIndex = rewardsTab) {
                        Tab(selected = rewardsTab == 0, onClick = { rewardsTab = 0 }, text = { Text(stringResource(R.string.app_nav_rewards)) })
                        Tab(selected = rewardsTab == 1, onClick = { rewardsTab = 1 }, text = { Text(stringResource(R.string.app_tab_shop)) })
                        Tab(selected = rewardsTab == 2, onClick = { rewardsTab = 2 }, text = { Text(stringResource(R.string.app_tab_quests)) })
                    }
                    when (rewardsTab) {
                        1 -> ShopScreen()
                        2 -> QuestScreen()
                        else -> RewardsScreen()
                    }
                }
                Destination.HERO -> HeroScreen(onOpenSettings = onOpenSettings, onOpenHistory = onOpenHistory)
            }
            feedback.gain?.let { g -> key(g.id) { FloatingGain(g, Modifier.align(Alignment.TopCenter)) { if (feedback.gain == g) feedback.gain = null } } }
        }
    }

    PlayDialogs(feedback)
}

/** "+15 XP +6 gold" drifting up and fading. The snackbar carries the same text for screen readers. */
@Composable
internal fun FloatingGain(g: Gain, modifier: Modifier, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(900, easing = LinearEasing))
        onDone()
    }
    Text(
        listOfNotNull(
            stringResource(R.string.app_gain_xp, g.xp).takeIf { g.xp > 0 },
            stringResource(R.string.app_gain_gold, g.gold).takeIf { g.gold > 0 },
        ).joinToString("  "),
        modifier.padding(top = 24.dp)
            // Lambda forms read the animation during layout and drawing, not composition, so it does not recompose every frame.
            .offset { IntOffset(0, (-40.dp.toPx() * progress.value).roundToInt()) }
            .graphicsLayer { alpha = 1f - progress.value }
            .clearAndSetSemantics {},
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}
