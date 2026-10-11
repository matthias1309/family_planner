package de.familyplanner.android.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.familyplanner.android.core.data.AppError

@StringRes
fun AppError.messageRes(): Int =
    when (this) {
        AppError.Network -> R.string.error_network
        AppError.Certificate -> R.string.error_certificate
        AppError.Unauthorized -> R.string.error_unauthorized
        AppError.InvalidPairingCode -> R.string.error_invalid_pairing_code
        AppError.Forbidden -> R.string.error_forbidden
        AppError.NotFound -> R.string.error_not_found
        AppError.RateLimited -> R.string.error_rate_limited
        AppError.SetupDisabled -> R.string.error_setup_disabled
        is AppError.Validation -> R.string.error_validation
        is AppError.Conflict -> R.string.error_conflict
        AppError.Server -> R.string.error_server
    }

/** Nutzermeldung; bei Validierung/Konflikt hat die (deutsche) Servermeldung Vorrang. */
@Composable
fun AppError.message(): String {
    val serverMessage =
        when (this) {
            is AppError.Validation -> message
            is AppError.Conflict -> message
            else -> ""
        }
    return serverMessage.ifBlank { stringResource(messageRes()) }
}
