package app.habitrpg.data

import app.habitrpg.data.repo.dayMonthFormatter
import app.habitrpg.data.repo.monthYearFormatter
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DateFormatsTest {
    private val day = LocalDate.of(2026, 11, 14) // a Saturday

    private fun dayText(tag: String) = day.format(dayMonthFormatter(Locale.forLanguageTag(tag)))

    @Test fun eachLanguageWritesTheDayTheWayItsReadersExpect() {
        assertEquals("Saturday, November 14", dayText("en-US"))
        assertEquals("Saturday 14 November", dayText("en-GB"))
        assertEquals("sábado, 14 de noviembre", dayText("es"))
        assertEquals("Samstag, 14. November", dayText("de"))
        assertEquals("samedi 14 novembre", dayText("fr"))
        assertEquals("sabato 14 novembre", dayText("it"))
    }

    @Test fun theCalendarHeaderNamesMonthAndYear() {
        val month = YearMonth.of(2026, 11)
        assertEquals("November 2026", month.format(monthYearFormatter(Locale.US)))
        assertEquals("noviembre de 2026", month.format(monthYearFormatter(Locale.forLanguageTag("es"))))
        assertEquals("November 2026", month.format(monthYearFormatter(Locale.GERMAN)))
    }
}
