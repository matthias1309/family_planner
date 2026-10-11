package de.familyplanner.core

import de.familyplanner.core.device.DeviceRepository
import de.familyplanner.core.device.DeviceService
import de.familyplanner.core.device.ExposedDeviceRepository
import de.familyplanner.core.member.ExposedMemberRepository
import de.familyplanner.core.member.MemberRepository
import de.familyplanner.core.member.MemberService
import de.familyplanner.core.pairing.ExposedPairingCodeRepository
import de.familyplanner.core.pairing.PairingCodeRepository
import de.familyplanner.core.pairing.PairingService
import de.familyplanner.core.pairing.SetupService
import de.familyplanner.core.persistence.DatabaseFactory
import de.familyplanner.core.security.SecretGenerator
import de.familyplanner.core.security.SecureRandomSecretGenerator
import org.koin.dsl.module
import java.nio.file.Path
import java.time.Clock

fun coreModule(
    dbPath: Path,
    setupToken: String?,
    clock: Clock,
) = module {
    single(createdAtStart = true) { DatabaseFactory.create(dbPath, clock) }
    single<SecretGenerator> { SecureRandomSecretGenerator() }
    single<MemberRepository> { ExposedMemberRepository(get()) }
    single<DeviceRepository> { ExposedDeviceRepository(get()) }
    single<PairingCodeRepository> { ExposedPairingCodeRepository(get()) }
    single { MemberService(get(), clock) }
    single { DeviceService(get(), clock) }
    single { PairingService(get(), get(), get(), clock) }
    single { SetupService(setupToken, get(), get(), clock) }
}
