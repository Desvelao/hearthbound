package app.habitrpg.data.repo

import androidx.room.withTransaction
import app.habitrpg.data.db.AppDatabase
import app.habitrpg.data.db.EquippedEntity
import app.habitrpg.data.db.OwnedItemEntity
import app.habitrpg.domain.Catalog
import app.habitrpg.domain.Event
import app.habitrpg.domain.Slot
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class BuyResult { BOUGHT, ALREADY_OWNED, NOT_ENOUGH_GOLD, UNKNOWN_ITEM }

/** Buying and wearing gear and pets. Modelled on [RewardRepository.buy]: gold moves through the one write path. */
@Singleton
class ShopRepository @Inject constructor(
    private val db: AppDatabase,
    private val game: GameRepository,
    private val feedback: PlayFeedback,
    private val clock: Clock,
) {
    private val dao = db.shopDao()

    fun observeOwned(): Flow<Set<String>> = dao.observeOwned().map { rows -> rows.map { it.itemId }.toSet() }

    /** What is worn in each slot. Rows whose slot or id the catalog does not know are ignored. */
    fun observeEquipped(): Flow<Map<Slot, String>> = dao.observeEquipped().map(::toSlotMap)

    suspend fun equipped(): Map<Slot, String> = toSlotMap(dao.getEquipped())

    /** Pays for an item and puts it on. Nothing changes unless the whole purchase succeeds. */
    suspend fun buy(itemId: String): BuyResult {
        val item = Catalog.byId(itemId) ?: return BuyResult.UNKNOWN_ITEM
        val result = db.withTransaction {
            if (dao.getOwnedItem(itemId) != null) return@withTransaction BuyResult.ALREADY_OWNED
            val gold = db.characterDao().get()?.gold ?: 0
            if (gold < item.price) return@withTransaction BuyResult.NOT_ENOUGH_GOLD
            game.dispatch(Event.Spend(item.price))
            dao.insertOwned(OwnedItemEntity(itemId, clock.millis(), item.price))
            dao.setEquipped(EquippedEntity(item.slot.name, itemId))
            BuyResult.BOUGHT
        }
        if (result == BuyResult.BOUGHT) feedback.emit(Feedback.Purchased(null, item.id, item.price))
        return result
    }

    /** Wears an owned item, replacing whatever was in its slot. False when it is not owned. */
    suspend fun equip(itemId: String): Boolean {
        val item = Catalog.byId(itemId) ?: return false
        return db.withTransaction {
            if (dao.getOwnedItem(itemId) == null) return@withTransaction false
            dao.setEquipped(EquippedEntity(item.slot.name, itemId))
            true
        }
    }

    suspend fun unequip(slot: Slot) = dao.clearSlot(slot.name)

    private fun toSlotMap(rows: List<EquippedEntity>): Map<Slot, String> = buildMap {
        rows.forEach { row ->
            val slot = Slot.entries.firstOrNull { it.name == row.slot } ?: return@forEach
            if (Catalog.byId(row.itemId)?.slot == slot) put(slot, row.itemId)
        }
    }
}
