package de.familyplanner.gamification.avatar

import de.familyplanner.core.error.AppException
import de.familyplanner.core.member.MemberId
import de.familyplanner.gamification.account.Actor
import de.familyplanner.gamification.fakes.GamificationFixture
import de.familyplanner.gamification.ledger.EntryKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val HEARTS = "eyes.hearts"
private const val HAT = "top.hat"

class AvatarServiceTest {
    private val fixture = GamificationFixture()

    private fun freeConfig() = AvatarConfig(AvatarSlot.entries.filter { !it.optional }.associateWith { AvatarCatalog.defaultOf(it).id })

    private fun AvatarConfig.with(
        slot: AvatarSlot,
        id: String,
    ) = AvatarConfig(items + (slot to id))

    private suspend fun balanceOf(child: Actor) =
        fixture.accounts
            .account(child, child.id, 10)
            .totals.balance

    private suspend fun credit(
        child: Actor,
        amount: Int,
    ) = fixture.accounts.book(fixture.parent(), child.id, amount, "Start")

    @Test
    fun `REQ-GAME-030 child saves avatar from free items and everyone sees it`() =
        runTest {
            val child = fixture.child("Mia")
            val sibling = fixture.child("Ben")

            fixture.avatars.save(child, child.id, freeConfig())

            val seen = fixture.avatars.all()
            assertEquals(listOf(child.id), seen.map { it.memberId })
            assertEquals(freeConfig(), seen.single().config)
            assertNotNull(fixture.avatars.profile(child, child.id).avatar)
            assertNull(fixture.avatars.profile(sibling, sibling.id).avatar)
        }

    @Test
    fun `REQ-GAME-030 child without avatar has null profile`() =
        runTest {
            val child = fixture.child()

            val profile = fixture.avatars.profile(child, child.id)

            assertNull(profile.avatar)
            assertTrue(profile.ownedItemIds.isEmpty())
            assertTrue(fixture.avatars.all().isEmpty())
        }

    @Test
    fun `REQ-GAME-030 all lists avatars sorted by name`() =
        runTest {
            val zoe = fixture.child("Zoe")
            val adam = fixture.child("Adam")

            fixture.avatars.save(zoe, zoe.id, freeConfig())
            fixture.avatars.save(adam, adam.id, freeConfig())

            assertEquals(listOf(adam.id, zoe.id), fixture.avatars.all().map { it.memberId })
        }

    @Test
    fun `REQ-GAME-030 missing required slot is rejected`() =
        runTest {
            val child = fixture.child()

            assertFailsWith<AppException.Validation> {
                fixture.avatars.save(child, child.id, AvatarConfig(freeConfig().items - AvatarSlot.MOUTH))
            }
        }

    @Test
    fun `REQ-GAME-030 unknown item and item of another slot are rejected`() =
        runTest {
            val child = fixture.child()

            assertFailsWith<AppException.Validation> {
                fixture.avatars.save(
                    child,
                    child.id,
                    freeConfig().with(AvatarSlot.EYES, "eyes.nope"),
                )
            }
            assertFailsWith<AppException.Validation> {
                fixture.avatars.save(
                    child,
                    child.id,
                    freeConfig().with(AvatarSlot.EYES, "mouth.smile"),
                )
            }
        }

    @Test
    fun `REQ-GAME-031 wearing an unowned paid item is a conflict`() =
        runTest {
            val child = fixture.child()

            assertFailsWith<AppException.Conflict> { fixture.avatars.save(child, child.id, freeConfig().with(AvatarSlot.TOP, HAT)) }
        }

    @Test
    fun `REQ-GAME-031 balance 12 and item 10 leaves balance 2 and collected unchanged`() =
        runTest {
            val child = fixture.child()
            credit(child, 12)

            val purchase = fixture.avatars.purchase(child, HEARTS)

            assertEquals(2, purchase.balance)
            assertEquals(2, balanceOf(child))
            assertEquals(
                12,
                fixture.accounts
                    .account(child, child.id, 10)
                    .totals.collected,
            )
            assertEquals(listOf(HEARTS), fixture.avatars.profile(child, child.id).ownedItemIds)
            assertEquals(
                EntryKind.SHOP,
                fixture.ledger.entries
                    .last()
                    .kind,
            )
        }

