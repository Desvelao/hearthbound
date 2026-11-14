package app.habitrpg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringTest {
    private val clock = clockAt()

    private fun run(s: GameState, e: Event) = Engine.reduce(s, e, clock)

    @Test fun difficultyMultipliers() {
        assertEquals(5, Engine.xpFor(Difficulty.TRIVIAL))
        assertEquals(10, Engine.xpFor(Difficulty.EASY))
        assertEquals(15, Engine.xpFor(Difficulty.MEDIUM))
        assertEquals(20, Engine.xpFor(Difficulty.HARD))
        assertEquals(2, Engine.goldFor(Difficulty.TRIVIAL))
        assertEquals(4, Engine.goldFor(Difficulty.EASY))
        assertEquals(6, Engine.goldFor(Difficulty.MEDIUM))
        assertEquals(8, Engine.goldFor(Difficulty.HARD))
    }

    @Test fun mediumTaskGives15XpAndSixGold() {
        val r = run(stateOf(todo(diff = Difficulty.MEDIUM)), Event.CompleteTodo("t"))
        assertEquals(15, r.state.character.xp)
        assertEquals(6, r.state.character.gold)
    }

    @Test fun streakBonusGrowsAndCaps() {
        assertEquals(1.0, Engine.streakBonus(0), 1e-9)
        assertEquals(1.1, Engine.streakBonus(5), 1e-9)
        assertEquals(1.4, Engine.streakBonus(20), 1e-9)
        assertEquals(1.4, Engine.streakBonus(200), 1e-9)
    }

    @Test fun dailyUsesStreakBonusAndIncrementsStreak() {
        val r = run(stateOf(daily(diff = Difficulty.HARD, streak = 20)), Event.CompleteDaily("d"))
        assertEquals(28, r.state.character.xp) // 20 * 1.4
        assertEquals(21, r.state.tasks.getValue("d").streak)
        assertTrue(r.state.tasks.getValue("d").doneToday)
    }

    @Test fun dailyCannotBeCompletedTwice() {
        val once = run(stateOf(daily()), Event.CompleteDaily("d"))
        val twice = run(once.state, Event.CompleteDaily("d"))
        assertEquals(once.state, twice.state)
        assertTrue(twice.changes.isEmpty())
    }

    @Test fun xpToNextLevelCurve() {
        assertEquals(100, Constants.xpToNextLevel(1))
        assertEquals(125, Constants.xpToNextLevel(2))
        assertEquals(200, Constants.xpToNextLevel(5))
    }

    @Test fun levelUpRefillsHealthAndCarriesXp() {
        val c = Character(xp = 95, health = 10, lastRolloverDate = D0)
        val r = run(stateOf(todo(diff = Difficulty.MEDIUM), character = c), Event.CompleteTodo("t"))
        assertEquals(2, r.state.character.level)
        assertEquals(10, r.state.character.xp)
        assertEquals(Constants.MAX_HEALTH, r.state.character.health)
        assertTrue(Milestone.LEVEL_UP in r.milestones)
    }

    @Test fun minusTapLosesHealthByDifficulty() {
        val r = run(stateOf(habit(diff = Difficulty.MEDIUM)), Event.HabitMinus("h"))
        assertEquals(Constants.MAX_HEALTH - 3, r.state.character.health)
        assertEquals(1, r.state.tasks.getValue("h").minusCount)
    }

    @Test fun disabledHabitButtonDoesNothing() {
        val s = stateOf(habit().copy(minusEnabled = false))
        assertTrue(run(s, Event.HabitMinus("h")).changes.isEmpty())
    }

    @Test fun healthZeroCostsLevelAndHalfGold() {
        val c = Character(level = 3, xp = 40, health = 2, gold = 51, lastRolloverDate = D0)
        val r = run(stateOf(habit(), character = c), Event.HabitMinus("h"))
        assertEquals(2, r.state.character.level)
        assertEquals(25, r.state.character.gold)
        assertEquals(Constants.MAX_HEALTH, r.state.character.health)
        assertTrue(Milestone.HEALTH_ZERO in r.milestones)
    }

    @Test fun healthZeroNeverDropsBelowLevelOne() {
        val c = Character(level = 1, health = 1, gold = 9, lastRolloverDate = D0)
        val r = run(stateOf(habit(), character = c), Event.HabitMinus("h"))
        assertEquals(1, r.state.character.level)
        assertEquals(4, r.state.character.gold)
    }

    @Test fun undoRestoresEverythingExactlyIncludingLevelUp() {
        val c = Character(xp = 95, health = 10, gold = 3, lastRolloverDate = D0)
        val start = stateOf(todo(diff = Difficulty.MEDIUM), character = c)
        val done = run(start, Event.CompleteTodo("t"))
        val undone = run(done.state, Event.Undo(done.changes.single()))
        assertEquals(start, undone.state)
    }

    @Test fun undoDailyRestoresStreak() {
        val start = stateOf(daily(streak = 4))
        val done = run(start, Event.CompleteDaily("d"))
        assertEquals(start, run(done.state, Event.Undo(done.changes.single())).state)
    }

    @Test fun undoAfterOtherActionsReversesDeltas() {
        val start = stateOf(todo("a"), todo("b"))
        val a = run(start, Event.CompleteTodo("a"))
        val b = run(a.state, Event.CompleteTodo("b"))
        val undoneA = run(b.state, Event.Undo(a.changes.single()))
        assertEquals(10, undoneA.state.character.xp) // only b remains
        assertEquals(4, undoneA.state.character.gold)
        assertEquals(false, undoneA.state.tasks.getValue("a").completed)
        assertEquals(true, undoneA.state.tasks.getValue("b").completed)
    }

    @Test fun undoHabitMinusRestoresHealth() {
        val start = stateOf(habit(diff = Difficulty.HARD))
        val m = run(start, Event.HabitMinus("h"))
        assertEquals(start, run(m.state, Event.Undo(m.changes.single())).state)
    }
}

