package de.familyplanner.gamification.persistence

import org.jetbrains.exposed.v1.core.Table

object AvatarItemTable : Table("game_avatar_items") {
    val memberId = varchar("member_id", ID_LENGTH)
    val itemId = varchar("item_id", ITEM_ID_LENGTH)
    val price = integer("price")
    val purchasedAt = long("purchased_at")

    override val primaryKey = PrimaryKey(memberId, itemId)
}

object AvatarSlotTable : Table("game_avatar_slots") {
    val memberId = varchar("member_id", ID_LENGTH)
    val slot = varchar("slot", SLOT_LENGTH)
    val itemId = varchar("item_id", ITEM_ID_LENGTH)
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(memberId, slot)
}

private const val ID_LENGTH = 36
private const val ITEM_ID_LENGTH = 64
private const val SLOT_LENGTH = 20
