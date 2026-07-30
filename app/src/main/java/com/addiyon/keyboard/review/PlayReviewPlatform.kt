package com.addiyon.keyboard.review

import androidx.activity.ComponentActivity
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory

internal class PlayReviewPlatform(
    private val activity: ComponentActivity
) : ReviewPlatform {
    override fun requestReview(onResult: (Result<ReviewLaunchToken>) -> Unit) {
        try {
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow()
                .addOnSuccessListener { info ->
                    onResult(Result.success(PlayReviewToken(manager, info)))
                }
                .addOnFailureListener { failure ->
                    onResult(Result.failure(failure))
                }
        } catch (failure: Throwable) {
            onResult(Result.failure(failure))
        }
    }

    override fun launchReview(
        token: ReviewLaunchToken,
        onResult: (Result<Unit>) -> Unit
    ) {
        val playToken = token as? PlayReviewToken
        if (playToken == null) {
            onResult(Result.failure(IllegalArgumentException()))
            return
        }
        try {
            playToken.manager.launchReviewFlow(activity, playToken.info)
                .addOnSuccessListener { onResult(Result.success(Unit)) }
                .addOnFailureListener { failure ->
                    onResult(Result.failure(failure))
                }
        } catch (failure: Throwable) {
            onResult(Result.failure(failure))
        }
    }

    private data class PlayReviewToken(
        val manager: ReviewManager,
        val info: ReviewInfo
    ) : ReviewLaunchToken
}
