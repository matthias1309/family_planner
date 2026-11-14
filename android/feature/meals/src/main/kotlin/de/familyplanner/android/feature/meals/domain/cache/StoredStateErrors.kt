package de.familyplanner.android.feature.meals.domain.cache

import de.familyplanner.android.core.data.AppError

/**
 * Verbindungsprobleme (`Network`, `Certificate`, `Server`): Ein gespeicherter Stand darf stehen bleiben (ADR-0011,
 * ADR-0018). Andere Fehler (403, 404, …) werden gezeigt und nie von gespeicherten Daten verdeckt.
 */
internal fun AppError.allowsStoredState() = this == AppError.Network || this == AppError.Certificate || this == AppError.Server
