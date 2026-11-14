package app.habitrpg.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import app.habitrpg.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun database(@ApplicationContext c: Context): AppDatabase =
        Room.databaseBuilder(c, AppDatabase::class.java, "habitrpg.db")
            .build() // never fallbackToDestructiveMigration

    @Provides fun taskDao(db: AppDatabase) = db.taskDao()
    @Provides fun characterDao(db: AppDatabase) = db.characterDao()
    @Provides fun eventDao(db: AppDatabase) = db.eventDao()
    @Provides fun rewardDao(db: AppDatabase) = db.rewardDao()
    @Provides fun tagDao(db: AppDatabase) = db.tagDao()
    @Provides fun reminderDao(db: AppDatabase) = db.reminderDao()
    @Provides fun shopDao(db: AppDatabase) = db.shopDao()

    @Provides @Singleton
    fun settingsStore(@ApplicationContext c: Context): DataStore<Preferences> = c.settingsStore

    @Provides @Singleton
    fun clock(): Clock = SystemZoneClock
}

/**
 * The system clock that follows the phone's time zone. `Clock.systemDefaultZone()` captures the zone once, so a
 * process that outlives a journey would keep scoring days in the old zone.
 */
object SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
}
