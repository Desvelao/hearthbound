package app.habitrpg.widget

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import app.habitrpg.data.repo.AppLanguage
import app.habitrpg.data.repo.GameRepository
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.data.repo.TaskItem
import app.habitrpg.data.repo.TaskRepository
import app.habitrpg.domain.Character
import app.habitrpg.domain.Constants
import app.habitrpg.domain.Event
import app.habitrpg.domain.GameDay
import app.habitrpg.domain.TaskType
import app.habitrpg.feature.tasks.today.TodayBuilder
import app.habitrpg.feature.tasks.today.TodayFilter
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

data class WidgetDaily(val id: String, val title: String, val done: Boolean)

/** Everything the widget draws. Plain data, so what it shows can be tested without a launcher. */
data class WidgetState(
    val level: Int = 1,
    val health: Int = Constants.MAX_HEALTH,
    val xp: Int = 0,
    val xpToNext: Int = Constants.xpToNextLevel(1),
    val dailies: List<WidgetDaily> = emptyList(),
    /** False before onboarding is finished: the widget then only invites the player to open the app. */
    val ready: Boolean = true,
) {
    val finished: Int get() = dailies.count { it.done }
}

/** What the widget draws and the language it draws it in. */
data class WidgetView(val state: WidgetState, val language: AppLanguage)

/** Which dailies the widget lists: exactly the Today tab's rule (due today), open ones first. */
object WidgetModel {
    fun build(
        character: Character, dailies: List<TaskItem>, today: LocalDate, dayStartHour: Int, clock: Clock,
    ): WidgetState {
        val rows = TodayBuilder.build(
            emptyList(), dailies, emptyList(), today, 0, clock.zone, dayStartHour, TodayFilter.DAILIES,
        )
        return WidgetState(
            level = character.level, health = character.health, xp = character.xp,
            xpToNext = Constants.xpToNextLevel(character.level),
            dailies = (rows.dueNow + rows.done).map { WidgetDaily(it.item.id, it.item.title, it.item.task.doneToday) },
        )
    }
}

/**
 * What the widget does, kept out of the Glance classes so it is plain and testable. Both entry points run the
 * day rollover first (it is idempotent, and the same call the app makes on open): a widget that sat on the home
 * screen past midnight must never show, or tick, yesterday's list.
 */
@Singleton
class WidgetActions @Inject constructor(
    private val game: GameRepository,
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    /** The language the widget is drawn in. */
    suspend fun language(): AppLanguage = AppLanguage.fromTag(settings.current().language)

    /** The widget as it is right now: the day is rolled over first, then the current data and language are read. */
    suspend fun view(): WidgetView = WidgetView(load(), language())

    /**
     * The widget's data and language as they change. A widget session that is still alive recomposes from this, so a new
     * language, a tick in the app or a new level shows up without waiting for the next full update.
     */
    fun observe(): Flow<WidgetView> = combine(
        settings.settings, game.observeCharacter(), tasks.observe(TaskType.DAILY),
    ) { s, character, dailies ->
        val language = AppLanguage.fromTag(s.language)
        if (!s.onboardingDone) WidgetView(WidgetState(ready = false), language)
        else WidgetView(
            WidgetModel.build(character, dailies, GameDay.today(clock, s.dayStartHour), s.dayStartHour, clock), language,
        )
    }

    suspend fun load(): WidgetState {
        val s = settings.current()
        if (!s.onboardingDone) return WidgetState(ready = false)
        game.dispatch(Event.Rollover)
        return WidgetModel.build(
            game.observeCharacter().first(), tasks.observe(TaskType.DAILY).first(),
            GameDay.today(clock, s.dayStartHour), s.dayStartHour, clock,
        )
    }

    /** Ticks a daily that is open, or takes back one that is done. Scores exactly once however often it is tapped. */
    suspend fun toggle(taskId: String) {
        if (!settings.current().onboardingDone) return
        game.dispatch(Event.Rollover)
        val item = tasks.get(taskId) ?: return
        if (item.type != TaskType.DAILY) return
        if (item.task.doneToday) game.uncomplete(taskId) else game.dispatch(Event.CompleteDaily(taskId))
    }
}
