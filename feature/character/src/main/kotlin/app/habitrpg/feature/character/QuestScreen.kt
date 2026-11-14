package app.habitrpg.feature.character

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.habitrpg.data.Names
import app.habitrpg.domain.Boss
import app.habitrpg.domain.QuestCatalog
import app.habitrpg.domain.QuestOutcome

/** Timed boss quests: pick a boss, finish tasks to hurt it, and beat it before its last day. */
@Composable
fun QuestScreen(modifier: Modifier = Modifier, viewModel: QuestViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loaded) return
    QuestContent(state, viewModel::start, viewModel::dismiss, modifier)
}

/** A boss's name in the player's language. */
@Composable
internal fun bossName(id: String?): String = Names.boss(id)?.let { stringResource(it) } ?: stringResource(R.string.character_quest_unknown_boss)

@Composable
internal fun QuestContent(state: QuestUiState, onStart: (String) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.questsOn) {
            item { Text(stringResource(R.string.character_quest_off), style = MaterialTheme.typography.bodyLarge) }
        }
        val quest = state.quest
        if (quest != null) {
            item { ActiveQuest(state, onDismiss) }
        } else if (state.questsOn) {
            item {
                Text(
                    stringResource(R.string.character_quest_intro),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(QuestCatalog.bosses, key = { it.id }) { boss -> BossOffer(boss, onStart) }
        }
        if (state.history.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.character_quest_past), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            items(state.history, key = { it.id }) { h ->
                Text(
                    stringResource(
                        if (h.outcome == QuestOutcome.WON.name) R.string.character_quest_history_won else R.string.character_quest_history_escaped,
                        bossName(h.bossId), h.endDate,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ActiveQuest(state: QuestUiState, onDismiss: () -> Unit) {
    val quest = state.quest ?: return
    val boss = QuestCatalog.byId(quest.bossId) ?: return
    val name = bossName(boss.id)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BossSprites.drawableFor(boss.id)?.let { PixelSprite(it, Modifier.size(96.dp), description = name) }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text(
                        when (quest.outcome) {
                            QuestOutcome.ACTIVE ->
                                if (state.daysLeft == 1) stringResource(R.string.character_quest_last_day)
                                else pluralStringResource(R.plurals.character_quest_days_left, state.daysLeft, state.daysLeft)
                            QuestOutcome.WON -> stringResource(R.string.character_quest_won, boss.rewardXp, boss.rewardGold)
                            QuestOutcome.ESCAPED -> stringResource(R.string.character_quest_escaped)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            StatBar(stringResource(R.string.character_stat_boss), quest.hp, boss.hp, HealthColor)
            if (quest.outcome != QuestOutcome.ACTIVE) {
                Button(onClick = onDismiss, Modifier.fillMaxWidth()) { Text(stringResource(R.string.character_done)) }
            }
        }
    }
}

@Composable
private fun BossOffer(boss: Boss, onStart: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BossSprites.drawableFor(boss.id)?.let { PixelSprite(it, Modifier.size(64.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(bossName(boss.id), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.character_boss_stats, boss.hp, boss.days), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.character_boss_reward, boss.rewardXp, boss.rewardGold),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = { onStart(boss.id) }) { Text(stringResource(R.string.character_boss_fight)) }
        }
    }
}
