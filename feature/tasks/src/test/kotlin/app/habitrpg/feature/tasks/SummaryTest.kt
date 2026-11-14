package app.habitrpg.feature.tasks

import app.habitrpg.data.repo.TaskItem
import app.habitrpg.domain.Task
import app.habitrpg.domain.TaskType
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.habitrpg.data.repo.AppLanguage
import app.habitrpg.data.repo.withLanguage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SummaryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val res = context.resources
    private fun TaskItem.summary() = summary(res)

    private fun habit(plus: Boolean, minus: Boolean) = TaskItem(
        Task("h", TaskType.HABIT, plusEnabled = plus, minusEnabled = minus, plusCount = 3, minusCount = 1),
        "h", "", 0, null, null, emptyList(),
    )

    @Test fun aPlusOnlyHabitDoesNotShowAMinusCount() = assertEquals("Easy · today +3", habit(true, false).summary())
    @Test fun aMinusOnlyHabitDoesNotShowAPlusCount() = assertEquals("Easy · today −1", habit(false, true).summary())
    private fun valued(type: TaskType, value: Double, done: Boolean = false) = TaskItem(
        Task("x", type, value = value, completed = done), "x", "", 0, null, null, emptyList(),
    )

    @Test fun aFarmedHabitSaysItIsWorthLessAndANeglectedOneWorthMore() {
        assertEquals("Easy · today +0 −0 · worth less", valued(TaskType.HABIT, 5.0).summary())
        assertEquals("Easy · today +0 −0 · worth more", valued(TaskType.HABIT, -5.0).summary())
        assertEquals("Easy · today +0 −0", valued(TaskType.HABIT, 1.5).summary())
    }

    @Test fun anOldOpenTodoIsWorthMoreButADoneOneSaysNothing() {
        assertEquals("Easy · worth more", valued(TaskType.TODO, -4.0).summary())
        assertEquals("Easy", valued(TaskType.TODO, -4.0, done = true).summary())
    }

    @Test fun aTwoWayHabitShowsBoth() = assertEquals("Easy · today +3 −1", habit(true, true).summary())

    @Test fun theSummaryFollowsTheChosenLanguage() {
        val es = context.withLanguage(AppLanguage.SPANISH).resources
        val de = context.withLanguage(AppLanguage.GERMAN).resources
        assertEquals("Fácil · hoy +3 −1", habit(true, true).summary(es))
        assertEquals("Leicht · heute +3 −1", habit(true, true).summary(de))
        assertEquals("Fácil · hoy +0 −0 · vale menos", valued(TaskType.HABIT, 5.0).summary(es))
    }
}
