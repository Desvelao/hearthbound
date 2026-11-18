package app.habitrpg.feature.tasks

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.habitrpg.data.repo.AppLanguage
import app.habitrpg.data.repo.withLanguage
import app.habitrpg.domain.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BulkParserTest {
    private fun types(text: String, default: TaskType = TaskType.TODO) = BulkParser.parse(text, default).map { it.type }

    @Test fun oneTaskPerNonBlankLine() {
        val lines = BulkParser.parse("Buy stamps\n\n   \nCall mom\r\n")
        assertEquals(listOf("Buy stamps", "Call mom"), lines.map { it.title })
    }

    @Test fun bulletsNumbersAndCheckboxesAreStripped() {
        val lines = BulkParser.parse("- one\n* two\n• three\n1. four\n2) five\n[ ] six\n- [x] seven")
        assertEquals(listOf("one", "two", "three", "four", "five", "six", "seven"), lines.map { it.title })
    }

    @Test fun anExplicitPrefixWinsOverKeywords() {
        val l = BulkParser.parseLine("h: Stretch every day")!!
        assertEquals(TaskType.HABIT, l.type)
        assertEquals(Guess.EXPLICIT, l.guess)
        assertEquals("Stretch every day", l.title)
        assertEquals(TaskType.DAILY, BulkParser.parseLine("[d] Pay rent")!!.type)
        assertEquals(TaskType.TODO, BulkParser.parseLine("T: run daily report")!!.type)
    }

    @Test fun aLineThatIsOnlyAPrefixIsDropped() {
        assertEquals(emptyList<ParsedLine>(), BulkParser.parse("- \nh:\n[ ]"))
    }

    @Test fun keywordsPickTheTypeInEveryLanguage() {
        assertEquals(TaskType.DAILY, types("Water plants every day").single())
        assertEquals(TaskType.DAILY, types("Gym every Monday").single())
        assertEquals(TaskType.DAILY, types("Estirar a diario").single())
        assertEquals(TaskType.DAILY, types("Zähne putzen täglich").single())
        assertEquals(TaskType.DAILY, types("Faire les comptes chaque jour").single())
        assertEquals(TaskType.DAILY, types("Studiare ogni martedì").single())
        assertEquals(TaskType.HABIT, types("+ Drink water").single())
        assertEquals(TaskType.HABIT, types("Biting nails +/-").single())
        assertEquals(TaskType.HABIT, types("Fumar, cada vez").single())
    }

    @Test fun aWordInsideAnotherWordDoesNotMatch() {
        assertEquals(TaskType.TODO, types("Fix the dailyness report").single())
        assertNull(BulkParser.classify("Call the habitat office"))
    }

    @Test fun noKeywordFallsBackToTheGivenDefault() {
        val l = BulkParser.parseLine("Buy stamps", TaskType.HABIT)!!
        assertEquals(TaskType.HABIT, l.type)
        assertEquals(Guess.DEFAULT, l.guess)
    }

    @Test fun aKeywordGuessIsMarkedAsAGuess() =
        assertEquals(Guess.KEYWORD, BulkParser.parseLine("Run daily")!!.guess)

    /** The examples the help dialog shows must really classify the way the dialog says, in every language. */
    @Test fun helpExamplesClassifyAsListed() {
        val base: Context = ApplicationProvider.getApplicationContext()
        AppLanguage.entries.filter { it.tag != "system" }.forEach { lang ->
            val c = base.withLanguage(lang)
            assertEquals(lang.tag, TaskType.HABIT, BulkParser.parseLine(c.getString(R.string.tasks_bulk_example_habit))!!.type)
            assertEquals(lang.tag, TaskType.DAILY, BulkParser.parseLine(c.getString(R.string.tasks_bulk_example_daily))!!.type)
            assertEquals(lang.tag, TaskType.TODO, BulkParser.parseLine(c.getString(R.string.tasks_bulk_example_todo))!!.type)
        }
    }
}
