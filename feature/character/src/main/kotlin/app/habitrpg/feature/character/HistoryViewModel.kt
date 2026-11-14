package app.habitrpg.feature.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.habitrpg.data.repo.DayActivity
import app.habitrpg.data.repo.DayLine
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.data.repo.StatsRepository
import app.habitrpg.domain.GameDay
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val loaded: Boolean = false,
    val month: YearMonth = YearMonth.of(2000, 1),
    val firstDay: DayOfWeek = DayOfWeek.MONDAY,
    val weeks: List<List<CalendarCell?>> = emptyList(),
    val selected: LocalDate = LocalDate.of(2000, 1, 1),
    val canGoNext: Boolean = false,
    val canGoPrevious: Boolean = true,
    val monthXp: Int = 0,
    val monthTasks: Int = 0,
    val bestStreak: Int = 0,
    val currentStreak: Int = 0,
    val day: DayActivity? = null,
    val dayLines: List<DayLine> = emptyList(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    stats: StatsRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val shownMonth = MutableStateFlow<YearMonth?>(null)   // null = this month
    private val pickedDay = MutableStateFlow<LocalDate?>(null)    // null = pick a sensible default

    val state: StateFlow<HistoryUiState> =
        combine(stats.observeHistory(), shownMonth, pickedDay, settings.settings) { history, requested, picked, s ->
            val today = GameDay.today(clock, s.dayStartHour)
            val thisMonth = YearMonth.from(today)
            val month = (requested ?: thisMonth).let { if (it.isAfter(thisMonth)) thisMonth else it }
            val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
            val inMonth = history.activity.filterKeys { YearMonth.from(it) == month }
            val selected = picked?.takeIf { YearMonth.from(it) == month && !it.isAfter(today) }
                ?: if (month == thisMonth) today else inMonth.filterValues { it.tasks > 0 }.keys.maxOrNull() ?: month.atDay(1)
            HistoryUiState(
                loaded = true, month = month, firstDay = first,
                weeks = CalendarGrid.build(month, first, today, history.activity),
                selected = selected,
                canGoNext = month.isBefore(thisMonth),
                canGoPrevious = month.isAfter(thisMonth.minusMonths(MAX_MONTHS_BACK)),
                monthXp = inMonth.values.sumOf { it.xp },
                monthTasks = inMonth.values.sumOf { it.tasks },
                bestStreak = history.bestStreak, currentStreak = history.currentStreak,
                day = history.activity[selected], dayLines = history.lines[selected].orEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun previousMonth() {
        val s = state.value
        if (!s.canGoPrevious) return
        shownMonth.value = s.month.minusMonths(1)
        pickedDay.value = null
    }

    fun nextMonth() {
        val s = state.value
        if (!s.canGoNext) return
        shownMonth.value = s.month.plusMonths(1)
        pickedDay.value = null
    }

    fun select(day: LocalDate) { pickedDay.value = day }

    companion object { const val MAX_MONTHS_BACK = 60L }
}
