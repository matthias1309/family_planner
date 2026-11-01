package de.familyplanner.gamification.avatar

import de.familyplanner.core.error.AppException
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.MemberRepository
import de.familyplanner.core.member.Role
import de.familyplanner.gamification.account.Actor
import de.familyplanner.gamification.requireChild
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

/** Avatar editor and shop: wearing items (REQ-GAME-030) and buying them with points (REQ-GAME-031). */
class AvatarService(
    private val members: MemberRepository,
    private val avatars: AvatarRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(AvatarService::class.java)

    fun catalog(): List<Pair<AvatarSlot, List<AvatarItem>>> = AvatarSlot.entries.map { it to AvatarCatalog.itemsOf(it) }

    /** Avatars of all children that saved one, sorted by name. */
    suspend fun all(): List<MemberAvatar> {
        val stored = avatars.findAll()
        return members
            .findAll()
            .filter { it.role == Role.CHILD }
            .sortedBy { it.name }
            .mapNotNull { member -> stored[member.id]?.let { memberAvatar(member.id, it) } }
    }

    suspend fun profile(
        actor: Actor,
        memberId: MemberId,
    ): AvatarProfile {
        members.requireChild(memberId)
        if (!actor.isParent && actor.id != memberId) throw AppException.Forbidden("Nur eigener Avatar")
        return profileOf(memberId)
    }

    suspend fun save(
        actor: Actor,
        memberId: MemberId,
        config: AvatarConfig,
    ): AvatarProfile {
        members.requireChild(memberId)
        if (actor.id != memberId) throw AppException.Forbidden("Nur das Kind selbst gestaltet seinen Avatar")
        val valid = validated(config, avatars.ownedOf(memberId))
        avatars.save(memberId, valid.items.mapKeys { it.key.key }, clock.instant())
        log.info("Avatar saved: {}", memberId.value)
        return profileOf(memberId)
    }

    suspend fun purchase(
        actor: Actor,
        itemId: String,
    ): AvatarPurchase {
        requireChildBuyer(actor)
        val item = purchasable(itemId)
        val outcome = avatars.purchase(actor.id, item, newId(), clock.instant())
        val purchased = outcome as? PurchaseOutcome.Purchased ?: throw failure(outcome)
        log.info("Avatar item purchased: {} by {}", item.id, actor.id.value)
        return AvatarPurchase(item, purchased.balance)
    }

    private suspend fun requireChildBuyer(actor: Actor) {
        if (actor.isParent) throw AppException.Forbidden("Nur Kinder kaufen im Shop")
        members.requireChild(actor.id)
    }

    private fun purchasable(itemId: String): AvatarItem {
        val item = AvatarCatalog.find(itemId) ?: throw AppException.NotFound("Teil nicht gefunden")
        if (item.isFree) throw AppException.Conflict("Dieses Teil ist kostenlos")
        return item
    }

    private fun failure(outcome: PurchaseOutcome): AppException =
        when (outcome) {
            is PurchaseOutcome.Purchased -> AppException.Conflict("Der Kauf ist bereits abgeschlossen")
            PurchaseOutcome.AlreadyOwned -> AppException.Conflict("Gehört dir schon")
            is PurchaseOutcome.InsufficientBalance ->
                AppException.Conflict("Nicht genug Guthaben: ${outcome.balance} von ${outcome.price} Punkten")
        }

    private suspend fun profileOf(memberId: MemberId): AvatarProfile {
        val stored = avatars.find(memberId)
        return AvatarProfile(
            memberId = memberId,
            avatar = stored?.let { sanitizedConfig(memberId, it) },
            ownedItemIds = avatars.ownedOf(memberId).sorted(),
        )
    }

    private fun memberAvatar(
        memberId: MemberId,
        stored: StoredAvatar,
    ) = MemberAvatar(memberId, sanitizedConfig(memberId, stored), stored.updatedAt)

    private fun sanitizedConfig(
        memberId: MemberId,
        stored: StoredAvatar,
    ): AvatarConfig {
        val result = sanitized(stored.items)
        if (result.repaired) log.warn("Stored avatar of {} contained items no longer in the catalog", memberId.value)
        return result.config
    }
}
