package app.habitrpg.data.repo

import app.habitrpg.data.db.CharacterEntity
import app.habitrpg.data.db.QuestEntity
import app.habitrpg.domain.Quest
import app.habitrpg.domain.QuestOutcome
import app.habitrpg.data.db.TaskEntity
import app.habitrpg.domain.Character
import app.habitrpg.domain.Difficulty
import app.habitrpg.domain.Schedule
import app.habitrpg.domain.Task
import app.habitrpg.domain.TaskType
import java.time.LocalDate

internal fun Schedule?.toColumns(): Triple<Int, Int, Pair<Int, String?>> = when (this) {
    null -> Triple(0, 0, 1 to null)
    is Schedule.Weekdays -> Triple(1, mask, 1 to null)
    is Schedule.EveryNDays -> Triple(2, 0, n to start.toString())
}

internal fun TaskEntity.schedule(): Schedule? = when (scheduleKind) {
    1 -> Schedule.Weekdays(weekdayMask)
    2 -> Schedule.EveryNDays(everyN, LocalDate.parse(startDate!!))
    else -> null
}

internal fun TaskEntity.toEngine() = Task(
    id = id,
    type = TaskType.valueOf(type),
    difficulty = Difficulty.valueOf(difficulty),
    plusEnabled = plusEnabled,
    minusEnabled = minusEnabled,
    plusCount = plusCount,
    minusCount = minusCount,
    value = value,
    schedule = schedule(),
    streak = streak,
    doneToday = doneToday,
    completed = completedAt != null,
)

/** Copies the engine-owned fields of [t] onto this row. [completedAt] is only set when `completed` flips. */
internal fun TaskEntity.withEngine(t: Task, nowMillis: Long) = copy(
    plusCount = t.plusCount,
    minusCount = t.minusCount,
    value = t.value,
    streak = t.streak,
    doneToday = t.doneToday,
    completedAt = when {
        !t.completed -> null
        completedAt != null -> completedAt
        else -> nowMillis
    },
)

internal fun CharacterEntity.toDomain() = Character(
    level = level, xp = xp, health = health, gold = gold,
    lastRolloverDate = lastRolloverDate?.let(LocalDate::parse),
    classId = classId, mana = mana, statMight = statMight, statMind = statMind, statVigor = statVigor,
)

internal fun Character.toEntity() = CharacterEntity(
    level = level, xp = xp, health = health, gold = gold, lastRolloverDate = lastRolloverDate?.toString(),
    classId = classId, mana = mana, statMight = statMight, statMind = statMind, statVigor = statVigor,
)

internal fun QuestEntity.toDomain() = Quest(
    bossId = bossId, hp = hp, startedDate = LocalDate.parse(startedDate), deadlineDate = LocalDate.parse(deadlineDate),
    outcome = runCatching { QuestOutcome.valueOf(outcome) }.getOrDefault(QuestOutcome.ACTIVE),
    endDate = endDate?.let(LocalDate::parse),
)

internal fun Quest.toEntity() = QuestEntity(
    bossId = bossId, hp = hp, startedDate = startedDate.toString(), deadlineDate = deadlineDate.toString(),
    outcome = outcome.name, endDate = endDate?.toString(),
)
