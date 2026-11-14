package app.habitrpg.feature.character

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.habitrpg.data.repo.DayActivity
import app.habitrpg.data.repo.DayLine
import app.habitrpg.data.repo.HistoryData
import app.habitrpg.data.repo.LineKind
import app.habitrpg.domain.Character
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class CalendarGridTest {
    private val nov = YearMonth.of(2026, 11) // the 1st is a Sunday
    private val today = LocalDate.of(2026, 11, 13)

    @Test fun mondayFirstPadsSixDaysBeforeASundayTheFirst() {
        val weeks = CalendarGrid.build(nov, DayOfWeek.MONDAY, today, emptyMap())
        assertEquals(6, weeks.size)
        assertEquals(List(6) { null }, weeks[0].take(6))
        assertEquals(1, weeks[0][6]!!.date.dayOfMonth)
        assertEquals(30, weeks.last()[0]!!.date.dayOfMonth)
        assertTrue(weeks.all { it.size == 7 })
    }

    @Test fun sundayFirstNeedsNoLeadingPaddingAndOnlyFiveRows() {
        val weeks = CalendarGrid.build(nov, DayOfWeek.SUNDAY, today, emptyMap())
        assertEquals(5, weeks.size)
        assertEquals(1, weeks[0][0]!!.date.dayOfMonth)
        assertEquals(List(5) { null }, weeks.last().drop(2))
    }

    @Test fun everyDayOfTheMonthAppearsExactlyOnce() {
        val days = CalendarGrid.build(nov, DayOfWeek.MONDAY, today, emptyMap()).flatten().filterNotNull().map { it.date.dayOfMonth }
        assertEquals((1..30).toList(), days)
        assertEquals(29, CalendarGrid.build(YearMonth.of(2028, 2), DayOfWeek.MONDAY, LocalDate.of(2028, 3, 1), emptyMap()).flatten().filterNotNull().size)
    }

    @Test fun todayFutureAndLevelsAreMarked() {
        val activity = mapOf(today to DayActivity(today, 6, 80, 20), today.minusDays(1) to DayActivity(today.minusDays(1), 1, 10, 4))
        val cells = CalendarGrid.build(nov, DayOfWeek.MONDAY, today, activity).flatten().filterNotNull().associateBy { it.date.dayOfMonth }
        assertTrue(cells.getValue(13).isToday && !cells.getValue(12).isToday)
        assertEquals(3, cells.getValue(13).level)
        assertEquals(1, cells.getValue(12).level)
        assertEquals(0, cells.getValue(5).level)
        assertTrue(cells.getValue(14).isFuture && !cells.getValue(13).isFuture)
    }

    @Test fun weekdayHeadersStartAtTheChosenDay() {
        assertEquals(DayOfWeek.SUNDAY, CalendarGrid.weekdayOrder(DayOfWeek.SUNDAY).first())
        assertEquals(DayOfWeek.SATURDAY, CalendarGrid.weekdayOrder(DayOfWeek.SUNDAY).last())
    }
}

@RunWith(RobolectricTestRunner::class)
class HistoryViewsTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 13)
    private val activity = mapOf(
        today to DayActivity(today, 6, 80, 20),
        today.minusDays(1) to DayActivity(today.minusDays(1), 1, 10, 4),
    )

    private fun calendar(selected: LocalDate = today, onSelect: (LocalDate) -> Unit = {}) = compose.setContent {
        MaterialTheme {
            MonthCalendar(CalendarGrid.build(YearMonth.of(2026, 11), DayOfWeek.MONDAY, today, activity), DayOfWeek.MONDAY, selected, onSelect)
        }
    }

    @Test fun everyCellNamesItsDateAndHowManyTasksWereDone() {
        calendar()
        compose.onNodeWithContentDescription("Friday, November 13, 6 tasks, today").assertExists()
        compose.onNodeWithContentDescription("Thursday, November 12, 1 task").assertExists()
        compose.onNodeWithContentDescription("Saturday, November 7, no tasks").assertExists()
    }

    @Test fun theSelectedDayIsMarkedAsSelected() {
        calendar(selected = today.minusDays(1))
        compose.onNodeWithContentDescription("Thursday, November 12, 1 task").assertIsSelected()
    }

    @Test fun pickingAPastDayReportsIt() {
        var picked: LocalDate? = null
        calendar { picked = it }
        compose.onNodeWithContentDescription("Thursday, November 12, 1 task").performClick()
        assertEquals(today.minusDays(1), picked)
    }

    @Test fun futureDaysCannotBePicked() {
        var picked: LocalDate? = null
        calendar { picked = it }
        compose.onNodeWithContentDescription("Saturday, November 14").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Saturday, November 14").performClick()
        assertNull(picked)
    }

    @Test fun everyCellIsAtLeastAsTallAsATouchTarget() {
        calendar()
        compose.onNodeWithContentDescription("Friday, November 13, 6 tasks, today").assertHeightIsAtLeast(44.dp)
    }

    private fun content(state: HistoryUiState, onPrevious: () -> Unit = {}, onNext: () -> Unit = {}) = compose.setContent {
        MaterialTheme { HistoryContent(state, onPrevious, onNext, {}) }
    }

    private val state = HistoryUiState(
        loaded = true, month = YearMonth.of(2026, 11), firstDay = DayOfWeek.MONDAY,
        weeks = CalendarGrid.build(YearMonth.of(2026, 11), DayOfWeek.MONDAY, today, activity),
        selected = today, canGoNext = false, canGoPrevious = true, monthXp = 90, monthTasks = 7, bestStreak = 21, currentStreak = 2,
        day = activity.getValue(today),
        dayLines = listOf(DayLine("Stretch", LineKind.DONE, 1, 14, 4, 0, Instant.EPOCH), DayLine("Water", LineKind.PLUS, 3, 15, 6, 0, Instant.EPOCH)),
    )

    @Test fun theSummaryMonthTitleAndTotalsAreShown() {
        content(state)
        compose.onNodeWithText("21 days").assertExists()
        compose.onNodeWithText("Now: 2").assertExists()
        compose.onNodeWithText("November 2026").assertExists()
        compose.onNodeWithText("90").assertExists()
        compose.onNodeWithText("Friday, November 13").assertExists()
        compose.onNodeWithText("6 tasks · +80 XP · +20 gold").assertExists()
    }

    @Test fun theDayLogFoldsPlusTapsAndShowsEffects() {
        content(state)
        compose.onNodeWithText("Done · Stretch").assertExists()
        compose.onNodeWithText("Plus ×3 · Water").assertExists()
        compose.onNodeWithText("+14 XP, +4 gold").assertExists()
    }

    @Test fun anEmptyDaySaysSo() {
        content(state.copy(selected = today.minusDays(5), day = null, dayLines = emptyList()))
        compose.onNodeWithText("Nothing logged on this day.").assertExists()
    }

    @Test fun theNextMonthButtonIsDisabledOnTheCurrentMonthAndPreviousWorks() {
        var previous = 0
        content(state, onPrevious = { previous++ })
        compose.onNodeWithContentDescription("Next month").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Previous month").assertIsEnabled().performClick()
        assertEquals(1, previous)
    }

    @Test fun anEarlierMonthCanGoForwardAgain() {
        content(state.copy(canGoNext = true))
        compose.onNodeWithContentDescription("Next month").assertIsEnabled()
    }
}

