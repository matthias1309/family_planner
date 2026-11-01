package de.familyplanner.gamification.avatar

import de.familyplanner.core.member.MemberId
import java.time.Instant

interface AvatarRepository {
    /** Stored slot rows of every member that has an avatar. */
    suspend fun findAll(): Map<MemberId, StoredAvatar>

    suspend fun find(memberId: MemberId): StoredAvatar?

    /** Replaces all slot rows of [memberId] in one transaction ([items]: slot key -> item id). */
    suspend fun save(
        memberId: MemberId,
        items: Map<String, String>,
        at: Instant,
    )

    /** Purchased item ids (not the free basic range). */
    suspend fun ownedOf(memberId: MemberId): Set<String>

    /**
     * In one transaction: not yet owned, balance >= price, inserts the ownership row and the SHOP debit
     * (title "Shop: <title>", source_key shop:<member>:<item>).
     */
    suspend fun purchase(
        memberId: MemberId,
        item: AvatarItem,
        debitEntryId: String,
        at: Instant,
    ): PurchaseOutcome
}

/** Slot key -> item id as stored, and when it was last saved. */
data class StoredAvatar(
    val items: Map<String, String>,
    val updatedAt: Instant,
)
