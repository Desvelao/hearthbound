package app.habitrpg.feature.tasks.today

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import app.habitrpg.data.repo.TaskItem
import app.habitrpg.domain.Difficulty
import app.habitrpg.domain.Schedule
import app.habitrpg.domain.Task
import app.habitrpg.domain.TaskType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A phone-sized window: the default is tiny, and a lazy list does not draw rows below the fold.
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
@RunWith(RobolectricTestRunner::class)
class TodayViewTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 13)

    private fun item(task: Task, title: String, due: LocalDate? = null, doneAt: Instant? = null) =
        TaskItem(task, title, "", 0, due, doneAt, emptyList())

    private val stretch = item(Task("d1", TaskType.DAILY, Difficulty.EASY, schedule = Schedule.Weekdays.EVERY_DAY, streak = 3), "Stretch")
    private val read = item(Task("d2", TaskType.DAILY, Difficulty.MEDIUM, schedule = Schedule.Weekdays.EVERY_DAY, doneToday = true), "Read")
    private val bill = item(Task("t1", TaskType.TODO), "Pay bill", due = today.minusDays(1))
    private val water = item(Task("h1", TaskType.HABIT, plusCount = 3), "Water")

    private fun state(filter: TodayFilter = TodayFilter.ALL) = TodayBuilder.build(
        habits = listOf(water), dailies = listOf(stretch, read), todos = listOf(bill),
        today = today, nowMinuteOfDay = 12 * 60, zone = ZoneOffset.UTC, dayStartHour = 0, filter = filter,
    )

    private class Taps {
        val toggled = mutableListOf<Pair<String, Boolean>>()
        val edited = mutableListOf<Pair<TaskType, String>>()
        val filters = mutableListOf<TodayFilter>()
        val plus = mutableListOf<String>()
        val minus = mutableListOf<String>()
    }

    private fun show(s: TodayState = state(), taps: Taps = Taps()): Taps {
        compose.setContent {
            MaterialTheme {
                TodayContent(
                    s, { taps.filters += it }, { type, id -> taps.edited += type to id },
                    { item, done -> taps.toggled += item.id to done }, { taps.plus += it }, { taps.minus += it },
                )
            }
        }
        return taps
    }

    @Test fun dueRowsAreUntickedCheckboxesAndTappingOneTicksIt() {
        val taps = show()
        compose.onNodeWithContentDescription("Stretch").assertIsOff()
        compose.onNodeWithContentDescription("Pay bill").assertIsOff()
        compose.onNodeWithContentDescription("Stretch").performClick()
        assertEquals(listOf("d1" to true), taps.toggled)
    }

    @Test fun rowsShowTheRewardAndTheReasonItIsDue() {
        show()
        compose.onNodeWithText("Daily · streak 3 · +11 XP").assertExists() // 10 XP with a 1.06 streak bonus rounds to 11
        compose.onNodeWithText("To-do · +10 XP").assertExists()
        compose.onNodeWithText("Overdue").assertExists()
    }

    @Test fun theHeaderCountsTheDayInWordsAndInTheRing() {
        show()
        compose.onNodeWithText("Friday, November 13 · 1 of 3 done").assertExists()
        compose.onNodeWithContentDescription("1 of 3 done today").assertExists()
    }

    @Test fun doneItemsStartCollapsedThenExpandAndCanBeUnticked() {
        val taps = show()
        compose.onNodeWithContentDescription("Read").assertDoesNotExist()
        compose.onNodeWithText("Done today · 1").performClick()
        compose.onNodeWithContentDescription("Read").assertIsOn()
        compose.onNodeWithContentDescription("Read").performClick()
        assertEquals(listOf("d2" to false), taps.toggled)
    }

    @Test fun habitButtonsReportPlusAndMinus() {
        val taps = show()
        compose.onNodeWithContentDescription("Plus: Water").performClick()
        compose.onNodeWithContentDescription("Minus: Water").performClick()
        assertEquals(listOf("h1"), taps.plus); assertEquals(listOf("h1"), taps.minus)
        compose.onNodeWithText("today +3 −0 · +10 XP each").assertExists()
    }

    @Test fun aPlusOnlyHabitHasNoMinusButton() {
        val plusOnly = water.copy(task = water.task.copy(minusEnabled = false))
        show(TodayBuilder.build(listOf(plusOnly), emptyList(), emptyList(), today, 0, ZoneOffset.UTC, 0, TodayFilter.ALL))
        compose.onNodeWithContentDescription("Plus: Water").assertExists()
        compose.onNodeWithContentDescription("Minus: Water").assertDoesNotExist()
    }

    @Test fun filterChipsReportTheChoice() {
        val taps = show()
        compose.onNodeWithText("Dailies").performClick()
        compose.onNodeWithText("Habits").performClick()
        assertEquals(listOf(TodayFilter.DAILIES, TodayFilter.HABITS), taps.filters)
    }

    @Test fun longPressEditsTheTask() {
        val taps = show()
        compose.onNodeWithContentDescription("Pay bill").performTouchInput { longClick() }
        assertEquals(listOf(TaskType.TODO to "t1"), taps.edited)
        assertTrue(taps.toggled.isEmpty())
    }

    @Test fun anEmptyDaySaysWhereToAddTasks() {
        show(TodayBuilder.build(emptyList(), emptyList(), emptyList(), today, 0, ZoneOffset.UTC, 0, TodayFilter.ALL))
        compose.onNodeWithText("Nothing is due today. Add tasks in the Tasks tab.").assertExists()
    }

    @Test fun finishingEverythingSaysSo() {
        val finished = TodayBuilder.build(emptyList(), listOf(read), emptyList(), today, 0, ZoneOffset.UTC, 0, TodayFilter.ALL)
        show(finished)
        compose.onNodeWithText("All done for today. Nice work.").assertExists()
        compose.onNodeWithText("Friday, November 13 · 1 of 1 done").assertExists()
    }
}
