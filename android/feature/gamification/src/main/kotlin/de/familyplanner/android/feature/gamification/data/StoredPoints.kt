package de.familyplanner.android.feature.gamification.data

import de.familyplanner.android.feature.gamification.domain.EntryKind
import de.familyplanner.android.feature.gamification.domain.LedgerEntry
import de.familyplanner.android.feature.gamification.domain.PointsAccount
import de.familyplanner.android.feature.gamification.domain.PointsSnapshot
import de.familyplanner.android.feature.gamification.domain.RankedChild
import kotlinx.serialization.Serializable
import java.time.Instant

internal const val CACHE_VERSION = 1

/** Dateiformat des lokalen Punktestands (ADR-0011), getrennt von der Domäne; Zeitpunkte als Epoch-Millis. */
@Serializable
data class StoredPoints(
    val version: Int = CACHE_VERSION,
    val memberId: String? = null,
    val syncedAtMillis: Long = 0,
    val account: StoredAccount? = null,
    val leaderboard: List<StoredRankedChild> = emptyList(),
)

@Serializable
data class StoredAccount(
    val memberId: String,
    val collected: Int,
    val balance: Int,
    val entries: List<StoredEntry>,
)

@Serializable
data class StoredEntry(
    val id: String,
    val kind: String,
    val amount: Int,
    val title: String,
    val note: String? = null,
    val bookedAtMillis: Long,
)

@Serializable
data class StoredRankedChild(
    val memberId: String,
    val name: String,
    val rank: Int,
    val collected: Int,
)

internal fun PointsSnapshot.toStored(memberId: String) =
    StoredPoints(
        memberId = memberId,
        syncedAtMillis = syncedAt.toEpochMilli(),
        account = account?.toStored(),
        leaderboard = leaderboard.map { StoredRankedChild(it.memberId, it.name, it.rank, it.collected) },
    )

private fun PointsAccount.toStored() =
    StoredAccount(
        memberId,
        collected,
        balance,
        entries.map { StoredEntry(it.id, it.kind.name, it.amount, it.title, it.note, it.bookedAt.toEpochMilli()) },
    )

/** Besitzer und Stand; `null` ohne Besitzer oder mit unlesbarer Art — der Stand zählt dann als nicht gespeichert. */
internal fun StoredPoints.toDomain(): Pair<String, PointsSnapshot>? {
    val owner = memberId ?: return null
    val storedAccount = account
    val mappedAccount = storedAccount?.let { it.toDomain() ?: return null }
    val ranked = leaderboard.map { RankedChild(it.memberId, it.name, it.rank, it.collected) }
    return owner to PointsSnapshot(mappedAccount, ranked, Instant.ofEpochMilli(syncedAtMillis))
}

private fun StoredAccount.toDomain(): PointsAccount? {
    val mapped =
        entries.mapAllOrNull { entry ->
            val kind = EntryKind.entries.firstOrNull { it.name == entry.kind } ?: return@mapAllOrNull null
            LedgerEntry(entry.id, kind, entry.amount, entry.title, entry.note, Instant.ofEpochMilli(entry.bookedAtMillis))
        } ?: return null
    return PointsAccount(memberId, collected, balance, mapped)
}
