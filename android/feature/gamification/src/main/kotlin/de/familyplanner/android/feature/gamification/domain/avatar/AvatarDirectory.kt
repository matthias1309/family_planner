package de.familyplanner.android.feature.gamification.domain.avatar

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.ui.avatar.AvatarLook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Avatare aller Kinder für Rangliste, Board und „Heute“; offline aus dem lokalen Stand (ADR-0011). */
class AvatarDirectory(
    private val repository: AvatarRepository,
    private val cache: AvatarCache,
) {
    private val _looks = MutableStateFlow<Map<String, AvatarLook>>(emptyMap())
    val looks: StateFlow<Map<String, AvatarLook>> = _looks.asStateFlow()

    private val mutex = Mutex()
    private var loaded = false

    /** Lädt einmal je Prozess (nach Erfolg); weitere Aufrufe sind billig. */
    suspend fun ensureLoaded() {
        if (!loaded) refresh()
    }

    suspend fun refresh() =
        mutex.withLock {
            when (val result = repository.avatars()) {
                is ApiResult.Success -> {
                    _looks.value = result.data
                    cache.write(result.data)
                    loaded = true
                }
                is ApiResult.Failure -> onRefreshFailed(result.error)
            }
        }

    /** Nach eigenem Speichern sofort überall sichtbar. */
    suspend fun update(
        memberId: String,
        look: AvatarLook,
    ) = mutex.withLock {
        _looks.value = _looks.value + (memberId to look)
        cache.write(_looks.value)
    }

    private suspend fun onRefreshFailed(error: AppError) {
        if (error == AppError.Unauthorized) {
            cache.clear()
            _looks.value = emptyMap()
            loaded = false
        } else if (_looks.value.isEmpty()) {
            cache.read()?.let { _looks.value = it }
        }
    }
}