@RunWith(RobolectricTestRunner::class)
class HeroViewTest {
    @get:Rule val compose = createComposeRule()

    private val state = HeroUiState(
        loaded = true,
        character = Character(level = 4, xp = 60, health = 42, gold = 37),
        history = HistoryData(bestStreak = 21, currentStreak = 12, goldEarned = 310, weekHealthLost = 8, weekMissed = 2, tasksThisWeek = 23, tasksLastWeek = 19),
        goldSpent = 120,
    )

    private fun show(opens: () -> Unit = {}, history: () -> Unit = {}, look: () -> Unit = {}, s: HeroUiState = state) =
        compose.setContent { MaterialTheme { HeroContent(s, opens, history, look) } }

    @Test fun showsLevelProgressAndTheNumbersWorthSeeing() {
        show()
        compose.onNodeWithText("Level 4 · 115 XP to level 5").assertExists()
        compose.onNodeWithText("12 days").assertExists()
        compose.onNodeWithText("Best: 21 days").assertExists()
        compose.onNodeWithText("23").assertExists()
        compose.onNodeWithText("4 more than last week").assertExists()
        compose.onNodeWithText("120 spent on rewards").assertExists()
        compose.onNodeWithText("from 2 missed dailies").assertExists()
    }

    @Test fun barsAndGoldAreReadAsNumbers() {
        show()
        compose.onNodeWithContentDescription("Health 42 of 50").assertExists()
        compose.onNodeWithContentDescription("37 gold").assertExists()
        compose.onNodeWithText("LV 4").assertExists()
    }

    @Test fun theThreeActionsAreReachable() {
        var settings = 0; var history = 0; var look = 0
        show(opens = { settings++ }, history = { history++ }, look = { look++ })
        compose.onNodeWithContentDescription("Settings").performClick()
        // These two sit below the fold of the scrolling page, so bring them into view first.
        compose.onNodeWithText("See history").performScrollTo().performClick()
        compose.onNodeWithText("Change look").performScrollTo().performClick()
        assertEquals(listOf(1, 1, 1), listOf(settings, history, look))
    }

    @Test fun aQuietWeekSaysSoInsteadOfInventingNumbers() {
        show(s = state.copy(history = HistoryData(tasksThisWeek = 3, tasksLastWeek = 3)))
        compose.onNodeWithText("same as last week").assertExists()
        compose.onNodeWithText("no missed dailies").assertExists()
        assertEquals(0, compose.onAllNodesWithText("Completions per task").fetchSemanticsNodes().size)
    }

    @Test fun weekComparisonWords() {
        compose.setContent {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text(compareWithLastWeek(23, 19))
                androidx.compose.material3.Text(compareWithLastWeek(1, 3))
                androidx.compose.material3.Text(compareWithLastWeek(0, 0))
            }
        }
        compose.onNodeWithText("4 more than last week").assertExists()
        compose.onNodeWithText("2 fewer than last week").assertExists()
        compose.onNodeWithText("same as last week").assertExists()
    }
}
