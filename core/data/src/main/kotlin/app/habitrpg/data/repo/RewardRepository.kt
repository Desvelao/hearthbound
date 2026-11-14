package app.habitrpg.data.repo

import androidx.room.withTransaction
import app.habitrpg.data.db.AppDatabase
import app.habitrpg.data.db.RewardEntity
import app.habitrpg.data.db.RewardPurchaseEntity
import app.habitrpg.domain.Event
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class RewardRepository @Inject constructor(
    private val db: AppDatabase,
    private val game: GameRepository,
    private val feedback: PlayFeedback,
    private val clock: Clock,
) {
    private val dao = db.rewardDao()

    fun observe(): Flow<List<RewardEntity>> = dao.observeAll()

    suspend fun get(id: String): RewardEntity? = dao.get(id)

    suspend fun save(id: String?, title: String, cost: Int, notes: String = ""): String {
        val rid = id ?: UUID.randomUUID().toString()
        val sort = dao.get(rid)?.sortOrder ?: dao.getAll().size
        dao.upsert(RewardEntity(rid, title.trim(), cost.coerceAtLeast(0), notes, sort))
        return rid
    }

    suspend fun delete(id: String) = dao.delete(id)

    /** Pays for a reward with gold. Returns false (and changes nothing) when gold is short. */
    suspend fun buy(id: String): Boolean {
        val reward = dao.get(id) ?: return false
        val paid = db.withTransaction {
            val gold = db.characterDao().get()?.gold ?: 0
            if (gold < reward.cost) return@withTransaction false
            game.dispatch(Event.Spend(reward.cost))
            dao.insertPurchase(RewardPurchaseEntity(UUID.randomUUID().toString(), id, clock.millis(), reward.cost))
            true
        }
        if (paid) feedback.emit(Feedback.Purchased(reward.title, null, reward.cost))
        return paid
    }
}
