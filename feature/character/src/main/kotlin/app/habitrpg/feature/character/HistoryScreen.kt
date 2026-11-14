package app.habitrpg.feature.character

import app.habitrpg.data.repo.monthYearFormatter
import app.habitrpg.data.repo.dayMonthFormatter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.habitrpg.data.repo.DayLine
import app.habitrpg.data.repo.LineKind
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** A month calendar of how busy each day was, with the log of the day you pick underneath. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.character_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.character_back_cd)) }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        HistoryContent(
            state, viewModel::previousMonth, viewModel::nextMonth, viewModel::select,
            Modifier.padding(padding).verticalScroll(rememberScrollState()),
        )
    }
}

@Composable
internal fun HistoryContent(
    state: HistoryUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (java.time.LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val monthLabel = remember(state.month, locale) { state.month.format(monthYearFormatter(locale)) }
    val short = state.month.month.getDisplayName(TextStyle.SHORT, locale)

    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(
                stringResource(R.string.character_best_streak), pluralStringResource(R.plurals.character_n_days, state.bestStreak, state.bestStreak),
                Modifier.weight(1f), note = stringResource(R.string.character_now_streak, state.currentStreak),
            )
            StatCard(stringResource(R.string.character_xp_in, short), "${state.monthXp}", Modifier.weight(1f))
            StatCard(stringResource(R.string.character_tasks_in, short), "${state.monthTasks}", Modifier.weight(1f))
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onPrevious, enabled = state.canGoPrevious) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.character_prev_month_cd))
            }
            Text(monthLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            IconButton(onClick = onNext, enabled = state.canGoNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.character_next_month_cd))
            }
        }

        MonthCalendar(state.weeks, state.firstDay, state.selected, onSelect)
        Legend()
        DayDetail(state, locale)
    }
}

@Composable
private fun Legend() {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
    ) {
        Text(stringResource(R.string.character_legend_fewer), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        listOf(0f, 0.3f, 0.55f, 0.8f, 1f).forEach { f ->
            Box(
                Modifier.size(16.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (f == 0f) scheme.surfaceContainer else lerp(scheme.surfaceContainerHigh, scheme.primary, f)),
            )
        }
        Text(stringResource(R.string.character_legend_more), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun DayDetail(state: HistoryUiState, locale: java.util.Locale) {
    val title = remember(state.selected, locale) { state.selected.format(dayMonthFormatter(locale)) }
    val day = state.day
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(
                if (day == null || (day.tasks == 0 && state.dayLines.isEmpty())) stringResource(R.string.character_day_empty)
                else listOf(
                    pluralStringResource(R.plurals.character_n_tasks, day.tasks, day.tasks),
                    stringResource(R.string.character_xp_signed, signed(day.xp)),
                    stringResource(R.string.character_gold_signed, signed(day.gold)),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.dayLines.forEach { LineRow(it) }
        }
    }
}

@Composable
private fun LineRow(line: DayLine) {
    val what = when (line.kind) {
        LineKind.DONE -> stringResource(R.string.character_line_done)
        LineKind.PLUS -> if (line.count > 1) stringResource(R.string.character_line_plus_n, line.count) else stringResource(R.string.character_line_plus)
        LineKind.MINUS -> if (line.count > 1) stringResource(R.string.character_line_minus_n, line.count) else stringResource(R.string.character_line_minus)
        LineKind.MISSED -> stringResource(R.string.character_line_missed)
        LineKind.UNDONE -> stringResource(R.string.character_line_undone)
    }
    val effect = listOfNotNull(
        stringResource(R.string.character_xp_signed, signed(line.xp)).takeIf { line.xp != 0 },
        stringResource(R.string.character_gold_signed, signed(line.gold)).takeIf { line.gold != 0 },
        stringResource(R.string.character_health_signed, signed(line.health)).takeIf { line.health != 0 },
    ).joinToString(", ")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$what · ${line.title}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(effect, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
