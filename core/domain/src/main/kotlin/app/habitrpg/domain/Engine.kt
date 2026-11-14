package app.habitrpg.domain

import java.time.Clock
import java.time.LocalDate
import kotlin.math.min
import kotlin.math.roundToInt

/** The only place game rules live. Pure: state, event and clock in; new state and changes out. */
object Engine {

    fun reduce(state: GameState, event: Event, clock: Clock): EngineResult = when (event) {
        is Event.HabitPlus -> habit(state, event.taskId, plus = true, clock)
        is Event.HabitMinus -> habit(state, event.taskId, plus = false, clock)
        is Event.CompleteDaily -> complete(state, event.taskId, TaskType.DAILY, clock)
        is Event.CompleteTodo -> complete(state, event.taskId, TaskType.TODO, clock)
        is Event.Undo -> undo(state, event.change, clock)
        is Event.Revert -> revert(state, event, clock)
        is Event.Spend -> spend(state, event.amount)
        is Event.StartQuest -> startQuest(state, event.bossId, clock)
        Event.DismissQuest -> dismissQuest(state)
        is Event.ChooseClass -> chooseClass(state, event.classId)
        is Event.SpendStat -> spendStat(state, event.stat)
        Event.Respec -> respec(state)
        is Event.CastSkill -> cast(state, event.skillId, clock)
        Event.AnchorToday -> Rollover.anchor(state, clock)
        Event.Rollover -> Rollover.run(state, clock)
    }

    private fun spend(state: GameState, amount: Int): EngineResult =
        if (amount < 0 || amount > state.character.gold) EngineResult(state)
        else EngineResult(state.copy(character = state.character.copy(gold = state.character.gold - amount)))

    // ---- Quests ----

    private fun startQuest(state: GameState, bossId: String, clock: Clock): EngineResult {
        val boss = QuestCatalog.byId(bossId) ?: return EngineResult(state)
        if (!state.features.quests || state.quest != null) return EngineResult(state)
        return EngineResult(state.copy(quest = QuestCatalog.start(boss, today(state, clock))))
    }

    private fun dismissQuest(state: GameState): EngineResult {
        val q = state.quest ?: return EngineResult(state)
        return if (q.outcome == QuestOutcome.ACTIVE) EngineResult(state) else EngineResult(state.copy(quest = null))
    }

    class BossHit(val damage: Int, val quest: Quest, val rewardXp: Int, val rewardGold: Int, val won: Boolean)

    /** Removes up to [amount] hp from the active boss. Null when quests are off or no boss is active. */
    private fun hitBoss(state: GameState, amount: Int, clock: Clock): BossHit? {
        val q = state.quest ?: return null
        if (!state.features.quests || q.outcome != QuestOutcome.ACTIVE || q.hp <= 0) return null
        val dmg = min(amount, q.hp)
        val hp = q.hp - dmg
        val won = hp == 0
        val boss = QuestCatalog.byId(q.bossId)
        return BossHit(
            dmg,
            if (won) q.copy(hp = 0, outcome = QuestOutcome.WON, endDate = today(state, clock)) else q.copy(hp = hp),
            if (won) boss?.rewardXp ?: 0 else 0, if (won) boss?.rewardGold ?: 0 else 0, won,
        )
    }

    /** Gives back boss hp when its change is undone. A finished-and-dismissed or escaped quest stays as it is. */
    private fun restoreBoss(state: GameState, damage: Int): GameState {
        val q = state.quest ?: return state
        if (damage <= 0 || q.outcome == QuestOutcome.ESCAPED) return state
        val max = QuestCatalog.byId(q.bossId)?.hp ?: return state
        return state.copy(quest = q.copy(hp = (q.hp + damage).coerceAtMost(max), outcome = QuestOutcome.ACTIVE, endDate = null))
    }

    // ---- Classes ----

