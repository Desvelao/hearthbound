package app.habitrpg.data.repo

import app.habitrpg.data.db.AppDatabase
import app.habitrpg.domain.GameDay
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

data class DayStats(val date: LocalDate, val xp: Int)

/** Completions of one task on each of the last seven game days, oldest first. */
data class TaskWeek(val taskId: String, val title: String, val perDay: List<Int>) {
    val total: Int get() = perDay.sum()
}

data class RecentEvent(
    val title: String,
    val kind: String,
    val xp: Int,
    val gold: Int,
    val health: Int,
    val at: Instant,
)

@Singleton
class StatsRepository @Inject constructor(
    db: AppDatabase,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val events = db.eventDao()
    private val rewards = db.rewardDao()
    private val shop = db.shopDao()

    /** Net XP for each of the last seven game days, oldest first. Undone ticks cancel out. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeWeek(): Flow<List<DayStats>> = settings.settings.flatMapLatest { s ->
        val start = GameDay.today(clock, s.dayStartHour).minusDays(6)
        val since = start.atStartOfDay(clock.zone).plusHours(s.dayStartHour.toLong()).toInstant().toEpochMilli()
        events.observeSince(since).map { rows ->
            val byDay = rows.groupBy {
                Instant.ofEpochMilli(it.timestamp).atZone(clock.zone).minusHours(s.dayStartHour.toLong()).toLocalDate()
            }.mapValues { (_, list) -> list.sumOf { it.xp } }
            (0..6).map { DayStats(start.plusDays(it.toLong()), byDay[start.plusDays(it.toLong())] ?: 0) }
        }
    }

    /**
     * Net completions per task over the last seven game days. A plus tap or a tick counts one; un-ticking a tick
     * takes it back. Tasks with nothing this week are left out, busiest first.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTaskWeek(): Flow<List<TaskWeek>> = settings.settings.flatMapLatest { s ->
        val start = GameDay.today(clock, s.dayStartHour).minusDays(6)
        val since = start.atStartOfDay(clock.zone).plusHours(s.dayStartHour.toLong()).toInstant().toEpochMilli()
        events.observeSince(since).map { rows ->
            rows.groupBy { it.taskId }.map { (id, list) ->
                val perDay = IntArray(7)
                for (e in list) {
                    val day = Instant.ofEpochMilli(e.timestamp).atZone(clock.zone)
                        .minusHours(s.dayStartHour.toLong()).toLocalDate()
                    val index = java.time.temporal.ChronoUnit.DAYS.between(start, day).toInt()
                    if (index !in 0..6) continue
                    perDay[index] += when {
                        e.kind == "PLUS" || e.kind == "DONE" -> 1
                        e.kind == "UNDONE" && (e.xp < 0 || e.gold < 0) -> -1 // took back a plus or a tick
                        else -> 0
                    }
                }
                TaskWeek(id, list.last().taskTitle, perDay.map { it.coerceAtLeast(0) })
            }.filter { it.total > 0 }.sortedWith(compareByDescending<TaskWeek> { it.total }.thenBy { it.title })
        }
    }

    /** The whole event log reduced for the History and Hero screens. Recomputed whenever an event is written. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeHistory(): Flow<HistoryData> = settings.settings.flatMapLatest { s ->
        events.observeAll().map { rows ->
            val logged = rows.map { LoggedEvent(it.taskId, it.taskTitle, Instant.ofEpochMilli(it.timestamp), it.kind, it.xp, it.gold, it.health) }
            HistoryMath.build(logged, clock.zone, s.dayStartHour, GameDay.today(clock, s.dayStartHour))
        }
    }

    /** Gold spent so far, on rewards and in the shop. */
    fun observeGoldSpent(): Flow<Int> = combine(rewards.observeSpent(), shop.observeSpent()) { a, b -> a + b }

    fun observeRecent(limit: Int = 20): Flow<List<RecentEvent>> = events.observeRecent(limit).map { rows ->
        rows.map { RecentEvent(it.taskTitle, it.kind, it.xp, it.gold, it.health, Instant.ofEpochMilli(it.timestamp)) }
    }
}
