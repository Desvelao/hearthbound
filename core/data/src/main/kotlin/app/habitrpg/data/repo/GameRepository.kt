package app.habitrpg.data.repo

import androidx.room.withTransaction
import app.habitrpg.data.db.AppDatabase
import app.habitrpg.data.db.QuestHistoryEntity
import app.habitrpg.data.db.TaskEventEntity
import app.habitrpg.domain.Bonuses
import app.habitrpg.domain.Catalog
import app.habitrpg.domain.Change
import app.habitrpg.domain.ChangeKind
import app.habitrpg.domain.Character
import app.habitrpg.domain.Engine
import app.habitrpg.domain.EngineResult
import app.habitrpg.domain.Classes
import app.habitrpg.domain.Event
import app.habitrpg.domain.Features
import app.habitrpg.domain.Quest
import app.habitrpg.domain.GameState
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Bridges the pure engine and storage: load state, ask the engine, save the result and the event log in one
 * transaction. Screens and the rollover worker both go through [dispatch], so there is one write path.
 */
@Singleton
class GameRepository @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val feedback: PlayFeedback,
) {
    private val taskDao = db.taskDao()
    private val characterDao = db.characterDao()
    private val eventDao = db.eventDao()
    private val checklistDao = db.checklistDao()
    private val shopDao = db.shopDao()
    private val questDao = db.questDao()

    fun observeCharacter(): Flow<Character> = characterDao.observe().map { it?.toDomain() ?: Character() }

    /** What equipped gear currently adds to a score, or nothing while the setting is off. The Today preview uses it. */
    fun observeBonuses(): Flow<Bonuses> = combine(
        settings.settings.map { it.gearBonuses }.distinctUntilChanged(), shopDao.observeEquipped(),
    ) { on, rows -> if (on) Catalog.bonusesFor(rows.map { it.itemId }) else Bonuses.NONE }.distinctUntilChanged()

    fun observeQuest(): Flow<Quest?> = questDao.observe().map { it?.toDomain() }

    fun observeQuestHistory(): Flow<List<QuestHistoryEntity>> = questDao.observeHistory()

    suspend fun dispatch(event: Event): EngineResult {
        var told: Feedback? = null
        val result = db.withTransaction {
            val before = loadState()
            val result = Engine.reduce(before, event, clock)
            persist(before, result, newDay = event == Event.Rollover)
            told = feedbackFor(event, before, result)
            result
        }
        // Emitted after the commit so a rolled-back transaction never announces anything.
        told?.let(feedback::emit)
        return result
    }

    /**
     * Changes the day-start hour and re-anchors the current day, so the new setting can never turn into
     * surprise damage or a skipped day.
     */
    suspend fun setDayStartHour(hour: Int) = db.withTransaction {
        settings.setDayStartHour(hour)
        dispatch(Event.AnchorToday)
    }

    /**
     * Un-ticks a daily or to-do by reversing what its latest tick gave, read from the event log. Works after
     * an app restart, when no in-memory snapshot exists. Returns null if the task is not currently ticked.
     */
    suspend fun uncomplete(taskId: String): EngineResult? = db.withTransaction {
        val last = eventDao.lastCompletion(taskId)
        if (last == null || last.kind != ChangeKind.DONE.name) return@withTransaction null
        dispatch(Event.Revert(taskId, ChangeKind.DONE, last.xp, last.gold, last.health, last.bossDamage))
    }

    private suspend fun loadState(): GameState {
        val s = settings.current()
        val character = characterDao.get()?.toDomain() ?: Character()
        val gear = if (s.gearBonuses) Catalog.bonusesFor(shopDao.getEquipped().map { it.itemId }) else Bonuses.NONE
        return GameState(
            character = character,
            tasks = taskDao.getAll().associate { it.id to it.toEngine() },
            paused = s.paused,
            dayStartHour = s.dayStartHour,
            bonuses = gear + if (s.classes) Classes.bonusesFor(character) else Bonuses.NONE,
            quest = questDao.get()?.toDomain(),
            features = Features(quests = s.quests, classes = s.classes),
        )
    }

    private suspend fun persist(before: GameState, result: EngineResult, newDay: Boolean) {
        val after = result.state
        if (after.character != before.character || characterDao.get() == null) {
            characterDao.upsert(after.character.toEntity())
        }
        if (after.quest != before.quest) {
            val q = after.quest
            if (q != null) questDao.upsert(q.toEntity())
            else {
                questDao.delete()
                // A dismissed quest goes to history. The engine only dismisses finished ones, which have an end date.
                before.quest?.let { b ->
                    questDao.insertHistory(
                        QuestHistoryEntity(
                            UUID.randomUUID().toString(), b.bossId, b.startedDate.toString(),
                            (b.endDate ?: b.deadlineDate).toString(), b.outcome.name,
                        ),
                    )
                }
            }
        }
        val now = clock.millis()
        val changed = after.tasks.values.filter { before.tasks[it.id] != it }
        if (changed.isNotEmpty()) {
            val rows = changed.mapNotNull { t -> taskDao.get(t.id)?.withEngine(t, now) }
            taskDao.update(rows)
        }
        if (newDay && after.character.lastRolloverDate != before.character.lastRolloverDate) {
            checklistDao.resetDailyChecklists()
        }
        if (result.changes.isNotEmpty()) {
            eventDao.insertAll(
                result.changes.map {
                    TaskEventEntity(
                        id = UUID.randomUUID().toString(), taskId = it.taskId,
                        taskTitle = taskDao.get(it.taskId)?.title ?: "",
                        timestamp = it.timestamp.toEpochMilli(), kind = it.kind.name,
                        xp = it.xp, gold = it.gold, health = it.health, day = it.day?.toString(),
                        bossDamage = it.bossDamage,
                    )
                },
            )
        }
    }

    private suspend fun feedbackFor(event: Event, before: GameState, result: EngineResult): Feedback? {
        // Taking something back, or buying, is silent here; purchases announce themselves.
        if (event is Event.Undo || event is Event.Revert || event is Event.Spend) return null
        if (event is Event.CastSkill) {
            val skill = Classes.all.flatMap { it.skills }.firstOrNull { it.id == event.skillId } ?: return null
            // The engine ignores a cast it cannot pay for or has no target for; that is silent.
            return if (result.state.character == before.character) null else Feedback.Cast(skill.id, result.milestones, result.state.character.level)
        }
        val changes = result.changes
        if (changes.isEmpty() && result.milestones.isEmpty()) return null
        val single: Change? = changes.singleOrNull()
        return Feedback.Scored(
            title = single?.let { taskDao.get(it.taskId)?.title },
            xp = changes.sumOf { it.xp },
            gold = changes.sumOf { it.gold },
            health = changes.sumOf { it.health },
            undo = single?.takeIf { it.kind == ChangeKind.PLUS || it.kind == ChangeKind.MINUS || it.kind == ChangeKind.DONE },
            milestones = result.milestones,
            characterAfter = result.state.character,
            fromRollover = event == Event.Rollover,
            bossDamage = changes.sumOf { it.bossDamage },
        )
    }
}
