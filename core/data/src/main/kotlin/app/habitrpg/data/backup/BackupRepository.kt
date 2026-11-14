package app.habitrpg.data.backup

import androidx.room.withTransaction
import app.habitrpg.data.db.AppDatabase
import app.habitrpg.data.db.ChecklistItemEntity
import app.habitrpg.data.db.CharacterEntity
import app.habitrpg.data.db.EquippedEntity
import app.habitrpg.data.db.OwnedItemEntity
import app.habitrpg.data.db.QuestEntity
import app.habitrpg.data.db.QuestHistoryEntity
import app.habitrpg.data.db.ReminderEntity
import app.habitrpg.data.db.RewardEntity
import app.habitrpg.data.db.RewardPurchaseEntity
import app.habitrpg.data.db.TagEntity
import app.habitrpg.data.db.TaskEntity
import app.habitrpg.data.db.TaskEventEntity
import app.habitrpg.data.db.TaskTagEntity
import app.habitrpg.data.repo.AppLanguage
import app.habitrpg.data.repo.DEFAULT_BODY
import app.habitrpg.data.repo.DEFAULT_HAIR
import app.habitrpg.data.repo.ReminderScheduler
import app.habitrpg.data.repo.Settings
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.data.repo.ThemeMode
import app.habitrpg.domain.Catalog
import app.habitrpg.domain.Character
import app.habitrpg.domain.Classes
import app.habitrpg.domain.Constants
import app.habitrpg.domain.QuestCatalog
import app.habitrpg.domain.QuestOutcome
import app.habitrpg.domain.Difficulty
import app.habitrpg.domain.Engine
import app.habitrpg.domain.TaskType
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupSettings(
    val dayStartHour: Int = 0,
    val paused: Boolean = false,
    val themeMode: String = ThemeMode.SYSTEM.name,
    val hair: String = DEFAULT_HAIR,
    val onboardingDone: Boolean = true,
    val body: String = DEFAULT_BODY,
    val gearBonuses: Boolean = true,
    val quests: Boolean = true,
    val classes: Boolean = true,
    val language: String = AppLanguage.SYSTEM.tag,
)

/**
 * The export file. The entities are written as they are, so a renamed column would silently change the format;
 * `BackupTest` pins the field names with a fixed file. Bump [CURRENT_VERSION] for any breaking change once released.
 */
@Serializable
data class BackupFile(
    val app: String = APP_ID,
    val version: Int = CURRENT_VERSION,
    val exportedAt: String = "",
    val settings: BackupSettings = BackupSettings(),
    val character: CharacterEntity? = null,
    val tasks: List<TaskEntity> = emptyList(),
    val checklist: List<ChecklistItemEntity> = emptyList(),
    val events: List<TaskEventEntity> = emptyList(),
    val rewards: List<RewardEntity> = emptyList(),
    val purchases: List<RewardPurchaseEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val taskTags: List<TaskTagEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val ownedItems: List<OwnedItemEntity> = emptyList(),
    val equipped: List<EquippedEntity> = emptyList(),
    val quest: QuestEntity? = null,
    val questHistory: List<QuestHistoryEntity> = emptyList(),
) {
    companion object {
        const val APP_ID = "habit-rpg"
        const val CURRENT_VERSION = 1
    }
}

/** Why a backup was refused. Never English text: the settings screen maps each one to a translated message. */
enum class ImportError {
    TOO_LARGE, UNREADABLE, NOT_A_BACKUP, INVALID_VERSION, NEWER_VERSION, UNKNOWN_TASK, INVALID_SCHEDULE,
    INVALID_DUE_DATE, INVALID_REMINDER, INVALID_CHARACTER, INVALID_DATE, INCONSISTENT,
}

sealed interface ImportResult {
    data class Success(val tasks: Int, val rewards: Int, val events: Int) : ImportResult
    /** Nothing was changed. [error] says why; the UI turns it into text in the player's language. */
    data class Failure(val error: ImportError) : ImportResult
}

