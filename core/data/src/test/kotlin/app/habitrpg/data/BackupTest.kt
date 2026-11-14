package app.habitrpg.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import app.habitrpg.data.backup.BackupFile
import app.habitrpg.data.backup.BackupRepository
import app.habitrpg.data.repo.Settings
import app.habitrpg.data.backup.ImportError
import app.habitrpg.data.backup.ImportResult
import app.habitrpg.data.db.AppDatabase
import app.habitrpg.data.db.EquippedEntity
import app.habitrpg.data.db.OwnedItemEntity
import app.habitrpg.data.repo.ChecklistDraft
import app.habitrpg.data.repo.GameRepository
import app.habitrpg.data.repo.PlayFeedback
import app.habitrpg.data.repo.ReminderDraft
import app.habitrpg.data.repo.RewardRepository
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.data.repo.TagRepository
import app.habitrpg.data.repo.TaskDraft
import app.habitrpg.data.repo.TaskRepository
import app.habitrpg.data.repo.ThemeMode
import app.habitrpg.domain.Difficulty
import app.habitrpg.domain.Event
import app.habitrpg.domain.Schedule
import app.habitrpg.domain.TaskType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A complete second app instance, standing in for "the second phone". */
private class Phone(context: android.content.Context, val clock: MutableClock, val scheduler: RecordingScheduler = RecordingScheduler()) {
    val db: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val prefs = File.createTempFile("phone", ".preferences_pb").also { it.delete() }
    val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { prefs })
    val tasks = TaskRepository(db, scheduler)
    val game = GameRepository(db, settings, clock, PlayFeedback())
    val backup = BackupRepository(db, settings, scheduler, clock)
    fun close() { db.close(); prefs.delete() }
}

@RunWith(RobolectricTestRunner::class)
class BackupTest : RepoTestBase() {

    private suspend fun BackupRepository.exported(): String = ByteArrayOutputStream().also { export(it) }.toString("UTF-8")
    private suspend fun BackupRepository.importText(text: String) = import(ByteArrayInputStream(text.toByteArray()))

    /** Fills the app with a bit of everything. */
    private suspend fun Phone.fill() {
        val health = TagRepository(db).create("Health")!!
        tasks.save(TaskDraft(type = TaskType.HABIT, title = "Walk", difficulty = Difficulty.MEDIUM, tagIds = setOf(health),
            reminders = listOf(ReminderDraft(9 * 60, 0b0011111))))
        val daily = tasks.save(TaskDraft(type = TaskType.DAILY, title = "Stretch", schedule = Schedule.EveryNDays(2, LocalDate.of(2026, 3, 1)),
            checklist = listOf(ChecklistDraft("a", true), ChecklistDraft("b"))))
        val todo = tasks.save(TaskDraft(type = TaskType.TODO, title = "Tax", dueDate = LocalDate.of(2026, 4, 15), notes = "forms"))
        game.dispatch(Event.Rollover)
        game.dispatch(Event.CompleteDaily(daily))
        game.dispatch(Event.CompleteTodo(todo))
        db.taskDao().getAll().first { it.type == "HABIT" }.let { game.dispatch(Event.HabitPlus(it.id)) }
        val rewards = RewardRepository(db, game, PlayFeedback(), clock)
        rewards.buy(rewards.save(null, "Coffee", 4, "latte"))
        settings.setThemeMode(ThemeMode.DARK); settings.setHair("hair_long_brown"); settings.setDayStartHour(4)
        settings.setPaused(true); settings.setOnboardingDone(true); settings.setGearBonuses(false)
        db.shopDao().insertOwned(OwnedItemEntity("weapon_sword_steel", clock.millis(), 180))
        db.shopDao().insertOwned(OwnedItemEntity("pet_owl_grey", clock.millis(), 250))
        db.shopDao().setEquipped(EquippedEntity("WEAPON", "weapon_sword_steel"))
        db.shopDao().setEquipped(EquippedEntity("PET", "pet_owl_grey"))
    }

