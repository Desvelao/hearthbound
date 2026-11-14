package app.habitrpg.feature.character

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.habitrpg.data.Names
import app.habitrpg.domain.Classes
import app.habitrpg.domain.Constants
import app.habitrpg.domain.HeroClass
import app.habitrpg.domain.SkillEffect
import app.habitrpg.domain.Stat

/** What the class card can do. [NONE] keeps previews and tests simple. */
data class ClassActions(
    val choose: (String) -> Unit,
    val spend: (Stat) -> Unit,
    val cast: (String) -> Unit,
    val respec: () -> Unit,
) {
    companion object { val NONE = ClassActions({}, {}, {}, {}) }
}

@Composable
private fun className(id: String): String = Names.heroClass(id)?.let { stringResource(it) } ?: id

@Composable
private fun HeroClass.perkText(): String =
    Names.perk(perk)?.let { stringResource(it.res, it.value) } ?: ""

/** Class choice at the unlock level, then stat points and skills. Hidden entirely while the switch is off. */
@Composable
internal fun ClassSection(state: HeroUiState, actions: ClassActions, modifier: Modifier = Modifier) {
    val c = state.character
    val cls = Classes.byId(c.classId)
    if (cls == null && c.level < Constants.CLASS_UNLOCK_LEVEL) {
        Text(
            stringResource(R.string.character_class_locked, Constants.CLASS_UNLOCK_LEVEL),
            modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (cls == null) {
                Text(stringResource(R.string.character_class_choose_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Classes.all.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(className(option.id), style = MaterialTheme.typography.titleSmall)
                            val skills = option.skills.map { s -> Names.skill(s.id)?.let { stringResource(it) } ?: s.id }.joinToString(", ")
                            Text(
                                stringResource(R.string.character_class_line, option.perkText(), skills),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = { actions.choose(option.id) }) { Text(stringResource(R.string.character_class_choose)) }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.character_class_line, className(cls.id), cls.perkText()),
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() },
                )
                val free = Classes.freePoints(c)
                Text(
                    if (free > 0) pluralStringResource(R.plurals.character_points_free, free, free) else stringResource(R.string.character_points_none),
                    style = MaterialTheme.typography.bodyMedium,
                )
                StatRow(
                    stringResource(R.string.character_stat_might, c.statMight),
                    stringResource(R.string.character_stat_might_hint, Constants.MIGHT_BOSS_DAMAGE_PCT), free > 0,
                ) { actions.spend(Stat.MIGHT) }
                StatRow(
                    stringResource(R.string.character_stat_mind, c.statMind),
                    stringResource(R.string.character_stat_mind_hint, Constants.MIND_XP_PCT, Constants.MANA_PER_MIND), free > 0,
                ) { actions.spend(Stat.MIND) }
                StatRow(
                    stringResource(R.string.character_stat_vigor, c.statVigor),
                    stringResource(R.string.character_stat_vigor_hint, Constants.VIGOR_DAMAGE_REDUCTION_PCT), free > 0,
                ) { actions.spend(Stat.VIGOR) }
                Text(stringResource(R.string.character_skills), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                cls.skills.forEach { skill ->
                    val needsBoss = skill.effect is SkillEffect.BossDamage
                    val enabled = c.mana >= skill.cost && (!needsBoss || state.bossActive)
                    val effect = Names.effect(skill.effect)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.character_skill_title, Names.skill(skill.id)?.let { stringResource(it) } ?: skill.id, skill.cost),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(effect.res, effect.value) +
                                    if (needsBoss && !state.bossActive) " " + stringResource(R.string.character_skill_needs_boss) else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { actions.cast(skill.id) }, enabled = enabled) { Text(stringResource(R.string.character_skill_cast)) }
                    }
                }
                TextButton(onClick = actions.respec, enabled = c.gold >= Constants.RESPEC_COST) {
                    Text(stringResource(R.string.character_class_reset, Constants.RESPEC_COST))
                }
            }
        }
    }
}

@Composable
private fun StatRow(title: String, effect: String, canSpend: Boolean, onSpend: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(effect, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onSpend, enabled = canSpend) { Text(stringResource(R.string.character_stat_add)) }
    }
}
