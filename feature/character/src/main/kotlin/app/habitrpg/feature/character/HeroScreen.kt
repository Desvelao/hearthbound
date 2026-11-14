package app.habitrpg.feature.character

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.habitrpg.data.repo.DayStats
import app.habitrpg.data.repo.TaskWeek
import app.habitrpg.domain.Classes
import app.habitrpg.domain.Constants
import app.habitrpg.domain.Slot
import java.time.format.TextStyle

private val SceneHeight = 272.dp

/** The hero's page: a pixel scene, the bars, a few numbers worth looking at, and what the week looked like. */
@Composable
fun HeroScreen(onOpenSettings: () -> Unit, onOpenHistory: () -> Unit, modifier: Modifier = Modifier, viewModel: HeroViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var choosingLook by remember { mutableStateOf(false) }
    HeroContent(
        state, onOpenSettings, onOpenHistory, onChangeLook = { choosingLook = true }, modifier,
        classActions = ClassActions(viewModel::chooseClass, viewModel::spendStat, viewModel::cast, viewModel::respec),
    )
    if (choosingLook) {
        AlertDialog(
            onDismissRequest = { choosingLook = false },
            title = { Text(stringResource(R.string.character_look_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.character_look_character), style = MaterialTheme.typography.titleSmall)
                    BodyPicker(state.body, viewModel::setBody, hairId = state.hair)
                    Text(stringResource(R.string.character_look_hair), style = MaterialTheme.typography.titleSmall)
                    HairPicker(state.hair, viewModel::setHair, bodyId = state.body)
                    GearPicker(state.owned, state.equipped, state.hair, state.body, viewModel::equip, viewModel::unequip)
                }
            },
            confirmButton = { TextButton(onClick = { choosingLook = false }) { Text(stringResource(R.string.character_done)) } },
        )
    }
}

@Composable
internal fun HeroContent(
    state: HeroUiState,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onChangeLook: () -> Unit,
    modifier: Modifier = Modifier,
    classActions: ClassActions = ClassActions.NONE,
) {
    val c = state.character
    val h = state.history
    Column(modifier.verticalScroll(rememberScrollState())) {
        HeroScene(c.level, c.gold, AvatarLook.forHair(state.hair, state.body, state.equipped), ItemSprites.drawableFor(state.equipped[Slot.PET]), onOpenSettings)

        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(stringResource(R.string.character_hero_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text(
                    stringResource(R.string.character_hero_level_line, c.level, Constants.xpToNextLevel(c.level) - c.xp, c.level + 1),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatBar(stringResource(R.string.character_stat_health), c.health, Constants.MAX_HEALTH, HealthColor, icon = R.drawable.sprite_icon_health)
            StatBar(stringResource(R.string.character_stat_xp), c.xp, Constants.xpToNextLevel(c.level), XpColor, icon = R.drawable.sprite_icon_xp)
            if (state.classesOn && c.classId != null) {
                StatBar(stringResource(R.string.character_stat_mana), c.mana, Classes.manaMax(c), ManaColor, icon = R.drawable.sprite_icon_mana)
            }
            if (state.classesOn) ClassSection(state, classActions)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    stringResource(R.string.character_card_streak), pluralStringResource(R.plurals.character_n_days, h.currentStreak, h.currentStreak),
                    Modifier.weight(1f),
                    note = stringResource(R.string.character_card_best, pluralStringResource(R.plurals.character_n_days, h.bestStreak, h.bestStreak)),
                )
                StatCard(stringResource(R.string.character_card_week), "${h.tasksThisWeek}", Modifier.weight(1f), note = compareWithLastWeek(h.tasksThisWeek, h.tasksLastWeek))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(stringResource(R.string.character_card_gold), "${h.goldEarned}", Modifier.weight(1f), note = stringResource(R.string.character_gold_spent, state.goldSpent))
                StatCard(
                    stringResource(R.string.character_card_health_lost), "${h.weekHealthLost}", Modifier.weight(1f),
                    note = if (h.weekMissed == 0) stringResource(R.string.character_no_missed)
                    else pluralStringResource(R.plurals.character_n_missed, h.weekMissed, h.weekMissed),
                )
            }

            Text(stringResource(R.string.character_xp_week), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            WeekChart(state.week)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onChangeLook, Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.character_change_look)) }
                Button(onClick = onOpenHistory, Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.character_see_history)) }
            }

            if (state.taskWeek.isNotEmpty()) {
                Text(stringResource(R.string.character_tasks_week_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                state.taskWeek.forEach { TaskWeekRow(it, state.week) }
            }
        }
    }
}

@Composable
internal fun compareWithLastWeek(now: Int, before: Int): String = when {
    now > before -> stringResource(R.string.character_week_more, now - before)
    now < before -> stringResource(R.string.character_week_fewer, before - now)
    else -> stringResource(R.string.character_week_same)
}

// The scene is the same in light and dark: it is a picture, not a surface.
private val Sky = listOf(Color(0xFF1B2A52), Color(0xFF223463), Color(0xFF2B4175), Color(0xFF36508C))
private val Hill = Color(0xFF1F3A5F)
private val Ground = Color(0xFF2D5A47)
private val Grass = Color(0xFF4E8F6E)
private val Star = Color(0xFFFBE9B0)
private val Panel = Color(0xCC14151F)

