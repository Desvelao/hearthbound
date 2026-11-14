package app.habitrpg.data.repo

import android.text.format.DateFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Weekday, day and month the way [locale] writes them: "Saturday, November 14" in US English, "sábado, 14 de
 * noviembre" in Spanish, "Samstag, 14. November" in German. The order and the little words come from Android's
 * own locale data rather than from a pattern written here.
 */
fun dayMonthFormatter(locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)

/** Month and year as the calendar header, such as "November 2026" or "noviembre de 2026". */
fun monthYearFormatter(locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "yLLLL"), locale)
