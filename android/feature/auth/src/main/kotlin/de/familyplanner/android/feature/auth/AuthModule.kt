package de.familyplanner.android.feature.auth

import android.os.Build
import de.familyplanner.android.feature.auth.data.RemoteAuthRepository
import de.familyplanner.android.feature.auth.data.RemoteFamilyRepository
import de.familyplanner.android.feature.auth.domain.AuthRepository
import de.familyplanner.android.feature.auth.domain.CreateMemberUseCase
import de.familyplanner.android.feature.auth.domain.CreatePairingCodeUseCase
import de.familyplanner.android.feature.auth.domain.FamilyRepository
import de.familyplanner.android.feature.auth.domain.LoadFamilyUseCase
import de.familyplanner.android.feature.auth.domain.PairDeviceUseCase
import de.familyplanner.android.feature.auth.domain.RefreshMeUseCase
import de.familyplanner.android.feature.auth.domain.RevokeDeviceUseCase
import de.familyplanner.android.feature.auth.domain.SetupFamilyUseCase
import de.familyplanner.android.feature.auth.ui.family.FamilyViewModel
import de.familyplanner.android.feature.auth.ui.home.HomeViewModel
import de.familyplanner.android.feature.auth.ui.pair.PairViewModel
import de.familyplanner.android.feature.auth.ui.setup.SetupViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val authModule =
    module {
        single<AuthRepository> { RemoteAuthRepository(get(), get()) }
        single<FamilyRepository> { RemoteFamilyRepository(get(), get(), get()) }
        factory { SetupFamilyUseCase(get(), get()) }
        factory { PairDeviceUseCase(get(), get()) }
        factory { RefreshMeUseCase(get(), get()) }
        factory { LoadFamilyUseCase(get()) }
        factory { CreateMemberUseCase(get()) }
        factory { CreatePairingCodeUseCase(get()) }
        factory { RevokeDeviceUseCase(get(), get()) }
        viewModel { PairViewModel(get(), defaultDeviceName = Build.MODEL.orEmpty()) }
        viewModel { SetupViewModel(get(), defaultDeviceName = Build.MODEL.orEmpty()) }
        viewModel { HomeViewModel(get(), get()) }
        viewModel { FamilyViewModel(get(), get(), get(), get(), get()) }
    }
