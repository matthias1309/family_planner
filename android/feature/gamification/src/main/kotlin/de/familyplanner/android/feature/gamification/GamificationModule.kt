package de.familyplanner.android.feature.gamification

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import de.familyplanner.android.feature.gamification.data.DataStorePointsCache
import de.familyplanner.android.feature.gamification.data.PointsCacheSerializer
import de.familyplanner.android.feature.gamification.data.RemotePointsRepository
import de.familyplanner.android.feature.gamification.domain.BookPointsUseCase
import de.familyplanner.android.feature.gamification.domain.CookingPointsUseCase
import de.familyplanner.android.feature.gamification.domain.LoadBadgesUseCase
import de.familyplanner.android.feature.gamification.domain.LoadChildAccountUseCase
import de.familyplanner.android.feature.gamification.domain.LoadPointsUseCase
import de.familyplanner.android.feature.gamification.domain.PointsCache
import de.familyplanner.android.feature.gamification.domain.PointsRepository
import de.familyplanner.android.feature.gamification.ui.account.ChildAccountViewModel
import de.familyplanner.android.feature.gamification.ui.badges.MemberBadgesViewModel
import de.familyplanner.android.feature.gamification.ui.family.FamilyPointsViewModel
import de.familyplanner.android.feature.gamification.ui.me.MeViewModel
import de.familyplanner.api.client.GamificationApi
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

private const val POINTS_CACHE_FILE = "game_points.json"

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

        factory { LoadPointsUseCase(get(), get(), get()) }
        factory { LoadChildAccountUseCase(get()) }
        factory { LoadBadgesUseCase(get()) }
        factory { BookPointsUseCase(get()) }
        factory { CookingPointsUseCase(get()) }
        viewModel { MeViewModel(get(), get()) }
        viewModel { FamilyPointsViewModel(get(), get()) }
        viewModel { params -> ChildAccountViewModel(params.get(), get(), get(), get()) }
        viewModel { params -> MemberBadgesViewModel(params.get(), get()) }
    }
