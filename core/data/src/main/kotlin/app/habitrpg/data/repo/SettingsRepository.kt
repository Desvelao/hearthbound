package app.habitrpg.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {
    private val dayStart = intPreferencesKey("day_start_hour")
    private val paused = booleanPreferencesKey("paused")
    private val theme = stringPreferencesKey("theme_mode")
    private val hair = stringPreferencesKey("hair")
    private val body = stringPreferencesKey("body")
    private val onboarding = booleanPreferencesKey("onboarding_done")
    private val gearBonuses = booleanPreferencesKey("gear_bonuses")
    private val quests = booleanPreferencesKey("quests")
    private val classes = booleanPreferencesKey("classes")
    private val language = stringPreferencesKey("language")

    val settings: Flow<Settings> = store.data.map {
        Settings(
            dayStartHour = it[dayStart] ?: Settings().dayStartHour,
            paused = it[paused] ?: false,
            themeMode = it[theme]?.let { v -> ThemeMode.entries.firstOrNull { m -> m.name == v } } ?: ThemeMode.SYSTEM,
            hair = it[hair] ?: DEFAULT_HAIR,
            onboardingDone = it[onboarding] ?: false,
            body = it[body] ?: DEFAULT_BODY,
            gearBonuses = it[gearBonuses] ?: true,
            quests = it[quests] ?: true,
            classes = it[classes] ?: true,
            language = AppLanguage.fromTag(it[language]).tag,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setDayStartHour(hour: Int) {
        require(hour in 0..23)
        store.edit { it[dayStart] = hour }
    }

    suspend fun setPaused(value: Boolean) {
        store.edit { it[paused] = value }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[theme] = mode.name }
    }

    suspend fun setHair(id: String) {
        store.edit { it[hair] = id }
    }

    suspend fun setBody(id: String) {
        store.edit { it[body] = id }
    }

    suspend fun setGearBonuses(on: Boolean) {
        store.edit { it[gearBonuses] = on }
    }

    suspend fun setQuests(on: Boolean) {
        store.edit { it[quests] = on }
    }

    suspend fun setClasses(on: Boolean) {
        store.edit { it[classes] = on }
    }

    suspend fun setLanguage(value: AppLanguage) {
        store.edit { it[language] = value.tag }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        store.edit { it[onboarding] = done }
    }

    /** Replaces every setting at once, for restoring a backup. */
    suspend fun restore(s: Settings) {
        store.edit {
            it[dayStart] = s.dayStartHour.coerceIn(0, 23)
            it[paused] = s.paused
            it[theme] = s.themeMode.name
            it[hair] = s.hair
            it[onboarding] = s.onboardingDone
            it[body] = s.body
            it[gearBonuses] = s.gearBonuses
            it[quests] = s.quests
            it[classes] = s.classes
            it[language] = AppLanguage.fromTag(s.language).tag
        }
    }
}
