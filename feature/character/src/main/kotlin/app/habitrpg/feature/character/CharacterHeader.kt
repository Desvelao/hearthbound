package app.habitrpg.feature.character

import androidx.compose.ui.res.stringResource
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.habitrpg.domain.Character
import app.habitrpg.domain.Classes
import app.habitrpg.domain.Constants

/** Always visible above the tabs, so every tick shows its effect on the bars at once. */
@Composable
fun CharacterHeader(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    viewModel: CharacterViewModel = hiltViewModel(),
) {
    val c by viewModel.character.collectAsStateWithLifecycle()
    val look by viewModel.look.collectAsStateWithLifecycle()
    val manaOn by viewModel.manaOn.collectAsStateWithLifecycle()
    val paused by viewModel.paused.collectAsStateWithLifecycle()
    CharacterHeaderContent(c, onClick, modifier, trailing, look, showMana = manaOn && c.classId != null, paused = paused, onResume = viewModel::resume)
}

@Composable
fun CharacterHeaderContent(
    c: Character,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    look: AvatarLook = AvatarLook(),
    showMana: Boolean = false,
    paused: Boolean = false,
    onResume: () -> Unit = {},
) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
      Column(Modifier.statusBarsPadding()) {
        Row(
            Modifier.clickable(onClickLabel = "Open character", onClick = onClick).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(Modifier.size(56.dp), look)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.character_level, c.level), style = MaterialTheme.typography.titleMedium)
                    val goldLabel = stringResource(R.string.character_gold_cd, c.gold)
                    Row(
                        Modifier.semantics(mergeDescendants = true) { contentDescription = goldLabel },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        PixelSprite(R.drawable.sprite_icon_gold, Modifier.size(24.dp))
                        Text("${c.gold}", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleMedium)
                    }
                }
                StatBar(stringResource(R.string.character_stat_health), c.health, Constants.MAX_HEALTH, HealthColor, icon = R.drawable.sprite_icon_health)
                StatBar(stringResource(R.string.character_stat_xp), c.xp, Constants.xpToNextLevel(c.level), XpColor, icon = R.drawable.sprite_icon_xp)
                if (showMana) StatBar(stringResource(R.string.character_stat_mana), c.mana, Classes.manaMax(c), ManaColor, icon = R.drawable.sprite_icon_mana)
            }
            trailing()
        }
        if (paused) PausedBanner(onResume, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp))
      }
    }
}

val HealthColor = Color(0xFFD64545)
val XpColor = Color(0xFF3F8CE0)
val GoldColor = Color(0xFFB8860B)
val ManaColor = Color(0xFF7B5CD6)

/** A labelled bar with its numbers in text, so colour is never the only cue. */
@Composable
fun StatBar(
    label: String,
    value: Int,
    max: Int,
    color: Color,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    val barLabel = stringResource(R.string.character_bar_cd, label, value, max)
    val target = if (max <= 0) 0f else (value.toFloat() / max).coerceIn(0f, 1f)
    val fraction by animateFloatAsState(target, label = "bar-$label")
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = barLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) PixelSprite(icon, Modifier.size(20.dp))
        else Text(label, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelSmall)
        Box(
            Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(color))
        }
        Text("$value/$max", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelSmall)
    }
}