@Composable
private fun HeroScene(level: Int, gold: Int, look: AvatarLook, pet: Int?, onOpenSettings: () -> Unit) {
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxWidth().height(SceneHeight + inset).drawBehind { drawScene() }) {
        Avatar(Modifier.size(160.dp).align(Alignment.BottomCenter).padding(bottom = 50.dp), look)
        // 80 dp is 5x the 16 px sprite, the same pixel scale as the 160 dp hero (5x of 32 px).
        pet?.let {
            PixelSprite(it, Modifier.size(80.dp).align(Alignment.BottomCenter).padding(bottom = 50.dp).offset(x = (-104).dp), description = stringResource(R.string.character_pet_cd))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = inset + 16.dp, start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.height(44.dp).background(Panel, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.character_scene_level, level), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.weight(1f))
            val goldLabel = stringResource(R.string.character_gold_cd, gold)
            Row(
                Modifier.height(44.dp).background(Panel, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp)
                    .semantics(mergeDescendants = true) { contentDescription = goldLabel },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PixelSprite(R.drawable.sprite_icon_gold, Modifier.size(24.dp))
                Text("$gold", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = onOpenSettings, Modifier.size(44.dp).background(Panel, RoundedCornerShape(12.dp))) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.character_settings_cd), tint = Color.White)
            }
        }
    }
}

/** Stepped sky bands, hills and ground: hard edges keep the pixel look at any size. */
private fun DrawScope.drawScene() {
    val dp = 1.dp.toPx()
    val band = size.height / Sky.size
    Sky.forEachIndexed { i, color -> drawRect(color, Offset(0f, i * band), Size(size.width, band + 1f)) }
    listOf(44f to 70f, 120f to 110f, 210f to 64f, 300f to 96f, 340f to 150f, 70f to 156f).forEach { (x, y) ->
        drawRect(Star, Offset(x * dp, y * dp), Size(4 * dp, 4 * dp))
    }
    fun hill(fromLeft: Boolean, inset: Float, width: Float, bottom: Float) {
        val x = if (fromLeft) inset * dp else size.width - (inset + width) * dp
        drawRect(Hill, Offset(x, size.height - (bottom + 16) * dp), Size(width * dp, 16 * dp))
    }
    hill(true, 0f, 150f, 64f); hill(true, 20f, 100f, 80f); hill(true, 40f, 56f, 96f)
    hill(false, 0f, 190f, 64f); hill(false, 24f, 130f, 80f); hill(false, 48f, 82f, 96f); hill(false, 70f, 40f, 112f)
    drawRect(Ground, Offset(0f, size.height - 64 * dp), Size(size.width, 64 * dp))
    drawRect(Grass, Offset(0f, size.height - 64 * dp), Size(size.width, 8 * dp))
}

@Composable
internal fun WeekChart(week: List<DayStats>) {
    if (week.isEmpty()) return
    val locale = LocalConfiguration.current.locales[0]
    val maxXp = week.maxOf { it.xp }.coerceAtLeast(1)
    val bar = XpColor
    val description = week.map {
        stringResource(R.string.character_day_xp, it.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale), it.xp)
    }.joinToString(", ")
    val chartLabel = stringResource(R.string.character_xp_day_cd, description)
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = chartLabel }) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / week.size
            val barWidth = slot * 0.6f
            week.forEachIndexed { i, d ->
                val h = size.height * (d.xp.coerceAtLeast(0).toFloat() / maxXp)
                drawRoundRect(
                    bar, Offset(i * slot + (slot - barWidth) / 2, size.height - h.coerceAtLeast(2f)),
                    Size(barWidth, h.coerceAtLeast(2f)), CornerRadius(4f, 4f),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            week.forEach {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(it.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.labelSmall)
                    Text("${it.xp}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** One task's last seven days as tiny bars, with the total in text so the chart is never the only cue. */
@Composable
internal fun TaskWeekRow(t: TaskWeek, week: List<DayStats>) {
    val locale = LocalConfiguration.current.locales[0]
    val peak = t.perDay.max().coerceAtLeast(1)
    val bar = XpColor
    val description = t.perDay.indices.map { i ->
        val day = week.getOrNull(i)?.date?.dayOfWeek?.getDisplayName(TextStyle.FULL, locale) ?: stringResource(R.string.character_day_n, i + 1)
        "$day ${t.perDay[i]}"
    }.joinToString(", ")
    val rowLabel = stringResource(R.string.character_task_week_cd, t.title, t.total, description)
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = rowLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.title, style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.character_task_week_total, t.total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.size(width = 84.dp, height = 32.dp)) {
            val slot = size.width / t.perDay.size
            t.perDay.forEachIndexed { i, n ->
                val h = if (n == 0) 2f else size.height * n / peak
                drawRoundRect(bar, Offset(i * slot + slot * 0.15f, size.height - h), Size(slot * 0.7f, h), CornerRadius(3f, 3f))
            }
        }
    }
}