    private suspend fun Phone.snapshot() = listOf(
        db.characterDao().get(), db.taskDao().exportAll(), db.checklistDao().getAll(), db.eventDao().getAll(),
        db.rewardDao().getAll(), db.rewardDao().getPurchases(), db.tagDao().getAll(), db.tagDao().getLinks(),
        db.reminderDao().getAll(), settings.current(), db.shopDao().getOwned(), db.shopDao().getEquipped(),
    )

    @Test fun everythingSurvivesAnExportAndRestoreOnAnotherPhone() = runTest {
        val first = Phone(context, clock); first.fill()
        val second = Phone(context, clock)
        val result = second.backup.importText(first.backup.exported())
        assertTrue(result.toString(), result is ImportResult.Success)
        assertEquals(first.snapshot(), second.snapshot())
        assertEquals(3, (result as ImportResult.Success).tasks)
        first.close(); second.close()
    }

    @Test fun eraseAllEmptiesEverythingCancelsAlarmsAndResetsSettings() = runTest {
        val phone = Phone(context, clock); phone.fill()
        val reminderIds = phone.db.reminderDao().getAll().map { it.id }
        assertTrue(reminderIds.isNotEmpty())
        phone.backup.eraseAll()
        assertTrue(phone.db.taskDao().getAll().isEmpty())
        assertTrue(phone.db.eventDao().getAll().isEmpty())
        assertTrue(phone.db.rewardDao().getAll().isEmpty())
        assertTrue(phone.db.tagDao().getAll().isEmpty())
        assertTrue(phone.db.reminderDao().getAll().isEmpty())
        assertEquals(null, phone.db.characterDao().get())
        assertTrue(phone.scheduler.cancelled.containsAll(reminderIds))
        assertEquals(Settings(), phone.settings.current())
        phone.close()
    }

    @Test fun restoringReplacesWhatWasThereBefore() = runTest {
        val source = Phone(context, clock)
        source.tasks.save(TaskDraft(type = TaskType.TODO, title = "From the backup"))
        val target = Phone(context, clock)
        target.tasks.save(TaskDraft(type = TaskType.TODO, title = "Old one"))
        target.tasks.save(TaskDraft(type = TaskType.HABIT, title = "Old habit"))
        target.backup.importText(source.backup.exported())
        assertEquals(listOf("From the backup"), target.db.taskDao().getAll().map { it.title })
        source.close(); target.close()
    }

    @Test fun restoredRemindersAreScheduledAndOldAlarmsCancelled() = runTest {
        val source = Phone(context, clock)
        source.tasks.save(TaskDraft(type = TaskType.HABIT, title = "w", reminders = listOf(ReminderDraft(8 * 60))))
        val target = Phone(context, clock)
        target.tasks.save(TaskDraft(type = TaskType.HABIT, title = "old", reminders = listOf(ReminderDraft(7 * 60))))
        val oldId = target.db.reminderDao().getAll().single().id
        target.backup.importText(source.backup.exported())
        assertTrue(oldId in target.scheduler.cancelled)
        assertEquals(source.db.reminderDao().getAll().map { it.id }, target.scheduler.scheduled.takeLast(1).map { it.id })
        source.close(); target.close()
    }

    @Test fun restoringKeepsTheRolloverDateSoALongGapIsNotPunished() = runTest {
        val source = Phone(context, clock); source.fill()
        val target = Phone(context, clock)
        target.backup.importText(source.backup.exported())
        assertEquals(source.db.characterDao().get()!!.lastRolloverDate, target.db.characterDao().get()!!.lastRolloverDate)
        source.close(); target.close()
    }

    // ---- bad files change nothing ----

    private suspend fun assertRejected(text: String, mention: String) {
        val phone = Phone(context, clock)
        phone.tasks.save(TaskDraft(type = TaskType.TODO, title = "Keep me"))
        val before = phone.snapshot()
        val r = phone.backup.importText(text)
        assertTrue("expected a failure for: $mention", r is ImportResult.Failure)
        assertEquals("a rejected backup must not change anything", before, phone.snapshot())
        phone.close()
    }

    @Test fun garbageAndEmptyFilesAreRejected() = runTest {
        assertRejected("not json at all", "garbage")
        assertRejected("", "empty")
        assertRejected("[1,2,3]", "wrong shape")
    }

