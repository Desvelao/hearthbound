package app.habitrpg.feature.tasks

import app.habitrpg.domain.TaskType

/** Why a line got its type. Only [KEYWORD] and [DEFAULT] are guesses; the preview says so for [KEYWORD]. */
enum class Guess { EXPLICIT, KEYWORD, DEFAULT }

data class ParsedLine(val title: String, val type: TaskType, val guess: Guess)

/**
 * The words the classifier looks for in one language.
 *
 * This is matching data, not user-visible text, so it is not in the string resources. The help dialog reads the
 * same lists, so what it shows is always what the classifier does. Text in any language is matched against all
 * languages at once, whatever the app language is.
 */
data class Keywords(
    /** Phrases that make a line a daily. */
    val daily: List<String>,
    /** Words that make a line a habit. */
    val habit: List<String>,
    /** A line with one of these words and then a weekday name ("every Monday") is a daily. */
    val every: List<String>,
    val weekdays: List<String>,
)

object BulkParser {
    val keywords: Map<String, Keywords> = mapOf(
        "en" to Keywords(
            daily = listOf("every day", "everyday", "each day", "daily", "every morning", "every evening", "every night", "weekly", "every week"),
            habit = listOf("habit", "each time", "every time"),
            every = listOf("every", "each"),
            weekdays = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"),
        ),
        "es" to Keywords(
            daily = listOf("cada día", "cada dia", "todos los días", "todos los dias", "a diario", "diario", "diaria", "cada mañana", "cada noche", "cada semana", "semanal"),
            habit = listOf("hábito", "habito", "cada vez"),
            every = listOf("cada", "todos los", "todas las"),
            weekdays = listOf("lunes", "martes", "miércoles", "miercoles", "jueves", "viernes", "sábado", "sabado", "domingo"),
        ),
        "de" to Keywords(
            daily = listOf("jeden tag", "täglich", "taeglich", "jeden morgen", "jeden abend", "jede nacht", "jede woche", "wöchentlich", "woechentlich"),
            habit = listOf("gewohnheit", "jedes mal"),
            every = listOf("jeden", "jede", "jedes"),
            weekdays = listOf("montag", "dienstag", "mittwoch", "donnerstag", "freitag", "samstag", "sonntag"),
        ),
        "fr" to Keywords(
            daily = listOf("chaque jour", "tous les jours", "quotidien", "quotidienne", "chaque matin", "chaque soir", "chaque semaine", "hebdomadaire"),
            habit = listOf("habitude", "chaque fois"),
            every = listOf("chaque", "tous les", "toutes les"),
            weekdays = listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"),
        ),
        "it" to Keywords(
            daily = listOf("ogni giorno", "tutti i giorni", "ogni mattina", "ogni sera", "ogni notte", "ogni settimana", "quotidiano", "giornaliero", "settimanale"),
            habit = listOf("abitudine", "ogni volta"),
            every = listOf("ogni", "tutti i", "tutte le"),
            weekdays = listOf("lunedì", "lunedi", "martedì", "martedi", "mercoledì", "mercoledi", "giovedì", "giovedi", "venerdì", "venerdi", "sabato", "domenica"),
        ),
    )

    /** The keywords to show in help for [language] (a code like "es"); English when unknown. */
    fun keywordsFor(language: String): Keywords = keywords[language] ?: keywords.getValue("en")

    // A bullet or a number: "- ", "* ", "• ", "1. ", "2) ".
    private val bullet = Regex("""^(?:[-*•]|\d+[.)])(?:\s+|$)""")
    private val checkbox = Regex("""^\[[ xX]?]\s*""")
    // "h:", "d:", "t:" or "[h]", "[d]", "[t]" (either case).
    private val prefix = Regex("""^(?:\[([hdtHDT])]|([hdtHDT])\s*:)\s*""")

    private val dailyRegexes: List<Regex> by lazy {
        keywords.values.flatMap { k ->
            val fixed = k.daily.map { phrase(it) }
            val weekly = k.every.flatMap { e -> k.weekdays.map { d -> phrase("$e $d") } }
            fixed + weekly
        }
    }
    private val habitRegexes: List<Regex> by lazy { keywords.values.flatMap { k -> k.habit.map { phrase(it) } } }
    private val plusMinus = Regex("""^\+|\+/-|±""")

    private fun phrase(text: String) =
        Regex("""(?<!\p{L})${Regex.escape(text)}(?!\p{L})""", RegexOption.IGNORE_CASE)

    /** One [ParsedLine] per non-blank line of [text]. Lines that are only a bullet or prefix are dropped. */
    fun parse(text: String, default: TaskType = TaskType.TODO): List<ParsedLine> =
        text.lineSequence().mapNotNull { parseLine(it, default) }.toList()

    fun parseLine(raw: String, default: TaskType = TaskType.TODO): ParsedLine? {
        var s = raw.trim()
        s = bullet.replace(s, "")
        s = checkbox.replace(s, "")
        var explicit: TaskType? = null
        prefix.find(s)?.let { m ->
            explicit = when ((m.groupValues[1] + m.groupValues[2]).lowercase()) {
                "h" -> TaskType.HABIT
                "d" -> TaskType.DAILY
                else -> TaskType.TODO
            }
            s = s.removeRange(m.range)
        }
        s = s.trim()
        if (s.isEmpty()) return null
        explicit?.let { return ParsedLine(s, it, Guess.EXPLICIT) }
        val type = classify(s)
        return if (type != null) ParsedLine(s.removePrefix("+").trim().ifEmpty { s }, type, Guess.KEYWORD)
        else ParsedLine(s, default, Guess.DEFAULT)
    }

    /** The type a keyword points to, or null when nothing matches. A daily wins over a habit word. */
    fun classify(title: String): TaskType? = when {
        dailyRegexes.any { it.containsMatchIn(title) } -> TaskType.DAILY
        plusMinus.containsMatchIn(title) || habitRegexes.any { it.containsMatchIn(title) } -> TaskType.HABIT
        else -> null
    }
}
