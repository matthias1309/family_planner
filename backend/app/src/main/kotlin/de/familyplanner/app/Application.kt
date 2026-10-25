package de.familyplanner.app

import de.familyplanner.app.plugins.configureMonitoring
import de.familyplanner.app.plugins.configureRateLimiting
import de.familyplanner.app.plugins.configureSecurity
import de.familyplanner.app.plugins.configureSerialization
import de.familyplanner.app.plugins.configureStatusPages
import de.familyplanner.core.coreModule
import de.familyplanner.core.device.DeviceService
import de.familyplanner.core.events.DomainEventDispatcher
import de.familyplanner.core.member.MemberService
import de.familyplanner.core.pairing.PairingService
import de.familyplanner.core.pairing.SetupService
import de.familyplanner.core.web.authRoutes
import de.familyplanner.core.web.deviceRoutes
import de.familyplanner.core.web.memberRoutes
import de.familyplanner.gamification.POINTS_SUBSCRIBER
import de.familyplanner.gamification.account.AccountService
import de.familyplanner.gamification.gamificationModule
import de.familyplanner.gamification.ledger.PointsEventHandler
import de.familyplanner.gamification.settings.GameSettingsService
import de.familyplanner.gamification.web.gamificationRoutes
import de.familyplanner.meals.attendance.AttendanceService
import de.familyplanner.meals.attendance.DeadlineSettingsService
import de.familyplanner.meals.mealsModule
import de.familyplanner.meals.plan.CookingConfirmationService
import de.familyplanner.meals.plan.CookingWeekService
import de.familyplanner.meals.plan.DishReviewService
import de.familyplanner.meals.plan.MealPlanService
import de.familyplanner.meals.recipe.RecipeService
import de.familyplanner.meals.shopping.ShoppingListService
import de.familyplanner.meals.web.attendanceRoutes
import de.familyplanner.meals.web.cookingRoutes
import de.familyplanner.meals.web.planRoutes
import de.familyplanner.meals.web.recipeRoutes
import de.familyplanner.meals.web.shoppingRoutes
import de.familyplanner.tasks.board.BoardService
import de.familyplanner.tasks.catalog.CatalogService
import de.familyplanner.tasks.tasksModule
import de.familyplanner.tasks.web.taskRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.io.FileInputStream
import java.security.KeyStore
import java.time.Clock
import java.time.ZoneOffset

private const val LOOPBACK = "127.0.0.1"

fun main() {
    val config = AppConfig.fromEnv()
    buildServer(config).start(wait = true)
}

private fun buildServer(config: AppConfig): EmbeddedServer<*, *> =
    embeddedServer(
        Netty,
        configure = {
            val tls = config.tls
            if (tls != null) {
                val keyStore =
                    KeyStore.getInstance("PKCS12").apply {
                        FileInputStream(tls.keystorePath.toFile()).use { load(it, tls.keystorePassword.toCharArray()) }
                    }
                sslConnector(keyStore, tls.keyAlias, { tls.keystorePassword.toCharArray() }, { tls.keystorePassword.toCharArray() }) {
                    port = config.port
                }
            } else {
                connector { port = config.port }
            }
            connector {
                host = LOOPBACK
                port = config.healthPort
            }
        },
        module = { module(config) },
    )

fun Application.module(
    config: AppConfig,
    clock: Clock = Clock.tickMillis(ZoneOffset.UTC),
) {
    install(Koin) {
        slf4jLogger()
        modules(coreModule(config.dbPath, config.setupToken, clock), mealsModule(clock), tasksModule(clock), gamificationModule(clock))
    }
    // Subscribe before the first request and before the catch-up delivery below (ADR-0016).
    val pointsHandler = get<PointsEventHandler>()
    get<DomainEventDispatcher>().subscribe(POINTS_SUBSCRIBER) { pointsHandler.handle(it) }
    configureSerialization()
    configureStatusPages()
    configureMonitoring()
    configureRateLimiting(config.rateLimits)
    configureSecurity(get<DeviceService>())

    routing {
        healthRoutes(get<Database>(), config.version)
        authRoutes(get<SetupService>(), get<PairingService>())
        memberRoutes(get<MemberService>(), get<PairingService>())
        deviceRoutes(get<DeviceService>())
        attendanceRoutes(get<AttendanceService>(), get<DeadlineSettingsService>())
        planRoutes(get<MealPlanService>())
        cookingRoutes(get<DishReviewService>(), get<CookingWeekService>(), get<CookingConfirmationService>())
        recipeRoutes(get<RecipeService>())
        shoppingRoutes(get<ShoppingListService>())
        taskRoutes(get<CatalogService>(), get<BoardService>())
        gamificationRoutes(get<AccountService>(), get<GameSettingsService>())
    }

    // Catch-up delivery for events logged before the last shutdown (ADR-0016).
    monitor.subscribe(ApplicationStarted) { launch { get<DomainEventDispatcher>().dispatchPending() } }
}
