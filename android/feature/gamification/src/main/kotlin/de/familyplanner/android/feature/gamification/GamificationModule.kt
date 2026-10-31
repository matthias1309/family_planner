package de.familyplanner.android.feature.gamification

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import de.familyplanner.android.feature.gamification.data.AndroidRewardNotifier
import de.familyplanner.android.feature.gamification.data.DataStorePointsCache
import de.familyplanner.android.feature.gamification.data.DataStoreRedemptionNoticeStore
import de.familyplanner.android.feature.gamification.data.PointsCacheSerializer
import de.familyplanner.android.feature.gamification.data.RedemptionStoreSerializer
import de.familyplanner.android.feature.gamification.data.RemotePointsRepository
import de.familyplanner.android.feature.gamification.data.RemoteRewardRepository
import de.familyplanner.android.feature.gamification.data.WorkManagerRewardSync
import de.familyplanner.android.feature.gamification.domain.BookPointsUseCase
import de.familyplanner.android.feature.gamification.domain.ChildRewardActions
import de.familyplanner.android.feature.gamification.domain.CookingPointsUseCase
import de.familyplanner.android.feature.gamification.domain.CountPendingRedemptionsUseCase
import de.familyplanner.android.feature.gamification.domain.LoadBadgesUseCase
import de.familyplanner.android.feature.gamification.domain.LoadChildAccountUseCase
import de.familyplanner.android.feature.gamification.domain.LoadChildRewardsUseCase
import de.familyplanner.android.feature.gamification.domain.LoadManagedRewardsUseCase
import de.familyplanner.android.feature.gamification.domain.LoadPointsUseCase
import de.familyplanner.android.feature.gamification.domain.LoadTodaySavingsUseCase
import de.familyplanner.android.feature.gamification.domain.ManageRewardActions
import de.familyplanner.android.feature.gamification.domain.PointsCache
import de.familyplanner.android.feature.gamification.domain.PointsRepository
import de.familyplanner.android.feature.gamification.domain.RedemptionNoticeStore
import de.familyplanner.android.feature.gamification.domain.RewardNotifier
import de.familyplanner.android.feature.gamification.domain.RewardRepository
import de.familyplanner.android.feature.gamification.domain.RewardSync
import de.familyplanner.android.feature.gamification.domain.notice.SyncRewardNoticesUseCase
import de.familyplanner.android.feature.gamification.ui.account.ChildAccountViewModel
import de.familyplanner.android.feature.gamification.ui.badges.MemberBadgesViewModel
import de.familyplanner.android.feature.gamification.ui.family.FamilyPointsViewModel
import de.familyplanner.android.feature.gamification.ui.manage.ManageRewardsViewModel
import de.familyplanner.android.feature.gamification.ui.me.MeViewModel
import de.familyplanner.android.feature.gamification.ui.rewards.RewardsViewModel
import de.familyplanner.android.feature.gamification.ui.today.TodaySavingsViewModel
import de.familyplanner.api.client.GamificationApi
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

private const val POINTS_CACHE_FILE = "game_points.json"
private const val REDEMPTION_STORE_FILE = "game_redemptions.json"

val gamificationModule =
    module {
        single { get<Retrofit>().create(GamificationApi::class.java) }
        single<PointsRepository> { RemotePointsRepository(get(), get()) }
        // Genau eine DataStore-Instanz je Datei und Prozess, sonst IllegalStateException.
        single<PointsCache> {
            DataStorePointsCache(
                DataStoreFactory.create(
                    serializer = PointsCacheSerializer(get()),
                    produceFile = { androidContext().dataStoreFile(POINTS_CACHE_FILE) },
                ),
            )
        }

        single<RewardRepository> { RemoteRewardRepository(get(), get()) }
        single<RedemptionNoticeStore> {
            DataStoreRedemptionNoticeStore(
                DataStoreFactory.create(
                    serializer = RedemptionStoreSerializer(get()),
                    produceFile = { androidContext().dataStoreFile(REDEMPTION_STORE_FILE) },
                ),
            )
        }
        single<RewardNotifier> { AndroidRewardNotifier(androidContext()) }
        single<RewardSync> { WorkManagerRewardSync(androidContext()) }
        // single wegen des Mutex gegen doppelte Meldungen
        single { SyncRewardNoticesUseCase(get(), get(), get(), get(), get()) }
        single { RewardOpenRequests() }

        factory { LoadPointsUseCase(get(), get(), get(), get()) }
        factory { LoadChildRewardsUseCase(get()) }
        factory { ChildRewardActions(get(), get()) }
        factory { LoadManagedRewardsUseCase(get(), get()) }
        factory { ManageRewardActions(get()) }
        factory { CountPendingRedemptionsUseCase(get()) }
        factory { LoadTodaySavingsUseCase(get(), get(), get()) }
        factory { LoadChildAccountUseCase(get()) }
        factory { LoadBadgesUseCase(get()) }
        factory { BookPointsUseCase(get()) }
        factory { CookingPointsUseCase(get()) }
        viewModel { MeViewModel(get(), get()) }
        viewModel { FamilyPointsViewModel(get(), get(), get()) }
        viewModel { RewardsViewModel(get(), get(), get()) }
        viewModel { ManageRewardsViewModel(get(), get()) }
        viewModel { TodaySavingsViewModel(get()) }
        viewModel { params -> ChildAccountViewModel(params.get(), get(), get(), get()) }
        viewModel { params -> MemberBadgesViewModel(params.get(), get()) }
    }
