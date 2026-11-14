package app.habitrpg.feature.tasks

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.habitrpg.data.db.AppDatabase
import app.habitrpg.data.repo.GameRepository
import app.habitrpg.data.repo.PlayFeedback
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.data.repo.TagRepository
import app.habitrpg.data.repo.TaskDraft
import app.habitrpg.data.repo.TaskRepository
import app.habitrpg.domain.Difficulty
import app.habitrpg.domain.Schedule
import app.habitrpg.domain.TaskType
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TasksViewModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = Clock.fixed(Instant.parse("2026-03-02T12:00:00Z"), ZoneOffset.UTC)
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
    private lateinit var settings: SettingsRepository
    private val prefs = File.createTempFile("vmtest", ".preferences_pb").also { it.delete() }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Runnable::run) // run Room work inline so the test dispatcher sees it complete
            .setTransactionExecutor(Runnable::run)
            .build()
        repo = TaskRepository(db, app.habitrpg.data.repo.NoopReminderScheduler)
        settings = SettingsRepository(PreferenceDataStoreFactory.create { prefs })
    }

    @After fun tearDown() { Dispatchers.resetMain(); db.close(); prefs.delete() }

    private fun tasksVm() = TasksViewModel(repo, GameRepository(db, settings, clock, PlayFeedback()), settings, TagRepository(db), clock)

    private fun editor(type: TaskType, id: String? = null) = TaskEditorViewModel(
        SavedStateHandle(buildMap { put("type", type.name); if (id != null) put("taskId", id) }), repo, TagRepository(db), settings, clock,
    )

    @Test fun newDailyWithNoWeekdaysCannotBeSaved() = runTest(dispatcher) {
        val vm = editor(TaskType.DAILY)
        advanceUntilIdle()
        vm.setTitle("Stretch")
        vm.toggleWeekday(0); // all seven start selected; clear them
        (1..6).forEach(vm::toggleWeekday)
        assertEquals(0, vm.state.value.weekdayMask)
        assertFalse(vm.state.value.canSave)
        vm.toggleWeekday(2)
        assertTrue(vm.state.value.canSave)
    }

    @Test fun savingEveryNDaysStartsToday() = runTest(dispatcher) {
        val vm = editor(TaskType.DAILY)
        advanceUntilIdle()
        vm.setTitle("Water plants"); vm.setScheduleMode(ScheduleMode.EVERY_N); vm.setEveryN(3)
        vm.save()
        vm.state.first { it.finished } // DataStore reads on a real IO thread, so wait for the flag
        val saved = repo.observe(TaskType.DAILY).first().single()
        assertEquals(Schedule.EveryNDays(3, LocalDate.of(2026, 3, 2)), saved.task.schedule)
    }

    @Test fun editingLoadsExistingValuesAndDeleteCloses() = runTest(dispatcher) {
        val id = repo.save(TaskDraft(type = TaskType.TODO, title = "Tax", difficulty = Difficulty.HARD,
            dueDate = LocalDate.of(2026, 4, 15)))
        val vm = editor(TaskType.TODO, id)
        advanceUntilIdle()
        assertEquals("Tax", vm.state.value.title)
        assertEquals(LocalDate.of(2026, 4, 15), vm.state.value.dueDate)
        vm.delete(); advanceUntilIdle()
        assertTrue(vm.state.value.finished)
        assertEquals(null, repo.get(id))
    }

    @Test fun habitNeedsAtLeastOneButton() = runTest(dispatcher) {
        val vm = editor(TaskType.HABIT)
        advanceUntilIdle()
        vm.setTitle("Water"); vm.setPlus(false); vm.setMinus(false)
        assertFalse(vm.state.value.canSave)
    }

    @Test fun moveUpAndDownReorder() = runTest(dispatcher) {
        val a = repo.save(TaskDraft(type = TaskType.HABIT, title = "a"))
        val b = repo.save(TaskDraft(type = TaskType.HABIT, title = "b"))
        val vm = tasksVm()
        backgroundScope.launch { vm.items(TaskType.HABIT).collect {} }
        advanceUntilIdle()
        vm.moveUp(TaskType.HABIT, b); advanceUntilIdle()
        assertEquals(listOf("b", "a"), vm.items(TaskType.HABIT).value!!.map { it.item.title })
        vm.moveUp(TaskType.HABIT, b); advanceUntilIdle() // already first: no-op
        assertEquals(listOf("b", "a"), vm.items(TaskType.HABIT).value!!.map { it.item.title })
        vm.moveDown(TaskType.HABIT, b); advanceUntilIdle()
        assertEquals(listOf("a", "b"), vm.items(TaskType.HABIT).value!!.map { it.item.title })
    }

    @Test fun filteringByATagShowsOnlyThoseTasksAndNamesTheirTags() = runTest(dispatcher) {
        val tags = TagRepository(db)
        val health = tags.create("Health")!!; val work = tags.create("Work")!!
        repo.save(TaskDraft(type = TaskType.HABIT, title = "Walk", tagIds = setOf(health)))
        repo.save(TaskDraft(type = TaskType.HABIT, title = "Email", tagIds = setOf(work)))
        repo.save(TaskDraft(type = TaskType.HABIT, title = "Untagged"))
        val vm = tasksVm()
        backgroundScope.launch { vm.items(TaskType.HABIT).collect {} }
        backgroundScope.launch { vm.selectedTag.collect {} }
        advanceUntilIdle()
        assertEquals(listOf("Walk", "Email", "Untagged"), vm.items(TaskType.HABIT).value!!.map { it.item.title })
        vm.setSelectedTag(health); advanceUntilIdle()
        val shown = vm.items(TaskType.HABIT).value!!
        assertEquals(listOf("Walk"), shown.map { it.item.title })
        assertEquals(listOf("Health"), shown.single().tags)
    }

    @Test fun deletingTheSelectedTagClearsTheFilter() = runTest(dispatcher) {
        val tags = TagRepository(db)
        val health = tags.create("Health")!!
        repo.save(TaskDraft(type = TaskType.HABIT, title = "Walk", tagIds = setOf(health)))
        repo.save(TaskDraft(type = TaskType.HABIT, title = "Other"))
        val vm = tasksVm()
        backgroundScope.launch { vm.items(TaskType.HABIT).collect {} }
        backgroundScope.launch { vm.selectedTag.collect {} }
        vm.setSelectedTag(health); advanceUntilIdle()
        assertEquals(1, vm.items(TaskType.HABIT).value!!.size)
        tags.delete(health); advanceUntilIdle()
        assertEquals(null, vm.selectedTag.value)
        assertEquals(2, vm.items(TaskType.HABIT).value!!.size)
    }

    @Test fun addingATagInTheEditorSelectsItAndSavesItWithTheTask() = runTest(dispatcher) {
        val vm = editor(TaskType.TODO)
        advanceUntilIdle()
        vm.setTitle("Plan"); vm.addTag("  #Home "); advanceUntilIdle()
        assertEquals(1, vm.state.value.tagIds.size)
        vm.addTag("home"); advanceUntilIdle() // same tag, ignoring case: still one
        assertEquals(1, vm.state.value.tagIds.size)
        vm.save(); vm.state.first { it.finished }
        val saved = repo.observe(TaskType.TODO).first().single()
        assertEquals(vm.state.value.tagIds, saved.tagIds)
        vm.toggleTag(vm.state.value.tagIds.single())
        assertEquals(emptySet<String>(), vm.state.value.tagIds)
    }
}
