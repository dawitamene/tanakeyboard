package com.addiyon.keyboard.review

internal interface ReviewLaunchToken

internal interface ReviewPlatform {
    fun requestReview(onResult: (Result<ReviewLaunchToken>) -> Unit)
    fun launchReview(
        token: ReviewLaunchToken,
        onResult: (Result<Unit>) -> Unit
    )
}

internal class ReviewPromptController(
    private val sessions: () -> Int,
    private val alreadyPrompted: () -> Boolean,
    private val markPrompted: () -> Unit,
    private val platform: ReviewPlatform,
    private val hostIsActive: () -> Boolean,
    private val onFailure: (Throwable) -> Unit = {}
) {
    private var consumed = false
    private var destroyed = false

    fun onNaturalMoment() {
        if (destroyed || consumed) return
        val eligible = try {
            ReviewPromptPolicy.shouldPrompt(sessions(), alreadyPrompted())
        } catch (failure: Throwable) {
            report(failure)
            false
        }
        if (!eligible) return
        consumed = true
        runCatching(markPrompted).onFailure(::report)
        try {
            platform.requestReview { result ->
                if (destroyed) return@requestReview
                result.fold(
                    onSuccess = { token ->
                        if (!active()) return@fold
                        try {
                            platform.launchReview(token) { launchResult ->
                                if (!destroyed) {
                                    launchResult.exceptionOrNull()?.let(::report)
                                }
                            }
                        } catch (failure: Throwable) {
                            report(failure)
                        }
                    },
                    onFailure = ::report
                )
            }
        } catch (failure: Throwable) {
            report(failure)
        }
    }

    fun onDestroy() {
        destroyed = true
    }

    private fun active(): Boolean =
        !destroyed && runCatching(hostIsActive).getOrDefault(false)

    private fun report(failure: Throwable) {
        runCatching { onFailure(failure) }
    }
}