    private fun chooseClass(state: GameState, id: String): EngineResult {
        val c = state.character
        if (!state.features.classes || c.classId != null || Classes.byId(id) == null || c.level < Constants.CLASS_UNLOCK_LEVEL) {
            return EngineResult(state)
        }
        val chosen = c.copy(classId = id)
        return EngineResult(state.copy(character = chosen.copy(mana = Classes.manaMax(chosen))))
    }

    private fun spendStat(state: GameState, stat: Stat): EngineResult {
        val c = state.character
        if (!state.features.classes || c.classId == null || Classes.freePoints(c) <= 0) return EngineResult(state)
        val next = when (stat) {
            Stat.MIGHT -> c.copy(statMight = c.statMight + 1)
            Stat.MIND -> c.copy(statMind = c.statMind + 1)
            Stat.VIGOR -> c.copy(statVigor = c.statVigor + 1)
        }
        return EngineResult(state.copy(character = next))
    }

    private fun respec(state: GameState): EngineResult {
        val c = state.character
        if (!state.features.classes || c.classId == null || c.gold < Constants.RESPEC_COST) return EngineResult(state)
        return EngineResult(
            state.copy(
                character = c.copy(
                    classId = null, mana = 0, statMight = 0, statMind = 0, statVigor = 0, gold = c.gold - Constants.RESPEC_COST,
                ),
            ),
        )
    }

    private fun cast(state: GameState, skillId: String, clock: Clock): EngineResult {
        val c = state.character
        if (!state.features.classes) return EngineResult(state)
        val skill = Classes.skill(c.classId, skillId) ?: return EngineResult(state)
        if (c.mana < skill.cost) return EngineResult(state)
        var s = state
        var xp = 0
        var gold = 0
        var heal = 0
        val milestones = mutableListOf<Milestone>()
        when (val e = skill.effect) {
            is SkillEffect.BossDamage -> {
                // Nothing to hit: keep the mana.
                val hit = hitBoss(s, Math.round(e.amount * (1 + state.bonuses.bossDamagePct / 100.0)).toInt(), clock) ?: return EngineResult(state)
                s = s.copy(quest = hit.quest)
                xp += hit.rewardXp; gold += hit.rewardGold
                if (hit.won) milestones += Milestone.QUEST_WON
            }
            is SkillEffect.Heal -> heal = e.amount
            SkillEffect.HealFull -> heal = Constants.MAX_HEALTH
            is SkillEffect.Xp -> xp += e.amount
            is SkillEffect.Gold -> gold += e.amount
        }
        val applied = applyDelta(s.character.copy(mana = c.mana - skill.cost), xp, gold, heal)
        return EngineResult(s.copy(character = applied.character), emptyList(), milestones + applied.milestones)
    }

    private fun revert(state: GameState, e: Event.Revert, clock: Clock): EngineResult {
        val task = state.tasks[e.taskId] ?: return EngineResult(state)
        val after = when (e.kind) {
            ChangeKind.PLUS -> task.copy(plusCount = (task.plusCount - 1).coerceAtLeast(0), value = clampValue(task.value - Constants.VALUE_STEP))
            ChangeKind.MINUS -> task.copy(minusCount = (task.minusCount - 1).coerceAtLeast(0), value = clampValue(task.value + Constants.VALUE_STEP))
            ChangeKind.DONE ->
                if (task.type == TaskType.DAILY) task.copy(doneToday = false, streak = (task.streak - 1).coerceAtLeast(0))
                else task.copy(completed = false)
            else -> return EngineResult(state)
        }
        val character = applyDelta(state.character, -e.xp, -e.gold, -e.health, allowLevelEffects = false).character
        val record = Change(
            taskId = e.taskId, kind = ChangeKind.UNDONE, timestamp = clock.instant(),
            xp = -e.xp, gold = -e.gold, health = -e.health,
            characterBefore = state.character, characterAfter = character, taskBefore = task, taskAfter = after,
            bossDamage = -e.bossDamage,
        )
        val restored = restoreBoss(state, e.bossDamage)
        return EngineResult(restored.copy(character = character, tasks = state.tasks + (after.id to after)), listOf(record))
    }

