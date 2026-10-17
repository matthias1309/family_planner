package de.familyplanner.android.core.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataStoreSessionStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val session = Session("tok", "m", "Lea", Role.PARENT, "d")

    private fun aead(): Aead {
        AeadConfig.register()
        return KeysetHandle
            .generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private fun newStore(
        scope: CoroutineScope,
        aead: Aead,
    ) = DataStoreSessionStore(
        DataStoreFactory.create(SessionSerializer(aead), scope = scope) { folder.root.resolve("s.pb") },
        scope,
    )

    @Test
    fun `save is visible immediately and survives a new store instance`() =
        runBlocking {
            val aead = aead()
            val firstScope = CoroutineScope(Job())
            val first = newStore(firstScope, aead)
            first.save(session)
            assertEquals(session, first.current)
            firstScope.cancel()

            val secondScope = CoroutineScope(Job())
            val second = newStore(secondScope, aead)
            val state = second.state.filter { it != SessionState.Loading }.first()
            assertEquals(SessionState.SignedIn(session), state)
            secondScope.cancel()
        }

    @Test
    fun `clear removes the session`() =
        runBlocking {
            val scope = CoroutineScope(Job())
            val store = newStore(scope, aead())
            store.save(session)
            store.clear()
            assertNull(store.current)
            assertEquals(SessionState.SignedOut, store.state.first())
            scope.cancel()
        }

    @Test
    fun `empty storage reports signed out`() =
        runBlocking {
            val scope = CoroutineScope(Job())
            val store = newStore(scope, aead())
            assertEquals(SessionState.SignedOut, store.state.filter { it != SessionState.Loading }.first())
            scope.cancel()
        }

    @Test
    fun `unreadable storage reports signed out instead of staying in loading`() =
        runBlocking {
            val scope = CoroutineScope(Job())
            val failing =
                object : DataStore<StoredSession> {
                    override val data: Flow<StoredSession> = flow { throw IOException("disk") }

                    override suspend fun updateData(transform: suspend (t: StoredSession) -> StoredSession) = throw IOException("disk")
                }

            val store = DataStoreSessionStore(failing, scope)

            val state = withTimeout(TIMEOUT_MS) { store.state.filter { it != SessionState.Loading }.first() }
            assertEquals(SessionState.SignedOut, state)
            scope.cancel()
        }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
