package app.habitrpg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValueTest {
    private val clock = clockAt()
    private fun reduce(s: GameState, e: Event) = Engine.reduce(s, e, clock)
    private fun next(s: GameState, days: Int = 1) = Rollover.run(s, clockAt(D0.plusDays(days.toLong()))).state

    @Test fun multiplierIsOneAtZeroAndStaysBetweenHalfAndOneAndAHalf() {
        assertEquals(1.0, Engine.valueMultiplier(0.0), 1e-9)
        assertEquals(0.75, Engine.valueMultiplier(5.0), 1e-9)
        assertEquals(0.5, Engine.valueMultiplier(10.0), 1e-9)
        assertEquals(0.5, Engine.valueMultiplier(500.0), 1e-9)
        assertEquals(1.5, Engine.valueMultiplier(-10.0), 1e-9)
        assertEquals(1.5, Engine.valueMultiplier(-500.0), 1e-9)
    }

    @Test fun valueNeverLeavesItsRangeAndNanBecomesZero() {
        assertEquals(10.0, Engine.clampValue(11.0), 0.0)
        assertEquals(-10.0, Engine.clampValue(-11.0), 0.0)
        assertEquals(0.0, Engine.clampValue(Double.NaN), 0.0)
    }

    @Test fun aNewHabitPaysTheBaseNumbersAndEachPlusMakesTheNextOneWorthLess() {
        var s = stateOf(habit("h", Difficulty.HARD))
        val first = reduce(s, Event.HabitPlus("h"))
        assertEquals(20, first.changes.single().xp)
        s = first.state
        assertEquals(1.0, s.tasks.getValue("h").value, 0.0)
        repeat(9) { s = reduce(s, Event.HabitPlus("h")).state }   // value is now 10
        val farmed = reduce(s, Event.HabitPlus("h")).changes.single()
        assertEquals("floor of half", 10, farmed.xp)
        assertEquals(4, farmed.gold)
    }

    @Test fun aMinusTapLowersValueSoTheHabitPaysMoreLater() {
        val s = reduce(stateOf(habit("h", Difficulty.HARD)), Event.HabitMinus("h")).state
        assertEquals(-1.0, s.tasks.getValue("h").value, 0.0)
        assertEquals(21, reduce(s, Event.HabitPlus("h")).changes.single().xp) // 20 x 1.05
    }

    @Test fun undoAndRevertPutTheValueBack() {
        val s = stateOf(habit("h"))
        val tapped = reduce(s, Event.HabitPlus("h"))
        val undone = reduce(tapped.state, Event.Undo(tapped.changes.single()))
        assertEquals(0.0, undone.state.tasks.getValue("h").value, 0.0)

        val c = tapped.changes.single()
        val reverted = reduce(tapped.state, Event.Revert("h", ChangeKind.PLUS, c.xp, c.gold, c.health))
        assertEquals(0.0, reverted.state.tasks.getValue("h").value, 0.0)

        val minus = reduce(s, Event.HabitMinus("h"))
        val back = reduce(minus.state, Event.Revert("h", ChangeKind.MINUS, 0, 0, minus.changes.single().health))
        assertEquals(0.0, back.state.tasks.getValue("h").value, 0.0)
    }

    @Test fun anIdleHabitIsWorthMoreEachDayAndATappedOneIsNot() {
        val tapped = habit("tapped").copy(plusCount = 2, value = 2.0)
        val idle = habit("idle").copy(value = 2.0)
        val after = next(stateOf(tapped, idle))
        assertEquals(2.0, after.tasks.getValue("tapped").value, 0.0)
        assertEquals(1.0, after.tasks.getValue("idle").value, 0.0)
    }

    @Test fun aGapOfTwoDaysCountsTheSecondDayAsIdleForEveryHabit() {
        val after = next(stateOf(habit("tapped").copy(plusCount = 2)), days = 2)
        assertEquals(-1.0, after.tasks.getValue("tapped").value, 0.0)
    }

    @Test fun anOpenTodoGrowsMoreValuableAndADoneOneDoesNot() {
        val after = next(stateOf(todo("open"), todo("done").copy(completed = true)), days = 2)
        assertEquals(-0.5, after.tasks.getValue("open").value, 0.0)
        assertEquals(0.0, after.tasks.getValue("done").value, 0.0)
    }

    @Test fun nothingDriftsWhilePausedOrAfterALongAbsence() {
        val tasks = arrayOf(habit("h"), todo("t"))
        assertEquals(0.0, next(stateOf(*tasks, paused = true)).tasks.getValue("h").value, 0.0)
        assertEquals(0.0, next(stateOf(*tasks), days = 3).tasks.getValue("h").value, 0.0)
        assertEquals(0.0, next(stateOf(*tasks), days = 3).tasks.getValue("t").value, 0.0)
    }

    @Test fun rolloverIsStillIdempotentWithValueDrift() {
        val once = Rollover.run(stateOf(habit("h"), todo("t")), clockAt(D0.plusDays(1)))
        val twice = Rollover.run(once.state, clockAt(D0.plusDays(1)))
        assertEquals(once.state, twice.state)
    }

    @Test fun anOldTodoPaysMoreWhenFinallyDone() {
        val s = stateOf(todo("t", Difficulty.HARD).copy(value = -10.0))
        assertEquals(30, reduce(s, Event.CompleteTodo("t")).changes.single().xp) // 20 x 1.5
    }

    @Test fun dailiesAreNotScaledByValue() {
        val s = stateOf(daily("d", Difficulty.HARD).copy(value = 10.0))
        assertEquals(20, reduce(s, Event.CompleteDaily("d")).changes.single().xp)
        assertEquals(8, reduce(s, Event.CompleteDaily("d")).changes.single().gold)
    }

    @Test fun valueStacksWithGearAndStreak() {
        val gear = Catalog.bonusesFor(listOf("weapon_sword_gold"))   // +15% XP
        val s = stateOf(habit("h", Difficulty.HARD).copy(value = 4.0)).copy(bonuses = gear)
        assertEquals(18, reduce(s, Event.HabitPlus("h")).changes.single().xp) // 20 x 1.15 x 0.8 = 18.4
    }

    @Test fun theHintAppearsOnlyWhenValueIsFarFromZero() {
        assertNull(Worth.of(0.0)); assertNull(Worth.of(2.0)); assertNull(Worth.of(-2.0))
        assertEquals(Worth.LESS, Worth.of(2.5))
        assertEquals(Worth.MORE, Worth.of(-3.0))
    }
}
