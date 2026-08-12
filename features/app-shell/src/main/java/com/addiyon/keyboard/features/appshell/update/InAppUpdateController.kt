package com.addiyon.keyboard.features.appshell.update

import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts

internal class InAppUpdateController(
    private val activity: ComponentActivity,
    freshLaunch: Boolean,
    onReadyToInstall: () -> Unit,
    onFailure: (String, Throwable) -> Unit
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
                        onFailure = onFailure
                    )
                },
                onFailure = { failure ->
                    runCatching {
                        onFailure(
                            if (failure is OutOfMemoryError) {
                                "AppUpdateManagerFactory.create OOM"
                            } else {
                                "AppUpdateManagerFactory.create"
                            },
                            failure
                        )
                    }
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
