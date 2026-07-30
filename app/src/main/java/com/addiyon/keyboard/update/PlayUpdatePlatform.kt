package com.addiyon.keyboard.update

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

internal class PlayUpdatePlatform private constructor(
    private val manager: AppUpdateManager,
    private val updateFlow: ActivityResultLauncher<IntentSenderRequest>
) : UpdatePlatform {
    private var registeredCallback: ((UpdateInstallState) -> Unit)? = null
    private var registeredListener: InstallStateUpdatedListener? = null

    override fun registerInstallListener(listener: (UpdateInstallState) -> Unit) {
        check(registeredListener == null)
        val playListener = InstallStateUpdatedListener { state ->
            listener(
                if (state.installStatus() == InstallStatus.DOWNLOADED) {
                    UpdateInstallState.DOWNLOADED
                } else {
                    UpdateInstallState.OTHER
                }
            )
        }
        manager.registerListener(playListener)
        registeredCallback = listener
        registeredListener = playListener
    }

    override fun unregisterInstallListener(listener: (UpdateInstallState) -> Unit) {
        if (registeredCallback !== listener) return
        registeredListener?.let(manager::unregisterListener)
        registeredCallback = null
        registeredListener = null
    }

    override fun requestUpdateInfo(onResult: (Result<UpdateSnapshot>) -> Unit) {
        try {
            manager.appUpdateInfo
                .addOnSuccessListener { info ->
                    onResult(Result.success(info.snapshot()))
                }
                .addOnFailureListener { failure ->
                    onResult(Result.failure(failure))
                }
        } catch (failure: Throwable) {
            onResult(Result.failure(failure))
        }
    }

    override fun startFlexibleUpdate(snapshot: UpdateSnapshot) {
        val token = snapshot.token as? PlayUpdateToken
            ?: throw IllegalArgumentException()
        manager.startUpdateFlowForResult(
            token.info,
            updateFlow,
            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
        )
    }

    override fun completeUpdate() {
        manager.completeUpdate()
    }

    private fun AppUpdateInfo.snapshot(): UpdateSnapshot =
        UpdateSnapshot(
            token = PlayUpdateToken(this),
            updateAvailable =
                updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE,
            flexibleAllowed = isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
            downloaded = installStatus() == InstallStatus.DOWNLOADED
        )

    private data class PlayUpdateToken(
        val info: AppUpdateInfo
    ) : UpdateLaunchToken

    internal companion object {
        fun create(
            activity: ComponentActivity,
            updateFlow: ActivityResultLauncher<IntentSenderRequest>
        ): Result<PlayUpdatePlatform> =
            runCatching {
                PlayUpdatePlatform(
                    manager = AppUpdateManagerFactory.create(activity),
                    updateFlow = updateFlow
                )
            }
    }
}
