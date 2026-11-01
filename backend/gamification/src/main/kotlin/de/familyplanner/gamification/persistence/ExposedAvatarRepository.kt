package de.familyplanner.gamification.persistence

import de.familyplanner.core.member.MemberId
import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.gamification.avatar.AvatarItem
import de.familyplanner.gamification.avatar.AvatarRepository
import de.familyplanner.gamification.avatar.PurchaseOutcome
import de.familyplanner.gamification.avatar.StoredAvatar
import de.familyplanner.gamification.ledger.EntryKind
import de.familyplanner.gamification.ledger.LedgerEntry
import de.familyplanner.gamification.ledger.LedgerEntryId
import de.familyplanner.gamification.ledger.shopSourceKey
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.Instant

/**
 * A purchase is one [dbQuery] transaction: ownership row and SHOP debit commit together or not at all. Both are plain
 * `insert`s (not `insertIgnore`), so a failing ledger write rolls the ownership back.
 */
class ExposedAvatarRepository(
    private val db: Database,
) : AvatarRepository {
    override suspend fun findAll(): Map<MemberId, StoredAvatar> =
        dbQuery(db) {
            AvatarSlotTable
                .selectAll()
                .toList()
                .groupBy { MemberId(it[AvatarSlotTable.memberId]) }
                .mapValues { it.value.toStored() }
        }

    override suspend fun find(memberId: MemberId): StoredAvatar? =
        dbQuery(db) {
            AvatarSlotTable
                .selectAll()
                .where { AvatarSlotTable.memberId eq memberId.value }
                .toList()
                .takeIf { it.isNotEmpty() }
                ?.toStored()
        }

    override suspend fun save(
        memberId: MemberId,
        items: Map<String, String>,
        at: Instant,
    ) {
        dbQuery(db) {
            AvatarSlotTable.deleteWhere { AvatarSlotTable.memberId eq memberId.value }
            items.forEach { (slotKey, id) ->
                AvatarSlotTable.insert {
                    it[AvatarSlotTable.memberId] = memberId.value
                    it[slot] = slotKey
                    it[itemId] = id
                    it[updatedAt] = at.toEpochMilli()
                }
            }
        }
    }

    override suspend fun ownedOf(memberId: MemberId): Set<String> =
        dbQuery(db) {
            AvatarItemTable
                .selectAll()
                .where { AvatarItemTable.memberId eq memberId.value }
                .map { it[AvatarItemTable.itemId] }
                .toSet()
        }

    override suspend fun purchase(
        memberId: MemberId,
        item: AvatarItem,
        debitEntryId: String,
        at: Instant,
    ): PurchaseOutcome =
        dbQuery(db) {
            val owned =
                AvatarItemTable
                    .selectAll()
                    .where { (AvatarItemTable.memberId eq memberId.value) and (AvatarItemTable.itemId eq item.id) }
                    .any()
            if (owned) return@dbQuery PurchaseOutcome.AlreadyOwned
            val balance = balanceOf(memberId)
            if (balance < item.price) return@dbQuery PurchaseOutcome.InsufficientBalance(balance, item.price)
            AvatarItemTable.insert {
                it[AvatarItemTable.memberId] = memberId.value
                it[itemId] = item.id
                it[price] = item.price
                it[purchasedAt] = at.toEpochMilli()
            }
            insertEntry(
                LedgerEntry(
                    LedgerEntryId(debitEntryId),
                    memberId,
                    EntryKind.SHOP,
                    -item.price,
                    "Shop: ${item.title}",
                    null,
                    shopSourceKey(memberId.value, item.id),
                    memberId,
                    at,
                ),
            )
            PurchaseOutcome.Purchased(balance - item.price)
        }
}

private fun List<ResultRow>.toStored() =
    StoredAvatar(
        items = associate { it[AvatarSlotTable.slot] to it[AvatarSlotTable.itemId] },
        updatedAt = Instant.ofEpochMilli(maxOf { it[AvatarSlotTable.updatedAt] }),
    )
