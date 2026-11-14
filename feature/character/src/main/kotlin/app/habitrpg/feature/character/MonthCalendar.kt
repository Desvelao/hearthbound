package app.habitrpg.feature.character

import app.habitrpg.data.repo.dayMonthFormatter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** How far each heat level moves from the empty colour towards the theme's primary colour. */
private val HEAT = floatArrayOf(0f, 0.3f, 0.55f, 0.8f, 1f)

/**
 * The month as a grid of 44dp cells. Colour is only an extra cue: every cell's accessible label says the date and
 * how many tasks were done, and today and the selected day are marked by more than colour.
 */
@Composable
fun MonthCalendar(
    weeks: List<List<CalendarCell?>>,
    firstDay: DayOfWeek,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val dayFormat = remember(locale) { dayMonthFormatter(locale) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CalendarGrid.weekdayOrder(firstDay).forEach {
                Text(
                    it.getDisplayName(TextStyle.SHORT, locale), Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { cell ->
                    if (cell == null) Spacer(Modifier.weight(1f).height(44.dp))
                    else DayCell(cell, cell.date == selected, dayFormat, onSelect, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayCell(cell: CalendarCell, isSelected: Boolean, format: DateTimeFormatter, onSelect: (LocalDate) -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val fill = when {
        cell.isFuture -> Color.Transparent
        cell.level == 0 -> scheme.surfaceContainer
        else -> lerp(scheme.surfaceContainerHigh, scheme.primary, HEAT[cell.level])
    }
    val text = when {
        cell.isFuture -> scheme.onSurfaceVariant
        else -> readableOn(fill, scheme.onSurface, scheme.onPrimary)
    }
    val border = when {
        isSelected -> 2.dp to scheme.secondary
        cell.level == 0 -> 1.dp to scheme.outlineVariant
        else -> 0.dp to Color.Transparent
    }
    val label = buildList {
        add(cell.date.format(format))
        if (!cell.isFuture) add(if (cell.tasks == 0) stringResource(R.string.character_cal_no_tasks) else pluralStringResource(R.plurals.character_n_tasks, cell.tasks, cell.tasks))
        if (cell.isToday) add(stringResource(R.string.character_cal_today))
    }.joinToString(", ")
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(10.dp)).background(fill)
            .border(border.first, border.second, RoundedCornerShape(10.dp))
            .selectable(selected = isSelected, enabled = !cell.isFuture, role = Role.Button, onClick = { onSelect(cell.date) })
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${cell.date.dayOfMonth}", color = text, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (cell.isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
        )
        // Today also gets a dot under the number, so it never relies on colour alone.
        if (cell.isToday) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(4.dp).clip(CircleShape).background(text))
    }
}

/** Picks whichever of [a] and [b] reads better on [background]. */
internal fun readableOn(background: Color, a: Color, b: Color): Color = if (contrast(background, a) >= contrast(background, b)) a else b

private fun contrast(x: Color, y: Color): Float {
    val lx = x.luminance()
    val ly = y.luminance()
    return (maxOf(lx, ly) + 0.05f) / (minOf(lx, ly) + 0.05f)
}