@Singleton
class BackupRepository @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun export(out: OutputStream) {
        val s = settings.current()
        // One transaction so every table is read from the same moment.
        val file = db.withTransaction {
            BackupFile(
                exportedAt = clock.instant().toString(),
                settings = BackupSettings(s.dayStartHour, s.paused, s.themeMode.name, s.hair, s.onboardingDone, s.body, s.gearBonuses, s.quests, s.classes, s.language),
                character = db.characterDao().get(),
                tasks = db.taskDao().exportAll(),
                checklist = db.checklistDao().getAll(),
                events = db.eventDao().getAll(),
                rewards = db.rewardDao().getAll(),
                purchases = db.rewardDao().getPurchases(),
                tags = db.tagDao().getAll(),
                taskTags = db.tagDao().getLinks(),
                reminders = db.reminderDao().getAll(),
                ownedItems = db.shopDao().getOwned(),
                equipped = db.shopDao().getEquipped(),
                quest = db.questDao().get(),
                questHistory = db.questDao().getHistory(),
            )
        }
        out.write(json.encodeToString(BackupFile.serializer(), file).toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /** Replaces everything with the backup, all or nothing. */
    suspend fun import(input: InputStream): ImportResult {
        val text = try {
            readBounded(input) ?: return ImportResult.Failure(ImportError.TOO_LARGE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ImportResult.Failure(ImportError.UNREADABLE)
        }
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ImportResult.Failure(ImportError.NOT_A_BACKUP)
        }
        validate(file)?.let { return ImportResult.Failure(it) }
        val (owned, equipped) = sanitizeShop(file)
        val character = file.character?.let(::sanitizeCharacter)
        val quest = sanitizeQuest(file.quest)
        val questHistory = sanitizeHistory(file.questHistory)

        var oldReminders = emptyList<ReminderEntity>()
        try {
            db.withTransaction {
                oldReminders = db.reminderDao().getAll()
                clearTables()

                db.taskDao().insertAll(file.tasks.map { it.copy(value = Engine.clampValue(it.value)) })
                db.checklistDao().insertAll(file.checklist)
                db.tagDao().insertAll(file.tags)
                db.tagDao().insertLinks(file.taskTags)
                db.reminderDao().insertAll(file.reminders)
                db.eventDao().insertAll(file.events.map { it.copy(bossDamage = it.bossDamage.coerceAtLeast(0)) })
                db.rewardDao().insertAll(file.rewards)
                db.rewardDao().insertPurchases(file.purchases)
                db.shopDao().insertAllOwned(owned)
                db.shopDao().insertAllEquipped(equipped)
                quest?.let { db.questDao().upsert(it) }
                db.questDao().insertAllHistory(questHistory)
                character?.let { db.characterDao().upsert(it.copy(id = 1)) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The transaction rolled back, so the existing data is untouched.
            return ImportResult.Failure(ImportError.INCONSISTENT)
        }

        settings.restore(file.settings.toSettings())
        oldReminders.forEach { scheduler.cancel(it.id) }
        file.reminders.forEach(scheduler::schedule)
        return ImportResult.Success(file.tasks.size, file.rewards.size, file.events.size)
    }

    /**
     * Deletes every task, reward, tag, reminder, history entry and the character, cancels all alarms and resets
     * the settings to a fresh install (so onboarding shows again). All or nothing for the database.
     */
    suspend fun eraseAll() {
        var oldReminders = emptyList<ReminderEntity>()
        db.withTransaction {
            oldReminders = db.reminderDao().getAll()
            clearTables()
        }
        oldReminders.forEach { scheduler.cancel(it.id) }
        settings.restore(Settings())
    }

    /** Children first, then parents. Foreign keys would also cascade, but being explicit is clearer. */
    private suspend fun clearTables() {
        db.reminderDao().deleteAll()
        db.tagDao().deleteAllLinks()
        db.checklistDao().deleteAll()
        db.tagDao().deleteAll()
        db.taskDao().deleteAll()
        db.eventDao().deleteAll()
        db.rewardDao().deleteAllPurchases()
        db.rewardDao().deleteAll()
        db.questDao().deleteHistory()
        db.questDao().delete()
        db.shopDao().deleteAllEquipped()
        db.shopDao().deleteAllOwned()
        db.characterDao().deleteAll()
    }

    /** Rejects anything the app could not safely load, before a single row is written. */
    private fun validate(f: BackupFile): ImportError? {
        if (f.app != BackupFile.APP_ID) return ImportError.NOT_A_BACKUP
        if (f.version < 1) return ImportError.INVALID_VERSION
        if (f.version > BackupFile.CURRENT_VERSION) {
            return ImportError.NEWER_VERSION
        }
        for (t in f.tasks) {
            if (TaskType.entries.none { it.name == t.type } || Difficulty.entries.none { it.name == t.difficulty }) {
                return ImportError.UNKNOWN_TASK
            }
            if (t.scheduleKind !in 0..2 || t.everyN < 1) return ImportError.INVALID_SCHEDULE
            if (t.scheduleKind == 2 && runCatching { LocalDate.parse(t.startDate!!) }.isFailure) {
                return ImportError.INVALID_SCHEDULE
            }
            if (t.dueDate != null && runCatching { LocalDate.parse(t.dueDate) }.isFailure) {
                return ImportError.INVALID_DUE_DATE
            }
        }
        if (f.reminders.any { it.minuteOfDay !in 0 until 24 * 60 }) return ImportError.INVALID_REMINDER
        val c = f.character
        if (c != null && (c.level < 1 || c.xp < 0 || c.health < 0 || c.gold < 0)) return ImportError.INVALID_CHARACTER
        if (c?.lastRolloverDate != null && runCatching { LocalDate.parse(c.lastRolloverDate) }.isFailure) {
            return ImportError.INVALID_DATE
        }
        return null
    }

    /**
     * Gear rows are untrusted too: unknown item ids are dropped, duplicates collapse, and an equipped row is kept only
     * when the item is owned and really belongs in that slot.
     */
    private fun sanitizeShop(f: BackupFile): Pair<List<OwnedItemEntity>, List<EquippedEntity>> {
        val owned = f.ownedItems.filter { Catalog.byId(it.itemId) != null }.distinctBy { it.itemId }
        val ownedIds = owned.map { it.itemId }.toSet()
        val equipped = f.equipped.filter {
            it.itemId in ownedIds && Catalog.byId(it.itemId)?.slot?.name == it.slot
        }.distinctBy { it.slot }
        return owned to equipped
    }

    /** Class, stat points and mana come from an untrusted file: anything impossible is reset rather than trusted. */
    private fun sanitizeCharacter(c: CharacterEntity): CharacterEntity {
        val cls = Classes.byId(c.classId) ?: return c.copy(classId = null, mana = 0, statMight = 0, statMind = 0, statVigor = 0)
        val stats = listOf(c.statMight, c.statMind, c.statVigor)
        val allowed = (c.level - Constants.CLASS_UNLOCK_LEVEL).coerceAtLeast(0)
        val statsOk = stats.none { it < 0 } && stats.sum() <= allowed
        val fixed = if (statsOk) c else c.copy(statMight = 0, statMind = 0, statVigor = 0)
        val manaMax = Classes.manaMax(Character(level = fixed.level, statMind = fixed.statMind))
        return fixed.copy(classId = cls.id, mana = fixed.mana.coerceIn(0, manaMax))
    }

    private fun parses(s: String?) = s != null && runCatching { LocalDate.parse(s) }.isSuccess

    private fun sanitizeQuest(q: QuestEntity?): QuestEntity? {
        val boss = QuestCatalog.byId(q?.bossId) ?: return null
        if (q == null || !parses(q.startedDate) || !parses(q.deadlineDate)) return null
        val outcome = QuestOutcome.entries.firstOrNull { it.name == q.outcome } ?: return null
        val endDate = q.endDate?.takeIf(::parses)
        return q.copy(id = 1, hp = q.hp.coerceIn(0, boss.hp), outcome = outcome.name, endDate = endDate)
    }

    private fun sanitizeHistory(rows: List<QuestHistoryEntity>) = rows
        .filter {
            QuestCatalog.byId(it.bossId) != null && parses(it.startedDate) && parses(it.endDate) &&
                it.outcome in setOf(QuestOutcome.WON.name, QuestOutcome.ESCAPED.name)
        }
        .distinctBy { it.id }
        .takeLast(500)

    private fun BackupSettings.toSettings() = Settings(
        dayStartHour = dayStartHour.coerceIn(0, 23),
        paused = paused,
        themeMode = ThemeMode.entries.firstOrNull { it.name == themeMode } ?: ThemeMode.SYSTEM,
        hair = hair,
        onboardingDone = onboardingDone,
        body = body,
        gearBonuses = gearBonuses,
        quests = quests,
        classes = classes,
        language = AppLanguage.fromTag(language).tag,
    )

    /** Reads at most [MAX_BYTES]; null when the stream is bigger, so a huge file cannot exhaust memory. */
    private fun readBounded(input: InputStream): String? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (out.size() + n > MAX_BYTES) return null
            out.write(buffer, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    companion object { const val MAX_BYTES = 32 * 1024 * 1024 }
}
