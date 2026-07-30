package com.addiyon.keyboard.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPromptControllerTest {
    @Test
    fun requestSuccessLaunchesReviewOnce() {
        val platform = FakeReviewPlatform()
        val state = ReviewState()
        val controller = controller(platform, state)

        controller.onNaturalMoment()

        assertEquals(1, platform.requestCount)
        assertEquals(1, state.markCount)
        assertTrue(state.prompted)

        val token = FakeReviewToken()
        platform.respondToRequest(Result.success(token))

        assertEquals(1, platform.launches.size)
        assertSame(token, platform.launches.single())
    }

    @Test
    fun requestFailureIsReportedAndDoesNotReprompt() {
        val platform = FakeReviewPlatform()
        val state = ReviewState()
        val failures = mutableListOf<Throwable>()
        val controller = controller(platform, state, failures = failures)
        val failure = IllegalStateException()

        controller.onNaturalMoment()
        platform.respondToRequest(Result.failure(failure))
        controller.onNaturalMoment()

        assertEquals(listOf(failure), failures)
        assertEquals(1, platform.requestCount)
        assertTrue(platform.launches.isEmpty())
    }

    @Test
    fun launchCompletionOrDismissalDoesNotReprompt() {
        val platform = FakeReviewPlatform()
        val state = ReviewState()
        val controller = controller(platform, state)

        controller.onNaturalMoment()
        platform.respondToRequest(Result.success(FakeReviewToken()))
        platform.respondToLaunch(Result.success(Unit))
        controller.onNaturalMoment()

        assertEquals(1, platform.requestCount)
        assertEquals(1, platform.launches.size)
        assertEquals(1, state.markCount)
    }

    @Test
    fun recreationHonorsThePersistedOneShotDecision() {
        val state = ReviewState()
        val firstPlatform = FakeReviewPlatform()
        val first = controller(firstPlatform, state)

        first.onNaturalMoment()

        val recreatedPlatform = FakeReviewPlatform()
        val recreated = controller(recreatedPlatform, state)
        recreated.onNaturalMoment()

        assertEquals(1, firstPlatform.requestCount)
        assertEquals(0, recreatedPlatform.requestCount)
        assertEquals(1, state.markCount)
    }

    @Test
    fun destroyedHostIgnoresLateRequestSuccess() {
        val platform = FakeReviewPlatform()
        val state = ReviewState()
        val controller = controller(platform, state)

        controller.onNaturalMoment()
        controller.onDestroy()
        platform.respondToRequest(Result.success(FakeReviewToken()))

        assertTrue(platform.launches.isEmpty())
    }

    private fun controller(
        platform: FakeReviewPlatform,
        state: ReviewState,
        failures: MutableList<Throwable> = mutableListOf()
    ) = ReviewPromptController(
        sessions = { ReviewPromptPolicy.MIN_SESSIONS },
        alreadyPrompted = { state.prompted },
        markPrompted = {
            state.markCount += 1
            state.prompted = true
        },
        platform = platform,
        hostIsActive = { true },
        onFailure = failures::add
    )

    private class ReviewState {
        var prompted = false
        var markCount = 0
    }

    private class FakeReviewToken : ReviewLaunchToken

    private class FakeReviewPlatform : ReviewPlatform {
        var requestCount = 0
        val launches = mutableListOf<ReviewLaunchToken>()
        private var requestResult: ((Result<ReviewLaunchToken>) -> Unit)? = null
        private var launchResult: ((Result<Unit>) -> Unit)? = null

        override fun requestReview(onResult: (Result<ReviewLaunchToken>) -> Unit) {
            requestCount += 1
            requestResult = onResult
        }

        override fun launchReview(
            token: ReviewLaunchToken,
            onResult: (Result<Unit>) -> Unit
        ) {
            launches += token
            launchResult = onResult
        }

        fun respondToRequest(result: Result<ReviewLaunchToken>) {
            requireNotNull(requestResult).invoke(result)
        }

        fun respondToLaunch(result: Result<Unit>) {
            requireNotNull(launchResult).invoke(result)
        }
    }
}
