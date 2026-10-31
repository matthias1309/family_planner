package de.familyplanner.gamification

import de.familyplanner.gamification.account.AccountService
import de.familyplanner.gamification.badges.BadgeRepository
import de.familyplanner.gamification.badges.BadgeService
import de.familyplanner.gamification.ledger.LedgerRepository
import de.familyplanner.gamification.ledger.PointsEventHandler
import de.familyplanner.gamification.persistence.ExposedBadgeRepository
import de.familyplanner.gamification.persistence.ExposedGameSettingsRepository
import de.familyplanner.gamification.persistence.ExposedLedgerRepository
import de.familyplanner.gamification.settings.GameSettingsRepository
import de.familyplanner.gamification.settings.GameSettingsService
import org.koin.dsl.module
import java.time.Clock

/** Stable cursor name of the points subscriber (ADR-0016) - never rename. */
const val POINTS_SUBSCRIBER = "gamification.points"

/** `Database` and `MemberRepository` come from `coreModule`. */
fun gamificationModule(clock: Clock) =
    module {
        single<LedgerRepository> { ExposedLedgerRepository(get()) }
        single<GameSettingsRepository> { ExposedGameSettingsRepository(get()) }
        single { GameSettingsService(get(), clock) }
        single<BadgeRepository> { ExposedBadgeRepository(get()) }
        single { BadgeService(get(), get(), get(), clock) }
        single { AccountService(get(), get(), get(), clock) }
        single { PointsEventHandler(get(), get(), get(), get()) }
    }
