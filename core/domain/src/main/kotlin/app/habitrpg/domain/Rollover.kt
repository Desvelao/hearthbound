package app.habitrpg.domain

import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Day rollover: must never run twice or be skipped. It is idempotent because it only processes days
 * between the stored last-rollover date and today, then moves the stored date to today.
 */
object Rollover {

    fun anchor(state: GameState, clock: Clock): EngineResult {
        val today = GameDay.today(clock, state.dayStartHour)
        val last = state.character.lastRolloverDate
        // Forward only: moving the date back would let an already scored day be scored again.
        return if (last != null && !today.isAfter(last)) EngineResult(state)
        else EngineResult(state.copy(character = state.character.copy(lastRolloverDate = today)))
    }

    /**
     * Task value moves with the day: a habit nobody tapped plus on, and every open to-do, becomes worth more. Only days
     * that count get here (no pause, no long absence), so a holiday never inflates anything. Later days of a gap
     * start with fresh counters, so a habit is idle on them whatever it showed on the first.
     */
    private fun drift(tasks: Map<String, Task>, firstDay: Boolean): Map<String, Task> = tasks.mapValues { (_, t) ->
        when {
            t.type == TaskType.HABIT && (!firstDay || t.plusCount == 0) ->
                t.copy(value = Engine.clampValue(t.value - Constants.HABIT_IDLE_VALUE_STEP))
            t.type == TaskType.TODO && !t.completed -> t.copy(value = Engine.clampValue(t.value - Constants.TODO_AGE_VALUE_STEP))
            else -> t
        }
    }

    fun run(state: GameState, clock: Clock): EngineResult {
        val today = GameDay.today(clock, state.dayStartHour)
        val last = state.character.lastRolloverDate
        // First ever run: just anchor to today.
        if (last == null) return EngineResult(state.copy(character = state.character.copy(lastRolloverDate = today)))
        // Same day, or the clock moved backwards. The stored date never goes back: a day that was already
        // scored must not be scored again when the clock comes forward.
        if (!today.isAfter(last)) return EngineResult(state)

        val endedDays = ChronoUnit.DAYS.between(last, today).toInt()
        // Damage for at most MAX_DAMAGE_DAYS days; a longer absence is treated as paused.
        val damaging = !state.paused && endedDays <= Constants.MAX_DAMAGE_DAYS

        var s = state
        val changes = mutableListOf<Change>()
        val milestones = mutableListOf<Milestone>()

        if (damaging) {
            for (offset in 0 until endedDays) {
                val day = last.plusDays(offset.toLong())
                for (task in s.tasks.values.filter { it.type == TaskType.DAILY }.sortedBy { it.id }) {
                    val sched = task.schedule ?: continue
                    // Only the first processed day can already be ticked; later days start fresh.
                    val done = task.doneToday && offset == 0
                    if (!sched.isDue(day) || done) continue
                    val cur = s.tasks.getValue(task.id)
                    val after = cur.copy(streak = 0)
                    val dmg = Engine.damageFor(cur.difficulty, s.bonuses)
                    val applied = Engine.applyDelta(s.character, 0, 0, -dmg)
                    changes += Change(
                        taskId = cur.id, kind = ChangeKind.MISSED, timestamp = clock.instant(),
                        xp = 0, gold = 0, health = -dmg,
                        characterBefore = s.character, characterAfter = applied.character,
                        taskBefore = cur, taskAfter = after, day = day,
                    )
                    milestones += applied.milestones
                    s = s.copy(character = applied.character, tasks = s.tasks + (cur.id to after))
                }
                s = s.copy(tasks = drift(s.tasks, firstDay = offset == 0))
                // The boss runs out of time at the end of its deadline day, after that day's ticks counted.
                val q = s.quest
                if (s.features.quests && q != null && q.outcome == QuestOutcome.ACTIVE && day == q.deadlineDate) {
                    s = s.copy(quest = q.copy(outcome = QuestOutcome.ESCAPED, endDate = day))
                    milestones += Milestone.QUEST_ESCAPED
                }
            }
        }

        // Days that do not count (pause, a long absence, quests switched off) never cost the player the boss:
        // the deadline moves with them. Counted days were handled in the loop above.
        val q = s.quest
        if (q != null && q.outcome == QuestOutcome.ACTIVE && (!damaging || !s.features.quests)) {
            s = s.copy(quest = q.copy(deadlineDate = q.deadlineDate.plusDays(endedDays.toLong())))
        }
        // Mana comes back with the days, even on a holiday: it is not damage.
        val cls = s.character
        if (s.features.classes && cls.classId != null) {
            val max = Classes.manaMax(cls)
            val regen = Constants.MANA_REGEN_PER_DAY.toLong() * endedDays
            // Never lowers mana that is already above the cap (for example after a Mind point was refunded by backup edits).
            val regained = minOf(max.toLong(), cls.mana + regen).toInt()
            s = s.copy(character = cls.copy(mana = maxOf(cls.mana, regained)))
        }

        // New day: reset per-day fields.
        val reset = s.tasks.mapValues { (_, t) ->
            when (t.type) {
                TaskType.HABIT -> t.copy(plusCount = 0, minusCount = 0)
                TaskType.DAILY -> t.copy(doneToday = false)
                TaskType.TODO -> t
            }
        }
        s = s.copy(tasks = reset, character = s.character.copy(lastRolloverDate = today))
        return EngineResult(s, changes, milestones)
    }
}
