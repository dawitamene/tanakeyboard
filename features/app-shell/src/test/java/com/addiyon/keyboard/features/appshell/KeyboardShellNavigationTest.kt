package com.addiyon.keyboard.features.appshell

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardShellNavigationTest {
    private val destinations = setOf(
        KeyboardShellDestinations.THEMES,
        KeyboardShellDestinations.GUIDE,
        KeyboardShellDestinations.PREFERENCES,
        KeyboardShellDestinations.FEEDBACK
    )

    @Test
    fun onboardingGatesEveryDestinationUntilTheKeyboardIsDefault() {
        assertEquals(
            KeyboardShellNavigation.ONBOARDING,
            KeyboardShellNavigation.resolveDestination(
                KeyboardShellDestinations.THEMES,
                keyboardIsDefault = false,
                destinationIds = destinations
            )
        )
    }

    @Test
    fun validRequestsOpenDirectlyAndUnknownRequestsFallBackToSettings() {
        assertEquals(
            KeyboardShellDestinations.THEMES,
            KeyboardShellNavigation.resolveDestination(
                KeyboardShellDestinations.THEMES,
                keyboardIsDefault = true,
                destinationIds = destinations
            )
        )
        assertEquals(
            KeyboardShellDestinations.FEEDBACK,
            KeyboardShellNavigation.resolveDestination(
                KeyboardShellDestinations.FEEDBACK,
                keyboardIsDefault = true,
                destinationIds = destinations
            )
        )
        assertEquals(
            KeyboardShellDestinations.SETTINGS,
            KeyboardShellNavigation.resolveDestination(
                "UNKNOWN",
                keyboardIsDefault = true,
                destinationIds = destinations
            )
        )
    }

    @Test
    fun backFinishesOnlyFromTheKeyboardEntryDestination() {
        assertEquals(
            KeyboardBackTarget.Finish,
            KeyboardShellNavigation.backTarget(
                currentDestinationId = KeyboardShellDestinations.THEMES,
                keyboardEntryDestinationId = KeyboardShellDestinations.THEMES,
                openedFromKeyboard = true,
                parentDestinationId = KeyboardShellDestinations.SETTINGS
            )
        )
        assertEquals(
            KeyboardBackTarget.Navigate(KeyboardShellDestinations.PREFERENCES),
            KeyboardShellNavigation.backTarget(
                currentDestinationId = KeyboardShellDestinations.KEYBOARD_HEIGHT,
                keyboardEntryDestinationId = KeyboardShellDestinations.THEMES,
                openedFromKeyboard = true,
                parentDestinationId = KeyboardShellDestinations.PREFERENCES
            )
        )
        assertEquals(
            KeyboardBackTarget.Finish,
            KeyboardShellNavigation.backTarget(
                currentDestinationId = KeyboardShellDestinations.FEEDBACK,
                keyboardEntryDestinationId = KeyboardShellDestinations.FEEDBACK,
                openedFromKeyboard = true,
                parentDestinationId = KeyboardShellDestinations.SETTINGS
            )
        )
    }
}
