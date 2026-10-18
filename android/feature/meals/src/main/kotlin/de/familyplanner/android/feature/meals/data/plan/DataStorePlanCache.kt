package de.familyplanner.android.feature.meals.data.plan

import android.util.Log
import androidx.datastore.core.DataStore
import de.familyplanner.android.feature.meals.data.parseDate
import de.familyplanner.android.feature.meals.domain.attendance.mondayOf
import de.familyplanner.android.feature.meals.domain.plan.PlanCache
import de.familyplanner.android.feature.meals.domain.plan.PlanLimits
import de.familyplanner.android.feature.meals.domain.plan.WeekPlanSnapshot
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.LocalDate

private const val TAG = "DataStorePlanCache"

class DataStorePlanCache(
    private val dataStore: DataStore<StoredPlanCache>,
) : PlanCache {
    override suspend fun read(
        memberId: String,
        weekStart: LocalDate,
    ): WeekPlanSnapshot? =
        try {
            val stored = dataStore.data.first()
            if (stored.memberId != memberId) null else stored.weeks.firstOrNull { it.weekStart == weekStart.toString() }?.toDomain()
        } catch (e: IOException) {
            Log.w(TAG, "Meal plan cache unreadable", e)
            null
        }

    override suspend fun write(
        memberId: String,
        week: WeekPlanSnapshot,
        today: LocalDate,
    ) {
        val oldest = mondayOf(today).minusWeeks(1)
        try {
            dataStore.updateData { current ->
                val others =
                    if (current.memberId == memberId) current.weeks.filter { it.weekStart != week.weekStart.toString() } else emptyList()
                val kept =
                    (others + week.toStored())
                        .filter { (parseDate(it.weekStart) ?: LocalDate.MIN) >= oldest }
                        .sortedByDescending { it.syncedAtMillis }
                        .take(PlanLimits.CACHED_WEEKS_MAX)
                StoredPlanCache(memberId = memberId, weeks = kept)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Meal plan cache not written", e)
        }
    }
}
