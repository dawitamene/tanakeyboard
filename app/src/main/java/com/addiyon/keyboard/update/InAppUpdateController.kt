package com.addiyon.keyboard.update

import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.addiyon.keyboard.SafeLog
import com.addiyon.keyboard.telemetry.NonFatalCategory

class InAppUpdateController(
    private val activity: ComponentActivity,
    freshLaunch: Boolean,
    onReadyToInstall: () -> Unit
) {
    private val updateFlow =
        activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { }

    private val lifecycleController: UpdateLifecycleController? =
        PlayUpdatePlatform.create(activity, updateFlow)
            .fold(
                onSuccess = { platform ->
                    UpdateLifecycleController(
                        platform = platform,
                        freshLaunch = freshLaunch,
                        hostIsActive = {
                            !activity.isFinishing && !activity.isDestroyed
                        },
                        onReadyToInstall = onReadyToInstall,
                        onFailure = { operation, failure ->
                            SafeLog.e(
                                failure,
                                operation,
                                NonFatalCategory.UPDATE
                            )
                        }
                    )
                },
                onFailure = { failure ->
                    SafeLog.e(
                        failure,
                        if (failure is OutOfMemoryError) {
                            "AppUpdateManagerFactory.create OOM"
                        } else {
                            "AppUpdateManagerFactory.create"
                        },
                        NonFatalCategory.UPDATE
                    )
                    null
                }
            )

    fun onResume() {
        lifecycleController?.onResume()
    }

    fun onDestroy() {
        lifecycleController?.onDestroy()
    }

    fun completeUpdate() {
        lifecycleController?.completeUpdate()
    }
}
