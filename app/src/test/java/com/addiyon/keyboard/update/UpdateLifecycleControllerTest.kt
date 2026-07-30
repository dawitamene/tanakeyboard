package com.addiyon.keyboard.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLifecycleControllerTest {
    @Test
    fun dismissalResumeDoesNotRepromptDuringTheSameFreshLaunch() {
        val platform = FakeUpdatePlatform()
        val controller = controller(platform, freshLaunch = true)
        val available = snapshot()

        controller.onResume()
        platform.respond(Result.success(available))
        controller.onResume()
        platform.respond(Result.success(available))

        assertEquals(2, platform.requestCount)
        assertEquals(1, platform.started.size)
        assertSame(available, platform.started.single())
    }

    @Test
    fun requestFailureIsReportedAndCanRetryOnNextResume() {
        val platform = FakeUpdatePlatform()
        val failures = mutableListOf<Pair<String, Throwable>>()
        val controller = controller(platform, failures = failures)
        val failure = IllegalStateException()

        controller.onResume()
        platform.respond(Result.failure(failure))
        controller.onResume()
        platform.respond(Result.success(snapshot()))

        assertEquals(listOf("appUpdateInfo" to failure), failures)
        assertEquals(2, platform.requestCount)
        assertEquals(1, platform.started.size)
    }

    @Test
    fun downloadedInfoOrListenerPublishesReadyStateOnce() {
        val platform = FakeUpdatePlatform()
        var readyCount = 0
        val controller = controller(
            platform = platform,
            onReady = { readyCount += 1 }
        )

        platform.emit(UpdateInstallState.DOWNLOADED)
        controller.onResume()
        platform.respond(Result.success(snapshot(downloaded = true)))

        assertEquals(1, readyCount)
        assertTrue(platform.started.isEmpty())
    }

    @Test
    fun recreationDoesNotPromptButANewFreshLaunchDoes() {
        val recreatedPlatform = FakeUpdatePlatform()
        val recreated = controller(recreatedPlatform, freshLaunch = false)

        recreated.onResume()
        recreatedPlatform.respond(Result.success(snapshot()))

        assertTrue(recreatedPlatform.started.isEmpty())
        recreated.onDestroy()

        val freshPlatform = FakeUpdatePlatform()
        val fresh = controller(freshPlatform, freshLaunch = true)

        fresh.onResume()
        freshPlatform.respond(Result.success(snapshot()))

        assertEquals(1, freshPlatform.started.size)
    }

    @Test
    fun completeUpdateDelegatesOnlyWhileAlive() {
        val platform = FakeUpdatePlatform()
        val controller = controller(platform)

        controller.completeUpdate()
        controller.onDestroy()
        controller.completeUpdate()

        assertEquals(1, platform.completeCount)
    }

    @Test
    fun destroyUnregistersOnceAndIgnoresLateCallbacks() {
        val platform = FakeUpdatePlatform()
        var readyCount = 0
        val controller = controller(
            platform = platform,
            onReady = { readyCount += 1 }
        )

        controller.onResume()
        val staleListener = requireNotNull(platform.lastRegisteredListener)
        controller.onDestroy()
        controller.onDestroy()
        platform.respond(Result.success(snapshot()))
        staleListener(UpdateInstallState.DOWNLOADED)

        assertEquals(1, platform.unregisterCount)
        assertTrue(platform.started.isEmpty())
        assertEquals(0, readyCount)
    }

    private fun controller(
        platform: FakeUpdatePlatform,
        freshLaunch: Boolean = true,
        onReady: () -> Unit = {},
        failures: MutableList<Pair<String, Throwable>> = mutableListOf()
    ) = UpdateLifecycleController(
        platform = platform,
        freshLaunch = freshLaunch,
        hostIsActive = { true },
        onReadyToInstall = onReady,
        onFailure = { operation, failure ->
            failures += operation to failure
        }
    )

    private fun snapshot(
        updateAvailable: Boolean = true,
        flexibleAllowed: Boolean = true,
        downloaded: Boolean = false
    ) = UpdateSnapshot(
        token = FakeUpdateToken(),
        updateAvailable = updateAvailable,
        flexibleAllowed = flexibleAllowed,
        downloaded = downloaded
    )

    private class FakeUpdateToken : UpdateLaunchToken

    private class FakeUpdatePlatform : UpdatePlatform {
        var requestCount = 0
        var unregisterCount = 0
        var completeCount = 0
        val started = mutableListOf<UpdateSnapshot>()
        var lastRegisteredListener: ((UpdateInstallState) -> Unit)? = null
        private var requestResult: ((Result<UpdateSnapshot>) -> Unit)? = null

        override fun registerInstallListener(listener: (UpdateInstallState) -> Unit) {
            lastRegisteredListener = listener
        }

        override fun unregisterInstallListener(listener: (UpdateInstallState) -> Unit) {
            assertSame(lastRegisteredListener, listener)
            unregisterCount += 1
            lastRegisteredListener = null
        }

        override fun requestUpdateInfo(onResult: (Result<UpdateSnapshot>) -> Unit) {
            requestCount += 1
            requestResult = onResult
        }

        override fun startFlexibleUpdate(snapshot: UpdateSnapshot) {
            started += snapshot
        }

        override fun completeUpdate() {
            completeCount += 1
        }

        fun respond(result: Result<UpdateSnapshot>) {
            requireNotNull(requestResult).invoke(result)
        }

        fun emit(state: UpdateInstallState) {
            requireNotNull(lastRegisteredListener).invoke(state)
        }
    }
}