    @Test
    fun `REQ-GAME-031 balance 5 and item 10 is a conflict with the missing points`() =
        runTest {
            val child = fixture.child()
            credit(child, 5)

            val failure = assertFailsWith<AppException.Conflict> { fixture.avatars.purchase(child, HEARTS) }

            assertTrue(failure.message.orEmpty().contains("5 von 10"))
            assertEquals(5, balanceOf(child))
        }

    @Test
    fun `REQ-GAME-031 owned item can be worn and taken off for free and not bought twice`() =
        runTest {
            val child = fixture.child()
            credit(child, 50)
            fixture.avatars.purchase(child, HEARTS)
            val wearing = freeConfig().with(AvatarSlot.EYES, HEARTS)

            fixture.avatars.save(child, child.id, wearing)
            fixture.avatars.save(child, child.id, freeConfig())
            fixture.avatars.save(child, child.id, wearing)

            assertFailsWith<AppException.Conflict> { fixture.avatars.purchase(child, HEARTS) }
            assertEquals(40, balanceOf(child))
        }

    @Test
    fun `REQ-GAME-031 purchase does not change the worn avatar`() =
        runTest {
            val child = fixture.child()
            credit(child, 50)

            fixture.avatars.purchase(child, HEARTS)

            assertNull(fixture.avatars.profile(child, child.id).avatar)
        }

    @Test
    fun `REQ-GAME-031 free item cannot be bought and unknown item is not found`() =
        runTest {
            val child = fixture.child()
            credit(child, 50)

            assertFailsWith<AppException.Conflict> { fixture.avatars.purchase(child, "top.shortFlat") }
            assertFailsWith<AppException.NotFound> { fixture.avatars.purchase(child, "top.unknown") }
        }

    @Test
    fun `REQ-GAME-030 parents cannot buy or save but can read every profile`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()

            assertFailsWith<AppException.Forbidden> { fixture.avatars.purchase(parent, HEARTS) }
            assertFailsWith<AppException.Forbidden> { fixture.avatars.save(parent, child.id, freeConfig()) }
            assertNull(fixture.avatars.profile(parent, child.id).avatar)
        }

    @Test
    fun `REQ-GAME-030 sibling cannot read profile or save for another child`() =
        runTest {
            val child = fixture.child()
            val sibling = fixture.child()

            assertFailsWith<AppException.Forbidden> { fixture.avatars.profile(sibling, child.id) }
            assertFailsWith<AppException.Forbidden> { fixture.avatars.save(sibling, child.id, freeConfig()) }
        }

    @Test
    fun `REQ-GAME-030 parent target is a validation error and unknown member is not found`() =
        runTest {
            val parent = fixture.parent()

            assertFailsWith<AppException.Validation> { fixture.avatars.profile(parent, parent.id) }
            assertFailsWith<AppException.NotFound> { fixture.avatars.profile(parent, MemberId("nobody")) }
        }

    @Test
    fun `REQ-GAME-014 purchase does not change leaderboard position`() =
        runTest {
            val first = fixture.child("Anna")
            val second = fixture.child("Ben")
            credit(first, 30)
            credit(second, 20)
            val before = fixture.accounts.leaderboard()

            fixture.avatars.purchase(first, HAT)

            assertEquals(before, fixture.accounts.leaderboard())
        }

    @Test
    fun `REQ-GAME-030 stored items the catalog no longer knows are repaired when read`() =
        runTest {
            val child = fixture.child()
            val stored = freeConfig().items.mapKeys { it.key.key } + ("eyes" to "eyes.removed") + ("accessories" to "accessories.gone")
            fixture.avatarRepository.plant(child.id, stored, fixture.clock.instant())

            val avatar = fixture.avatars.profile(child, child.id).avatar

            assertEquals(AvatarCatalog.defaultOf(AvatarSlot.EYES).id, avatar?.items?.get(AvatarSlot.EYES))
            assertNull(avatar?.items?.get(AvatarSlot.ACCESSORIES))
        }

    @Test
    fun `REQ-GAME-030 catalog lists all slots in editor order`() {
        val slots = fixture.avatars.catalog().map { it.first }

        assertEquals(AvatarSlot.entries, slots)
    }
}
