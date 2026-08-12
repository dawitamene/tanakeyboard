package com.addiyon.keyboard.features.appshell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardExternalActionRunnerTest {
    @Test
    fun missingHandlerShowsFallbackWithoutLaunching() {
        var launched = false
        var failed = false

        val result = KeyboardExternalActionRunner.run(
            canLaunch = { false },
            launch = { launched = true },
            onFailure = { failed = true }
        )

        assertFalse(result)
        assertFalse(launched)
        assertTrue(failed)
    }

    @Test
    fun resolverAndLauncherFailuresShowFallback() {
        var resolverFailure = false
        assertFalse(
            KeyboardExternalActionRunner.run(
                canLaunch = { throw SecurityException("blocked") },
                launch = {},
                onFailure = { resolverFailure = true }
            )
        )
        assertTrue(resolverFailure)

        var launchFailure = false
        assertFalse(
            KeyboardExternalActionRunner.run(
                canLaunch = { true },
                launch = { throw RuntimeException("removed") },
                onFailure = { launchFailure = true }
            )
        )
        assertTrue(launchFailure)
    }

    @Test
    fun successfulHandlerLaunchesExactlyOnce() {
        var launches = 0
        var failures = 0

        assertTrue(
            KeyboardExternalActionRunner.run(
                canLaunch = { true },
                launch = { launches += 1 },
                onFailure = { failures += 1 }
            )
        )
        assertTrue(launches == 1)
        assertTrue(failures == 0)
    }
}
