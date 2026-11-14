package app.habitrpg.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.habitrpg.data.db.AppDatabase
import app.habitrpg.data.db.ReminderEntity
import app.habitrpg.data.repo.GameRepository
import app.habitrpg.data.repo.ReminderScheduler
import app.habitrpg.data.repo.PlayFeedback
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.data.repo.TaskRepository
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Before

/** Records what would have been scheduled, so tests can assert on alarms without an alarm service. */
class RecordingScheduler : ReminderScheduler {
    val scheduled = mutableListOf<ReminderEntity>()
    val cancelled = mutableListOf<String>()
    override fun schedule(reminder: ReminderEntity) { scheduled += reminder }
    override fun cancel(reminderId: String) { cancelled += reminderId }
}

class MutableClock(var now: Instant, private val tz: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = tz
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now
    fun at(date: LocalDate, hour: Int = 12) { now = date.atTime(hour, 0).atZone(tz).toInstant() }
}

abstract class RepoTestBase {
    protected val context: Context = ApplicationProvider.getApplicationContext()
    protected val clock = MutableClock(LocalDate.of(2026, 3, 2).atTime(12, 0).toInstant(ZoneOffset.UTC))
    protected lateinit var db: AppDatabase
    protected lateinit var settings: SettingsRepository
    protected lateinit var tasks: TaskRepository
    protected val scheduler = RecordingScheduler()
    protected lateinit var game: GameRepository
    protected lateinit var feedback: PlayFeedback
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefsFile = File.createTempFile("settings", ".preferences_pb").also { it.delete() }

    protected fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { prefsFile })
        tasks = TaskRepository(db, scheduler)
        feedback = PlayFeedback()
        game = GameRepository(db, settings, clock, feedback)
    }

    @Before fun setUp() = open()
    @After fun tearDown() { db.close(); prefsFile.delete() }
}
