package app.habitrpg.data

import app.habitrpg.data.repo.StatsRepository
import app.habitrpg.data.repo.TagRepository
import app.habitrpg.data.repo.TaskDraft
import app.habitrpg.data.repo.ThemeMode
import app.habitrpg.domain.Event
import app.habitrpg.domain.TaskType
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class M5RepositoryTest : RepoTestBase() {

    // ---- settings ----

    @Test fun newSettingsDefaults() = runTest {
        val s = settings.current()
        assertEquals(ThemeMode.SYSTEM, s.themeMode)
        assertEquals("hair_short_brown", s.hair)
        assertEquals("body_default", s.body)
        assertFalse(s.onboardingDone)
    }

    @Test fun bodyPersists() = runTest {
        settings.setBody("body_female")
        assertEquals("body_female", settings.current().body)
    }

    @Test fun themeHairAndOnboardingPersist() = runTest {
        settings.setThemeMode(ThemeMode.DARK); settings.setHair("hair_spiky_brown"); settings.setOnboardingDone(true)
        val s = settings.current()
        assertEquals(ThemeMode.DARK, s.themeMode)
        assertEquals("hair_spiky_brown", s.hair)
        assertTrue(s.onboardingDone)
    }

    // ---- tags ----

    @Test fun tagsAreDeduplicatedIgnoringCaseAndTidied() = runTest {
        val tags = TagRepository(db)
        val a = tags.create("  #Health ")
        assertEquals(a, tags.create("health"))
        assertNull(tags.create("   "))
        assertEquals(listOf("Health"), tags.observe().first().map { it.name })
    }

    @Test fun taggedTasksKeepTheirTagsAndDeletingATagKeepsTheTask() = runTest {
        val tags = TagRepository(db)
        val health = tags.create("Health")!!
        val work = tags.create("Work")!!
        val id = tasks.save(TaskDraft(type = TaskType.HABIT, title = "Walk", tagIds = setOf(health, work)))
        assertEquals(setOf(health, work), tasks.get(id)!!.tagIds)
        assertEquals(setOf(health, work), tasks.observe(TaskType.HABIT).first().single().tagIds)
        tags.delete(work)
        assertEquals(setOf(health), tasks.get(id)!!.tagIds)
        assertEquals("Walk", tasks.get(id)!!.title)
    }

    @Test fun editingATaskKeepsOrChangesItsTags() = runTest {
        val tags = TagRepository(db)
        val a = tags.create("A")!!; val b = tags.create("B")!!
        val id = tasks.save(TaskDraft(type = TaskType.TODO, title = "t", tagIds = setOf(a)))
        tasks.save(TaskDraft(id = id, type = TaskType.TODO, title = "t", tagIds = setOf(b)))
        assertEquals(setOf(b), tasks.get(id)!!.tagIds)
    }

    // ---- per-task stats ----

    @Test fun taskWeekCountsPlusAndTicksAndSubtractsUndos() = runTest {
        val stats = StatsRepository(db, settings, clock)
        val walk = tasks.save(TaskDraft(type = TaskType.HABIT, title = "Walk"))
        val todo = tasks.save(TaskDraft(type = TaskType.TODO, title = "Tax"))
        repeat(3) { game.dispatch(Event.HabitPlus(walk)) }
        game.dispatch(Event.CompleteTodo(todo))
        game.uncomplete(todo) // taken back: must not count
        val week = stats.observeTaskWeek().first()
        assertEquals(listOf("Walk"), week.map { it.title })
        assertEquals(3, week.single().total)
        assertEquals(3, week.single().perDay.last()) // today is the last slot
        assertEquals(List(6) { 0 }, week.single().perDay.dropLast(1))
    }

    @Test fun taskWeekSpreadsAcrossDaysAndSortsBusiestFirst() = runTest {
        val stats = StatsRepository(db, settings, clock)
        val a = tasks.save(TaskDraft(type = TaskType.HABIT, title = "A"))
        val b = tasks.save(TaskDraft(type = TaskType.HABIT, title = "B"))
        game.dispatch(Event.Rollover)
        game.dispatch(Event.HabitPlus(a))
        clock.at(LocalDate.of(2026, 3, 3)); game.dispatch(Event.Rollover)
        game.dispatch(Event.HabitPlus(a)); game.dispatch(Event.HabitPlus(b))
        val week = stats.observeTaskWeek().first()
        assertEquals(listOf("A", "B"), week.map { it.title })
        assertEquals(2, week[0].total)
        assertEquals(listOf(1, 1), week[0].perDay.takeLast(2))
        assertEquals(listOf(0, 1), week[1].perDay.takeLast(2))
    }

    @Test fun taskWeekIgnoresMinusAndMissed() = runTest {
        val stats = StatsRepository(db, settings, clock)
        val h = tasks.save(TaskDraft(type = TaskType.HABIT, title = "Bad habit"))
        game.dispatch(Event.HabitMinus(h))
        assertTrue(stats.observeTaskWeek().first().isEmpty())
    }
}