    @Test fun anotherAppsJsonIsRejected() = runTest { assertRejected("""{"app":"something-else","version":1}""", "other app") }

    @Test fun aNewerFormatVersionIsRejectedWithAHelpfulMessage() = runTest {
        val phone = Phone(context, clock)
        val r = phone.backup.importText("""{"app":"habit-rpg","version":${BackupFile.CURRENT_VERSION + 1}}""")
        assertEquals(ImportError.NEWER_VERSION, (r as ImportResult.Failure).error)
        phone.close()
    }

    @Test fun valuesTheAppCouldNotLoadAreRejectedBeforeAnythingIsWritten() = runTest {
        val task = """{"id":"t","type":"WIZARD","title":"x","difficulty":"EASY","sortOrder":0}"""
        assertRejected("""{"app":"habit-rpg","version":1,"tasks":[$task]}""", "unknown task type")
        val badSchedule = """{"id":"t","type":"DAILY","title":"x","difficulty":"EASY","sortOrder":0,"scheduleKind":2,"everyN":0}"""
        assertRejected("""{"app":"habit-rpg","version":1,"tasks":[$badSchedule]}""", "bad schedule")
        assertRejected("""{"app":"habit-rpg","version":1,"character":{"level":0,"xp":0,"health":1,"gold":0}}""", "level 0")
    }

    @Test fun aBackupWithDanglingReferencesRollsBackCompletely() = runTest {
        val dangling = """{"app":"habit-rpg","version":1,"checklist":[{"id":"c","taskId":"missing","text":"x","done":false,"position":0}]}"""
        assertRejected(dangling, "dangling checklist item")
    }

    @Test fun anOversizedFileIsRejectedWithoutReadingItAll() = runTest {
        val phone = Phone(context, clock)
        val huge = object : java.io.InputStream() { var n = 0L; override fun read() = if (n++ < Long.MAX_VALUE) 'a'.code else -1 }
        assertTrue(phone.backup.import(huge) is ImportResult.Failure)
        phone.close()
    }

    // ---- the format ----

    /** A fixed v1 file with every field. If this stops parsing, the format changed: bump the version and keep reading the old one. */
    private val golden = """
      {"app":"habit-rpg","version":1,"exportedAt":"2026-03-02T12:00:00Z",
       "settings":{"dayStartHour":4,"paused":true,"themeMode":"DARK","hair":"hair_spiky_brown","onboardingDone":true,
                   "body":"body_default","gearBonuses":false,"quests":false,"classes":true,"language":"es"},
       "character":{"id":1,"level":10,"xp":40,"health":31,"gold":17,"lastRolloverDate":"2026-03-02","classId":"mage","mana":20},
       "ownedItems":[{"itemId":"head_helm_bronze","acquiredAt":1772452800000,"pricePaid":60}],
       "equipped":[{"slot":"HEADGEAR","itemId":"head_helm_bronze"}],
       "tasks":[{"id":"t1","type":"DAILY","title":"Stretch","notes":"","difficulty":"HARD","sortOrder":0,"archived":false,
                 "plusEnabled":true,"minusEnabled":true,"plusCount":0,"minusCount":0,
                 "scheduleKind":2,"weekdayMask":0,"everyN":3,"startDate":"2026-03-01","streak":5,"doneToday":true,
                 "dueDate":null,"completedAt":null}],
       "checklist":[{"id":"c1","taskId":"t1","text":"Neck","done":true,"position":0}],
       "events":[{"id":"e1","taskId":"t1","taskTitle":"Stretch","timestamp":1772452800000,"kind":"DONE","xp":28,"gold":8,"health":0,"day":null}],
       "rewards":[{"id":"r1","title":"Coffee","cost":4,"notes":"","sortOrder":0}],
       "purchases":[{"id":"p1","rewardId":"r1","timestamp":1772452800000,"costPaid":4}],
       "tags":[{"id":"g1","name":"Health"}],"taskTags":[{"taskId":"t1","tagId":"g1"}],
       "reminders":[{"id":"m1","taskId":"t1","minuteOfDay":540,"weekdayMask":127}],
       "someFutureField":"ignored"}
    """.trimIndent()

