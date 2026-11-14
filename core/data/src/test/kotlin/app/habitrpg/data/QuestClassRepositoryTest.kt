package app.habitrpg.data

import app.habitrpg.data.db.CharacterEntity
import app.habitrpg.data.repo.Feedback
import app.habitrpg.data.repo.TaskDraft
import app.habitrpg.domain.Difficulty
import app.habitrpg.domain.Event
import app.habitrpg.domain.Milestone
import app.habitrpg.domain.QuestCatalog
import app.habitrpg.domain.QuestOutcome
import app.habitrpg.domain.Schedule
import app.habitrpg.domain.TaskType
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuestClassRepositoryTest : RepoTestBase() {
    private val slime = QuestCatalog.bosses.first()

    @Test fun quickStartedQuestPersistsAndTapsRemoveHp() = runTest {
        val id = tasks.save(TaskDraft(type = TaskType.HABIT, title = "h", difficulty = Difficulty.MEDIUM))
        game.dispatch(Event.StartQuest(slime.id))
        game.dispatch(Event.HabitPlus(id))
        val q = game.observeQuest().first()!!
        assertEquals(slime.hp - 15, q.hp)
        assertEquals(15, db.eventDao().getAll().single().bossDamage)
    }

    @Test fun killingBlowUndoneFromTheLogAfterARestartGivesTheBossBack() = runTest {
        val id = tasks.save(TaskDraft(type = TaskType.TODO, title = "t", difficulty = Difficulty.HARD))
        game.dispatch(Event.StartQuest(slime.id))
        db.questDao().upsert(db.questDao().get()!!.copy(hp = 5))
        val r = game.dispatch(Event.CompleteTodo(id))
        assertTrue(Milestone.QUEST_WON in r.milestones)
        assertEquals(QuestOutcome.WON, game.observeQuest().first()!!.outcome)
        val goldWithReward = game.observeCharacter().first().gold
        assertTrue(goldWithReward >= slime.rewardGold)
        game.uncomplete(id) // reads the event log, not a snapshot
        val q = game.observeQuest().first()!!
        assertEquals(QuestOutcome.ACTIVE, q.outcome)
        assertEquals(5, q.hp)
        assertEquals(0, game.observeCharacter().first().gold)
    }

    @Test fun dismissingMovesItToHistory() = runTest {
        game.dispatch(Event.StartQuest(slime.id))
        db.questDao().upsert(db.questDao().get()!!.copy(hp = 0, outcome = "WON", endDate = "2026-03-02"))
        game.dispatch(Event.DismissQuest)
        assertNull(game.observeQuest().first())
        assertEquals(listOf("WON"), db.questDao().getHistory().map { it.outcome })
    }

    @Test fun escapeIsPersistedOnceAcrossRollovers() = runTest {
        tasks.save(TaskDraft(type = TaskType.DAILY, title = "d", schedule = Schedule.Weekdays.EVERY_DAY))
        game.dispatch(Event.Rollover)
        game.dispatch(Event.StartQuest(slime.id))
        db.questDao().upsert(db.questDao().get()!!.copy(deadlineDate = "2026-03-02"))
        clock.at(LocalDate.of(2026, 3, 3))
        val first = game.dispatch(Event.Rollover)
        val second = game.dispatch(Event.Rollover)
        assertTrue(Milestone.QUEST_ESCAPED in first.milestones)
        assertTrue(second.milestones.isEmpty())
        assertEquals(QuestOutcome.ESCAPED, game.observeQuest().first()!!.outcome)
    }

    @Test fun questsSwitchOffStopsDamageAndKeepsTheBoss() = runTest {
        val id = tasks.save(TaskDraft(type = TaskType.HABIT, title = "h"))
        game.dispatch(Event.StartQuest(slime.id))
        settings.setQuests(false)
        game.dispatch(Event.HabitPlus(id))
        assertEquals(slime.hp, game.observeQuest().first()!!.hp)
    }

    @Test fun classChoiceStatsAndSkillsPersistAndBonusesReachTheEngine() = runTest {
        db.characterDao().upsert(CharacterEntity(1, 12, 0, 50, 0, "2026-03-02"))
        game.dispatch(Event.ChooseClass("warrior"))
        game.dispatch(Event.SpendStat(app.habitrpg.domain.Stat.MIGHT))
        var c = game.observeCharacter().first()
        assertEquals("warrior", c.classId); assertEquals(1, c.statMight); assertEquals(24, c.mana)
        val id = tasks.save(TaskDraft(type = TaskType.TODO, title = "t", difficulty = Difficulty.MEDIUM))
        game.dispatch(Event.StartQuest(slime.id))
        game.dispatch(Event.CompleteTodo(id))
        // 15 base, +10% class perk, +1% might = 11% -> 16.65 -> 17
        assertEquals(17, db.eventDao().getAll().single().bossDamage)
        game.dispatch(Event.CastSkill("strike"))
        c = game.observeCharacter().first()
        assertEquals(14, c.mana)
        settings.setClasses(false)
        game.dispatch(Event.CastSkill("second_wind"))
        assertEquals(14, game.observeCharacter().first().mana)
    }

    @Test fun castingEmitsFeedbackOnlyWhenItWorked() = runTest {
        db.characterDao().upsert(CharacterEntity(1, 10, 0, 40, 0, "2026-03-02", "healer", 30))
        val seen = mutableListOf<Feedback>()
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).let { s ->
            s.launch { feedback.events.collect { seen += it } }
        }
        game.dispatch(Event.CastSkill("mend"))
        game.dispatch(Event.CastSkill("renew")) // 25 mana, only 20 left
        job.cancel()
        assertEquals(1, seen.filterIsInstance<Feedback.Cast>().size)
        assertEquals(50 - 25, game.observeCharacter().first().health.let { 50 - 25 })
    }
}
