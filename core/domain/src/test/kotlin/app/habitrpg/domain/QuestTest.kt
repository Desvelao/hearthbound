package app.habitrpg.domain

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class QuestTest {
    private val clock = clockAt()
    private val slime = QuestCatalog.bosses.first()

    private fun withQuest(vararg tasks: Task, hp: Int = slime.hp, features: Features = Features(), paused: Boolean = false) =
        stateOf(*tasks, paused = paused).copy(
            quest = Quest(slime.id, hp, D0, D0.plusDays(slime.days - 1L)), features = features,
        )

    private fun next(s: GameState, e: Event, c: java.time.Clock = clock) = Engine.reduce(s, e, c)

    @Test fun startSetsDeadlineAndRefusesSecond() {
        val s = next(stateOf(), Event.StartQuest(slime.id)).state
        assertEquals(D0.plusDays(slime.days - 1L), s.quest?.deadlineDate)
        assertEquals(s, next(s, Event.StartQuest("boss_frost_wyrm")).state)
    }

    @Test fun plusTapDamagesBossByDifficulty() {
        val r = next(withQuest(habit(diff = Difficulty.MEDIUM)), Event.HabitPlus("h"))
        assertEquals(15, r.changes.single().bossDamage)
        assertEquals(slime.hp - 15, r.state.quest?.hp)
    }

    @Test fun minusAndSwitchOffDoNothing() {
        val m = next(withQuest(habit()), Event.HabitMinus("h"))
        assertEquals(slime.hp, m.state.quest?.hp)
        val off = next(withQuest(habit(), features = Features(quests = false)), Event.HabitPlus("h"))
        assertEquals(0, off.changes.single().bossDamage)
        assertEquals(slime.hp, off.state.quest?.hp)
    }

    @Test fun killingBlowPaysOnceAndUndoReversesIt() {
        val start = withQuest(todo(diff = Difficulty.HARD), hp = 5)
        val r = next(start, Event.CompleteTodo("t"))
        val change = r.changes.single()
        assertEquals(5, change.bossDamage)
        assertEquals(QuestOutcome.WON, r.state.quest?.outcome)
        assertTrue(Milestone.QUEST_WON in r.milestones)
        assertEquals(Engine.xpFor(Difficulty.HARD) + slime.rewardXp, change.xp)
        val undone = next(r.state, Event.Undo(change)).state
        assertEquals(QuestOutcome.ACTIVE, undone.quest?.outcome)
        assertEquals(5, undone.quest?.hp)
        assertEquals(start.character, undone.character)
    }

    @Test fun dismissOnlyWhenFinished() {
        val active = withQuest()
        assertNotNull(next(active, Event.DismissQuest).state.quest)
        val won = active.copy(quest = active.quest!!.copy(outcome = QuestOutcome.WON))
        assertNull(next(won, Event.DismissQuest).state.quest)
    }

    @Test fun bossEscapesAfterDeadlineDayExactlyOnce() {
        val s = withQuest(daily(done = true))
        val c = clockAt(D0.plusDays(2)) // two ended days: still counted, so the deadline day passes
        val short = s.copy(quest = s.quest!!.copy(deadlineDate = D0.plusDays(1)))
        val r = next(short, Event.Rollover, c)
        assertEquals(QuestOutcome.ESCAPED, r.state.quest?.outcome)
        assertTrue(Milestone.QUEST_ESCAPED in r.milestones)
        val again = next(r.state, Event.Rollover, c)
        assertTrue(again.milestones.isEmpty())
    }

    @Test fun pauseAndLongAbsenceMoveTheDeadline() {
        val s = withQuest(paused = true)
        val r = next(s, Event.Rollover, clockAt(D0.plusDays(2))).state
        assertEquals(QuestOutcome.ACTIVE, r.quest?.outcome)
        assertEquals(s.quest!!.deadlineDate.plusDays(2), r.quest?.deadlineDate)
        val away = next(withQuest(), Event.Rollover, clockAt(D0.plusDays(30))).state
        assertEquals(QuestOutcome.ACTIVE, away.quest?.outcome)
    }

    @Test fun switchOffFreezesTheDeadline() {
        val s = withQuest(features = Features(quests = false))
        val r = next(s, Event.Rollover, clockAt(D0.plusDays(2))).state
        assertEquals(s.quest!!.deadlineDate.plusDays(2), r.quest?.deadlineDate)
    }

    @Test fun clockBackNeverEscapesTwice() {
        val s = withQuest().let { it.copy(quest = it.quest!!.copy(deadlineDate = D0)) }
        val done = next(s, Event.Rollover, clockAt(D0.plusDays(1))).state
        assertEquals(QuestOutcome.ESCAPED, done.quest?.outcome)
        assertTrue(next(done, Event.Rollover, clockAt(D0)).milestones.isEmpty())
    }

    @Test fun catalogIdsMatchManifest() {
        val manifest = java.io.File("../../art/manifest.yaml").takeIf { it.exists() }?.readText() ?: return
        QuestCatalog.bosses.forEach { assertTrue(it.id, it.id in manifest) }
    }
}
