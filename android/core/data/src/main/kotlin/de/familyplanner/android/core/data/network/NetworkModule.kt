package de.familyplanner.android.core.data.network

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import de.familyplanner.android.core.data.session.AeadFactory
import de.familyplanner.android.core.data.session.DataStoreSessionStore
import de.familyplanner.android.core.data.session.DefaultSessionEvents
import de.familyplanner.android.core.data.session.SessionEvents
import de.familyplanner.android.core.data.session.SessionSerializer
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.api.client.AuthApi
import de.familyplanner.api.client.DevicesApi
import de.familyplanner.api.client.MembersApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

private const val CONNECT_TIMEOUT_S = 5L
private const val READ_TIMEOUT_S = 10L
private const val CALL_TIMEOUT_S = 15L
private const val SESSION_FILE = "session.pb"

fun createJson(): Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

fun createHttpClient(
    sessions: SessionStore,
    events: SessionEvents,
): OkHttpClient =
    OkHttpClient
        .Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor(sessions))
        .addInterceptor(UnauthorizedInterceptor(sessions, events))
        .build()

fun createRetrofit(
    config: ServerConfig,
    client: OkHttpClient,
    json: Json,
): Retrofit =
    Retrofit
        .Builder()
        .baseUrl(config.baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

val sessionModule =
    module {
        single<SessionEvents> { DefaultSessionEvents() }
        single<SessionStore> {
            val context = androidContext()
            val dataStore =
                DataStoreFactory.create(
                    serializer = SessionSerializer(AeadFactory.create(context)),
                    produceFile = { context.dataStoreFile(SESSION_FILE) },
                )
            DataStoreSessionStore(dataStore, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        }
    }

val networkModule =
    module {
        single { createJson() }
        single { createHttpClient(get(), get()) }
        single { createRetrofit(get(), get(), get()) }
        single { get<Retrofit>().create(AuthApi::class.java) }
        single { get<Retrofit>().create(MembersApi::class.java) }
        single { get<Retrofit>().create(DevicesApi::class.java) }
    }