    @Test fun theFixedVersion1FileLoadsEverything() = runTest {
        val phone = Phone(context, clock)
        val r = phone.backup.importText(golden)
        assertTrue(r.toString(), r is ImportResult.Success)
        val task = phone.tasks.get("t1")!!
        assertEquals(Schedule.EveryNDays(3, LocalDate.of(2026, 3, 1)), task.task.schedule)
        assertEquals(5, task.task.streak)
        assertEquals(setOf("g1"), task.tagIds)
        assertEquals(540, task.reminders.single().minuteOfDay)
        assertEquals(10, phone.db.characterDao().get()!!.level)
        assertEquals("mage", phone.db.characterDao().get()!!.classId)
        assertEquals(ThemeMode.DARK, phone.settings.current().themeMode)
        assertEquals(4, phone.settings.current().dayStartHour)
        assertEquals(listOf("head_helm_bronze"), phone.db.shopDao().getOwned().map { it.itemId })
        assertEquals(false, phone.settings.current().gearBonuses)
        assertEquals(false, phone.settings.current().quests)
        assertEquals("es", phone.settings.current().language)
        phone.close()
    }

    @Test fun gearRowsFromAnUntrustedFileAreCleanedNotTrusted() = runTest {
        val file = """
          {"app":"habit-rpg","version":1,
           "ownedItems":[{"itemId":"weapon_sword_gold","acquiredAt":1,"pricePaid":450},
                         {"itemId":"weapon_sword_gold","acquiredAt":2,"pricePaid":450},
                         {"itemId":"cheat_item","acquiredAt":1,"pricePaid":0},
                         {"itemId":"pet_cat_black","acquiredAt":1,"pricePaid":250}],
           "equipped":[{"slot":"WEAPON","itemId":"weapon_sword_gold"},
                       {"slot":"ARMOUR","itemId":"weapon_staff_gold"},
                       {"slot":"HEADGEAR","itemId":"head_cap_gold"},
                       {"slot":"WEAPON","itemId":"cheat_item"},
                       {"slot":"PET","itemId":"weapon_sword_gold"}]}
        """.trimIndent()
        val phone = Phone(context, clock)
        assertTrue(phone.backup.importText(file) is ImportResult.Success)
        assertEquals(listOf("pet_cat_black", "weapon_sword_gold"), phone.db.shopDao().getOwned().map { it.itemId }.sorted())
        // not owned, wrong slot, unknown, or duplicated slots do not survive; the one honest row does
        assertEquals(listOf("WEAPON" to "weapon_sword_gold"), phone.db.shopDao().getEquipped().map { it.slot to it.itemId })
        phone.close()
    }

    @Test fun eraseAllAlsoClearsTheShop() = runTest {
        val phone = Phone(context, clock); phone.fill()
        assertTrue(phone.db.shopDao().getOwned().isNotEmpty())
        phone.backup.eraseAll()
        assertTrue(phone.db.shopDao().getOwned().isEmpty())
        assertTrue(phone.db.shopDao().getEquipped().isEmpty())
        phone.close()
    }

    @Test fun anExportContainsTheFieldNamesOfTheDocumentedFormat() = runTest {
        val phone = Phone(context, clock); phone.fill()
        val text = phone.backup.exported()
        listOf("\"app\": \"habit-rpg\"", "\"version\": ${BackupFile.CURRENT_VERSION}", "\"settings\"", "\"tasks\"", "\"checklist\"", "\"events\"",
            "\"rewards\"", "\"purchases\"", "\"tags\"", "\"taskTags\"", "\"reminders\"", "\"lastRolloverDate\"", "\"taskTitle\"")
            .forEach { assertTrue("missing $it", text.contains(it)) }
        phone.close()
    }

    @Test fun questAndClassSurviveABackupAndRestore() = runTest {
        val source = Phone(context, clock)
        source.db.characterDao().upsert(app.habitrpg.data.db.CharacterEntity(1, 12, 0, 50, 5, "2026-03-02", "mage", 10, 0, 2, 0))
        source.game.dispatch(Event.StartQuest("boss_stone_golem"))
        source.settings.setQuests(false); source.settings.setClasses(false)
        val target = Phone(context, clock)
        assertTrue(target.backup.importText(source.backup.exported()) is ImportResult.Success)
        val c = target.db.characterDao().get()!!
        assertEquals(listOf("mage", 10, 2), listOf(c.classId, c.mana, c.statMind))
        assertEquals("boss_stone_golem", target.db.questDao().get()!!.bossId)
        assertEquals(false, target.settings.current().quests)
        assertEquals(false, target.settings.current().classes)
        source.close(); target.close()
    }

