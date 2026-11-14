package de.familyplanner.android.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavBackStackEntry

/** Navigation-Compose blendet sonst 700 ms über; so lange wirkt jeder Bildschirmwechsel träge. */
internal const val NAV_TRANSITION_MS = 150

internal val navEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { fadeIn(tween(NAV_TRANSITION_MS)) }

internal val navExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { fadeOut(tween(NAV_TRANSITION_MS)) }
