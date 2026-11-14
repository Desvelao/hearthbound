package app.habitrpg

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.habitrpg.data.db.AppDatabase
import app.habitrpg.data.repo.GameRepository
import app.habitrpg.data.repo.NoopReminderScheduler
import app.habitrpg.data.repo.PlayFeedback
import app.habitrpg.data.repo.RewardRepository
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.data.repo.TaskRepository
import app.habitrpg.domain.TaskType
import app.habitrpg.onboarding.OnboardingViewModel
import app.habitrpg.onboarding.Starters
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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
class OnboardingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = Clock.fixed(Instant.parse("2026-03-02T12:00:00Z"), ZoneOffset.UTC)
    private val prefs = File.createTempFile("onboarding", ".preferences_pb").also { it.delete() }
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var tasks: TaskRepository
    private lateinit var vm: OnboardingViewModel

    @Before fun setUp() {
        // viewModelScope runs on Main, and under Robolectric the test thread is Main: do not let waiting block it.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { prefs })
        tasks = TaskRepository(db, NoopReminderScheduler)
        val game = GameRepository(db, settings, clock, PlayFeedback())
        vm = OnboardingViewModel(context, tasks, RewardRepository(db, game, PlayFeedback(), clock), settings)
    }

    @After fun tearDown() { Dispatchers.resetMain(); db.close(); prefs.delete() }

    private fun finishAndWait(selected: Set<String>, hair: String) {
        val done = java.util.concurrent.CountDownLatch(1)
        vm.finish(selected, hair) { done.countDown() }
        assertTrue("onboarding did not finish", done.await(10, java.util.concurrent.TimeUnit.SECONDS))
    }

    @Test fun choosingStartersCreatesJustThoseAndSavesTheLook() = runBlocking {
        finishAndWait(setOf("water", "stretch", "plan", "episode"), "hair_spiky_brown")
        assertEquals(listOf("Drink a glass of water"), tasks.observe(TaskType.HABIT).first().map { it.title })
        assertEquals(listOf("Stretch for 5 minutes"), tasks.observe(TaskType.DAILY).first().map { it.title })
        assertEquals(listOf("Plan tomorrow"), tasks.observe(TaskType.TODO).first().map { it.title })
        assertEquals(listOf("Watch an episode"), db.rewardDao().getAll().map { it.title })
        assertEquals(20, db.rewardDao().getAll().single().cost)
        val s = settings.current()
        assertEquals("hair_spiky_brown", s.hair)
        assertTrue(s.onboardingDone)
    }

    @Test fun theStarterHabitIsPlusOnly() = runBlocking {
        finishAndWait(setOf("water"), "hair_short_brown")
        assertFalse(tasks.observe(TaskType.HABIT).first().single().task.minusEnabled)
    }

    @Test fun untickingEverythingCreatesNothingButStillFinishes() = runBlocking {
        finishAndWait(emptySet(), "hair_short_brown")
        assertEquals(0, db.taskDao().getAll().size)
        assertEquals(0, db.rewardDao().getAll().size)
        assertTrue(settings.current().onboardingDone)
    }

    @Test fun skippingCreatesNothingKeepsTheDefaultLookAndNeverAsksAgain() = runBlocking {
        val done = java.util.concurrent.CountDownLatch(1)
        vm.skip { done.countDown() }
        assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(0, db.taskDao().getAll().size)
        val s = settings.current()
        assertTrue(s.onboardingDone)
        assertEquals("hair_short_brown", s.hair)
    }

    @Test fun featureChoicesAreSavedWithTheRest() = runBlocking {
        val done = java.util.concurrent.CountDownLatch(1)
        vm.finish(emptySet(), "hair_short_brown", quests = false, classes = false, gearBonuses = true) { done.countDown() }
        assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS))
        val s = settings.current()
        assertFalse(s.quests); assertFalse(s.classes); assertTrue(s.gearBonuses)
        assertTrue(s.onboardingDone)
    }

    @Test fun gearBonusesCanBeTurnedOffToo() = runBlocking {
        val done = java.util.concurrent.CountDownLatch(1)
        vm.finish(emptySet(), "hair_short_brown", gearBonuses = false) { done.countDown() }
        assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS))
        val s = settings.current()
        assertFalse(s.gearBonuses); assertTrue(s.quests); assertTrue(s.classes)
    }

    @Test fun finishingWithoutTouchingTheFeaturesLeavesThemOn() = runBlocking {
        finishAndWait(emptySet(), "hair_short_brown")
        val s = settings.current()
        assertTrue(s.quests); assertTrue(s.classes); assertTrue(s.gearBonuses)
    }

    @Test fun skippingLeavesTheFeaturesOn() = runBlocking {
        val done = java.util.concurrent.CountDownLatch(1)
        vm.skip { done.countDown() }
        assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS))
        val s = settings.current()
        assertTrue(s.quests); assertTrue(s.classes); assertTrue(s.gearBonuses)
    }

    @Test fun everyStarterHasAUniqueId() {
        assertEquals(Starters.all.size, Starters.all.map { it.id }.toSet().size)
    }

    @Test fun starterTitlesAreWrittenInTheChosenLanguage() = runBlocking {
        settings.setLanguage(app.habitrpg.data.repo.AppLanguage.SPANISH)
        finishAndWait(setOf("water", "stretch", "plan", "episode"), "hair_short_brown")
        assertEquals(listOf("Beber un vaso de agua"), tasks.observe(TaskType.HABIT).first().map { it.title })
        assertEquals(listOf("Estirarse 5 minutos"), tasks.observe(TaskType.DAILY).first().map { it.title })
        assertEquals(listOf("Planificar el día de mañana"), tasks.observe(TaskType.TODO).first().map { it.title })
        assertEquals(listOf("Ver un episodio"), db.rewardDao().getAll().map { it.title })
    }

    @Test fun theLanguageChosenOnTheFirstPageIsKeptWhenSkipping() = runBlocking {
        vm.setLanguage(app.habitrpg.data.repo.AppLanguage.GERMAN)
        val done = java.util.concurrent.CountDownLatch(1)
        vm.skip { done.countDown() }
        assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals("de", settings.current().language)
    }
}