    @Test fun questAndClassRowsFromAnUntrustedFileAreCleaned() = runTest {
        val file = """
          {"app":"habit-rpg","version":1,
           "character":{"level":11,"xp":0,"health":50,"gold":0,"lastRolloverDate":null,
                        "classId":"wizard","mana":999,"statMight":50,"statMind":0,"statVigor":0},
           "quest":{"bossId":"cheat_boss","hp":10,"startedDate":"2026-03-02","deadlineDate":"2026-03-05","outcome":"ACTIVE","endDate":null},
           "questHistory":[{"id":"a","bossId":"boss_slime_king","startedDate":"2026-03-01","endDate":"2026-03-02","outcome":"WON"},
                           {"id":"b","bossId":"cheat_boss","startedDate":"2026-03-01","endDate":"2026-03-02","outcome":"WON"},
                           {"id":"c","bossId":"boss_slime_king","startedDate":"nope","endDate":"2026-03-02","outcome":"WON"}]}
        """.trimIndent()
        val phone = Phone(context, clock)
        assertTrue(phone.backup.importText(file) is ImportResult.Success)
        val c = phone.db.characterDao().get()!!
        assertEquals(listOf(null, 0, 0), listOf(c.classId, c.mana, c.statMight))
        assertEquals(null, phone.db.questDao().get())
        assertEquals(listOf("a"), phone.db.questDao().getHistory().map { it.id })

        val ok = """
          {"app":"habit-rpg","version":1,
           "character":{"level":12,"xp":0,"health":50,"gold":0,"lastRolloverDate":null,
                        "classId":"warrior","mana":999,"statMight":5,"statMind":0,"statVigor":0},
           "quest":{"bossId":"boss_slime_king","hp":9999,"startedDate":"2026-03-02","deadlineDate":"2026-03-05","outcome":"ACTIVE","endDate":null}}
        """.trimIndent()
        assertTrue(phone.backup.importText(ok) is ImportResult.Success)
        val d = phone.db.characterDao().get()!!
        // 5 points but only 2 allowed at level 12: stats reset; mana clamped to the cap
        assertEquals(listOf("warrior", 0), listOf(d.classId, d.statMight))
        assertEquals(24, d.mana)
        assertEquals(150, phone.db.questDao().get()!!.hp)
        phone.close()
    }

    @Test fun eraseAllAlsoClearsQuests() = runTest {
        val phone = Phone(context, clock)
        phone.game.dispatch(Event.StartQuest("boss_slime_king"))
        phone.backup.eraseAll()
        assertEquals(null, phone.db.questDao().get())
        phone.close()
    }

    @Test fun theLanguageSurvivesABackupAndRestore() = runTest {
        val source = Phone(context, clock)
        source.settings.setLanguage(app.habitrpg.data.repo.AppLanguage.GERMAN)
        val target = Phone(context, clock)
        assertTrue(target.backup.importText(source.backup.exported()) is ImportResult.Success)
        assertEquals("de", target.settings.current().language)
        source.close(); target.close()
    }

    @Test fun anUnknownLanguageFromAFileFallsBackToSystem() = runTest {
        val file = """{"app":"habit-rpg","version":1,"settings":{"language":"klingon"}}"""
        val phone = Phone(context, clock)
        assertTrue(phone.backup.importText(file) is ImportResult.Success)
        assertEquals("system", phone.settings.current().language)
        phone.close()
    }

    @Test fun eraseAllPutsTheLanguageBackOnSystem() = runTest {
        val phone = Phone(context, clock)
        phone.settings.setLanguage(app.habitrpg.data.repo.AppLanguage.FRENCH)
        phone.backup.eraseAll()
        assertEquals("system", phone.settings.current().language)
        phone.close()
    }
}