class RevertAndSpendTest {
    private val clock = clockAt()
    private fun run(s: GameState, e: Event) = Engine.reduce(s, e, clock)

    @Test fun revertDoneTodoRestoresTaskAndRewards() {
        val start = stateOf(todo(diff = Difficulty.MEDIUM), character = Character(xp = 3, gold = 1, lastRolloverDate = D0))
        val done = run(start, Event.CompleteTodo("t"))
        val c = done.changes.single()
        val back = run(done.state, Event.Revert("t", ChangeKind.DONE, c.xp, c.gold, c.health))
        assertEquals(start, back.state)
        assertEquals(ChangeKind.UNDONE, back.changes.single().kind)
    }

    @Test fun revertDailyLowersStreakAndClearsDone() {
        val start = stateOf(daily(streak = 4))
        val done = run(start, Event.CompleteDaily("d"))
        val c = done.changes.single()
        assertEquals(start, run(done.state, Event.Revert("d", ChangeKind.DONE, c.xp, c.gold, c.health)).state)
    }

    @Test fun revertAfterLevelUpLowersLevelAgain() {
        val start = stateOf(todo(diff = Difficulty.MEDIUM), character = Character(xp = 95, lastRolloverDate = D0))
        val done = run(start, Event.CompleteTodo("t"))
        assertEquals(2, done.state.character.level)
        val c = done.changes.single()
        val back = run(done.state, Event.Revert("t", ChangeKind.DONE, c.xp, c.gold, c.health))
        assertEquals(1, back.state.character.level)
        assertEquals(95, back.state.character.xp)
    }

    @Test fun revertOfUnknownTaskOrMissedKindIsIgnored() {
        val s = stateOf(todo())
        assertTrue(run(s, Event.Revert("nope", ChangeKind.DONE, 1, 1, 0)).changes.isEmpty())
        assertTrue(run(s, Event.Revert("t", ChangeKind.MISSED, 0, 0, -2)).changes.isEmpty())
    }

    @Test fun spendDeductsGoldOnlyWhenAffordable() {
        val s = stateOf(character = Character(gold = 10, lastRolloverDate = D0))
        assertEquals(4, run(s, Event.Spend(6)).state.character.gold)
        assertEquals(10, run(s, Event.Spend(11)).state.character.gold)
        assertEquals(0, run(s, Event.Spend(10)).state.character.gold)
    }
}
