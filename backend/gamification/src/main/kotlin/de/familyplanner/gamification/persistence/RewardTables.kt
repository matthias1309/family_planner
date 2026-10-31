package de.familyplanner.gamification.persistence

import org.jetbrains.exposed.v1.core.Table

object RewardTable : Table("game_rewards") {
    val id = varchar("id", ID_LENGTH)
    val title = varchar("title", TITLE_LENGTH)
    val emoji = varchar("emoji", EMOJI_LENGTH).nullable()
    val price = integer("price")
    val archivedAt = long("archived_at").nullable()
    val createdBy = varchar("created_by", ID_LENGTH).nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object RedemptionTable : Table("game_redemptions") {
    val seq = long("seq").autoIncrement()
    val id = varchar("id", ID_LENGTH)
    val memberId = varchar("member_id", ID_LENGTH)
    val rewardId = varchar("reward_id", ID_LENGTH)
    val title = varchar("title", TITLE_LENGTH)
    val emoji = varchar("emoji", EMOJI_LENGTH).nullable()
    val price = integer("price")
    val status = varchar("status", STATUS_LENGTH)
    val rejectionReason = varchar("rejection_reason", REASON_LENGTH).nullable()
    val requestedAt = long("requested_at")
    val decidedBy = varchar("decided_by", ID_LENGTH).nullable()
    val decidedAt = long("decided_at").nullable()

    override val primaryKey = PrimaryKey(seq)
}

object SavingsGoalTable : Table("game_savings_goals") {
    val memberId = varchar("member_id", ID_LENGTH)
    val rewardId = varchar("reward_id", ID_LENGTH)
    val setAt = long("set_at")

    override val primaryKey = PrimaryKey(memberId)
}

private const val ID_LENGTH = 36
private const val TITLE_LENGTH = 40
private const val EMOJI_LENGTH = 16
private const val STATUS_LENGTH = 10
private const val REASON_LENGTH = 200
