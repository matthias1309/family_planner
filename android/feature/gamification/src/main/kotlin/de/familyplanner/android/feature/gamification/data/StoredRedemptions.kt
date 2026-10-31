package de.familyplanner.android.feature.gamification.data

import de.familyplanner.android.feature.gamification.domain.RedemptionStatus
import de.familyplanner.android.feature.gamification.domain.notice.RedemptionSnapshot
import kotlinx.serialization.Serializable
import java.time.Instant

internal const val REDEMPTION_STORE_VERSION = 1

/** Dateiformat des Abgleich-Stands der Einlösungen (ADR-0013), getrennt von der Domäne. */
@Serializable
data class StoredRedemptions(
    val version: Int = REDEMPTION_STORE_VERSION,
    val memberId: String? = null,
    val syncedAtMillis: Long = 0,
    val statuses: Map<String, String> = emptyMap(),
)

internal fun RedemptionSnapshot.toStored(memberId: String) =
    StoredRedemptions(
        memberId = memberId,
        syncedAtMillis = syncedAt.toEpochMilli(),
        statuses = statuses.mapValues { it.value.name },
    )

/** Besitzer und Stand; `null` ohne Besitzer. Unbekannte Status-Namen werden übersprungen. */
internal fun StoredRedemptions.toDomain(): Pair<String, RedemptionSnapshot>? {
    val owner = memberId ?: return null
    val known =
        statuses.mapNotNull { (id, name) -> RedemptionStatus.entries.firstOrNull { it.name == name }?.let { id to it } }.toMap()
    return owner to RedemptionSnapshot(known, Instant.ofEpochMilli(syncedAtMillis))
}