    fun streakBonus(streak: Int): Double =
        min(Constants.STREAK_BONUS_CAP, 1.0 + Constants.STREAK_BONUS_PER_DAY * streak)

    /** What a task's value does to its rewards: 1.0 at value 0, down to 0.5 when farmed, up to 1.5 when neglected. */
    fun valueMultiplier(value: Double): Double =
        (1.0 - Constants.VALUE_REWARD_PER_POINT * value).coerceIn(Constants.VALUE_MULTIPLIER_MIN, Constants.VALUE_MULTIPLIER_MAX)

    fun clampValue(value: Double): Double = if (value.isNaN()) 0.0 else value.coerceIn(Constants.VALUE_MIN, Constants.VALUE_MAX)

    fun xpFor(d: Difficulty, bonus: Double = 1.0, gear: Bonuses = Bonuses.NONE, value: Double = 0.0) =
        (Constants.BASE_XP * d.multiplier * bonus * (1 + gear.xpPct / 100.0) * valueMultiplier(value)).roundToInt()
    fun goldFor(d: Difficulty, gear: Bonuses = Bonuses.NONE, value: Double = 0.0) =
        (Constants.BASE_GOLD * d.multiplier * (1 + gear.goldPct / 100.0) * valueMultiplier(value)).roundToInt()
    /** Armour trims damage but never below 1, so a missed daily always costs something. */
    fun damageFor(d: Difficulty, gear: Bonuses = Bonuses.NONE) =
        (Constants.DAMAGE_BASE * d.multiplier * (1 - gear.damageReductionPct / 100.0)).roundToInt().coerceAtLeast(1)

    private fun habit(state: GameState, id: String, plus: Boolean, clock: Clock): EngineResult {
        val task = state.tasks[id] ?: return EngineResult(state)
        if (task.type != TaskType.HABIT) return EngineResult(state)
        if (plus && !task.plusEnabled || !plus && !task.minusEnabled) return EngineResult(state)
        val after = if (plus) task.copy(plusCount = task.plusCount + 1, value = clampValue(task.value + Constants.VALUE_STEP))
        else task.copy(minusCount = task.minusCount + 1, value = clampValue(task.value - Constants.VALUE_STEP))
        return if (plus) {
            // Paid at the value the habit had before this tap.
            score(
                state, task, after, ChangeKind.PLUS, xpFor(task.difficulty, 1.0, state.bonuses, task.value),
                goldFor(task.difficulty, state.bonuses, task.value), 0, clock, hitsBoss = true,
            )
        } else {
            score(state, task, after, ChangeKind.MINUS, 0, 0, -damageFor(task.difficulty, state.bonuses), clock)
        }
    }

    private fun complete(state: GameState, id: String, type: TaskType, clock: Clock): EngineResult {
        val task = state.tasks[id] ?: return EngineResult(state)
        if (task.type != type) return EngineResult(state)
        val after: Task
        val xp: Int
        if (type == TaskType.DAILY) {
            if (task.doneToday) return EngineResult(state)
            after = task.copy(doneToday = true, streak = task.streak + 1)
            xp = xpFor(task.difficulty, streakBonus(task.streak), state.bonuses)
        } else {
            if (task.completed) return EngineResult(state)
            after = task.copy(completed = true)
            xp = xpFor(task.difficulty, 1.0, state.bonuses, task.value)
        }
        // Dailies are not scaled by value: their streak bonus is their reward curve.
        val gold = goldFor(task.difficulty, state.bonuses, if (type == TaskType.TODO) task.value else 0.0)
        return score(state, task, after, ChangeKind.DONE, xp, gold, 0, clock, hitsBoss = true)
    }

    private fun score(
        state: GameState, before: Task, after: Task, kind: ChangeKind,
        baseXp: Int, baseGold: Int, health: Int, clock: Clock, hitsBoss: Boolean = false,
    ): EngineResult {
        // The killing blow pays the boss reward inside this change, so undo reverses it with everything else.
        val hit = if (hitsBoss) hitBoss(state, QuestCatalog.damageFor(before.difficulty, state.bonuses), clock) else null
        val xp = baseXp + (hit?.rewardXp ?: 0)
        val gold = baseGold + (hit?.rewardGold ?: 0)
        val applied = applyDelta(state.character, xp, gold, health)
        val change = Change(
            taskId = before.id, kind = kind, timestamp = clock.instant(),
            xp = xp, gold = gold, health = health,
            characterBefore = state.character, characterAfter = applied.character,
            taskBefore = before, taskAfter = after, bossDamage = hit?.damage ?: 0,
        )
        val milestones = if (hit?.won == true) applied.milestones + Milestone.QUEST_WON else applied.milestones
        return EngineResult(
            state.copy(character = applied.character, tasks = state.tasks + (after.id to after), quest = hit?.quest ?: state.quest),
            listOf(change), milestones,
        )
    }

    /** Reverses a change exactly: restores the snapshots when it was the latest, else reverses the deltas. */
    private fun undo(state: GameState, change: Change, clock: Clock): EngineResult {
        val current = state.tasks[change.taskId]
        val restoredTask = change.taskBefore.let { b ->
            // Keep unrelated live fields (e.g. other habit counter) from the current task where the change did not touch them.
            if (current == null) b else when (change.kind) {
                ChangeKind.PLUS -> current.copy(plusCount = b.plusCount, value = b.value)
                ChangeKind.MINUS -> current.copy(minusCount = b.minusCount, value = b.value)
                else -> current.copy(doneToday = b.doneToday, streak = b.streak, completed = b.completed)
            }
        }
        val character = if (state.character == change.characterAfter) {
            change.characterBefore
        } else {
            applyDelta(state.character, -change.xp, -change.gold, -change.health, allowLevelEffects = false).character
        }
        val record = change.copy(
            kind = ChangeKind.UNDONE, timestamp = clock.instant(),
            xp = -change.xp, gold = -change.gold, health = -change.health,
            characterBefore = state.character, characterAfter = character,
            taskBefore = current ?: change.taskAfter, taskAfter = restoredTask, bossDamage = -change.bossDamage,
        )
        return EngineResult(
            restoreBoss(state, change.bossDamage).copy(character = character, tasks = state.tasks + (restoredTask.id to restoredTask)),
            listOf(record),
        )
    }

    class Applied(val character: Character, val milestones: List<Milestone>)

    /**
     * Applies signed deltas. Level-ups refill health; reaching zero health costs a level and half the gold.
     * With [allowLevelEffects] false (used by undo) deltas are only clamped, never trigger rewards or penalties.
     */
    fun applyDelta(c: Character, xp: Int, gold: Int, health: Int, allowLevelEffects: Boolean = true): Applied {
        val milestones = mutableListOf<Milestone>()
        var level = c.level
        var curXp = c.xp + xp
        var hp = (c.health + health).coerceAtMost(Constants.MAX_HEALTH)
        var g = (c.gold + gold).coerceAtLeast(0)

        while (curXp >= Constants.xpToNextLevel(level)) {
            curXp -= Constants.xpToNextLevel(level)
            level++
            if (allowLevelEffects) { hp = Constants.MAX_HEALTH; milestones += Milestone.LEVEL_UP }
        }
        while (curXp < 0 && level > 1) {
            level--
            curXp += Constants.xpToNextLevel(level)
        }
        curXp = curXp.coerceAtLeast(0)

        if (hp <= 0) {
            if (allowLevelEffects) {
                level = (level - 1).coerceAtLeast(1)
                g /= 2
                hp = Constants.MAX_HEALTH
                milestones += Milestone.HEALTH_ZERO
            } else {
                hp = 0
            }
        }
        return Applied(c.copy(level = level, xp = curXp, health = hp, gold = g), milestones)
    }

    fun today(state: GameState, clock: Clock): LocalDate = GameDay.today(clock, state.dayStartHour)
}
