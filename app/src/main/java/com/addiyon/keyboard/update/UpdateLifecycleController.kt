package com.addiyon.keyboard.update

internal interface UpdateLaunchToken

internal data class UpdateSnapshot(
    val token: UpdateLaunchToken,
    val updateAvailable: Boolean,
    val flexibleAllowed: Boolean,
    val downloaded: Boolean
)

internal enum class UpdateInstallState {
    OTHER,
    DOWNLOADED
}

internal interface UpdatePlatform {
    fun registerInstallListener(listener: (UpdateInstallState) -> Unit)
    fun unregisterInstallListener(listener: (UpdateInstallState) -> Unit)
    fun requestUpdateInfo(onResult: (Result<UpdateSnapshot>) -> Unit)
    fun startFlexibleUpdate(snapshot: UpdateSnapshot)
    fun completeUpdate()
}

internal class UpdateLifecycleController(
    private val platform: UpdatePlatform,
    freshLaunch: Boolean,
    private val hostIsActive: () -> Boolean,
    private val onReadyToInstall: () -> Unit,
    private val onFailure: (String, Throwable) -> Unit = { _, _ -> }
) {
    private var alreadyPrompted = !freshLaunch
    private var destroyed = false
    private var infoRequestInFlight = false
    private var listenerRegistered = false
    private var readyDelivered = false
    private val installListener: (UpdateInstallState) -> Unit = { state ->
        if (state == UpdateInstallState.DOWNLOADED && active()) {
            publishReady()
        }
    }

    init {
        try {
            platform.registerInstallListener(installListener)
            listenerRegistered = true
        } catch (failure: Throwable) {
            report("registerListener", failure)
        }
    }

    fun onResume() {
        if (destroyed || infoRequestInFlight) return
        infoRequestInFlight = true
        try {
            platform.requestUpdateInfo { result ->
                infoRequestInFlight = false
                if (destroyed) return@requestUpdateInfo
                result.fold(
                    onSuccess = ::handle,
                    onFailure = { report("appUpdateInfo", it) }
                )
            }
        } catch (failure: Throwable) {
            infoRequestInFlight = false
            report("appUpdateInfo", failure)
        }
    }

    fun completeUpdate() {
        if (destroyed) return
        try {
            platform.completeUpdate()
        } catch (failure: Throwable) {
            report("completeUpdate", failure)
        }
    }

    fun onDestroy() {
        if (destroyed) return
        destroyed = true
        infoRequestInFlight = false
        if (!listenerRegistered) return
        listenerRegistered = false
        try {
            platform.unregisterInstallListener(installListener)
        } catch (failure: Throwable) {
            report("unregisterListener", failure)
        }
    }

    private fun handle(snapshot: UpdateSnapshot) {
        if (!active()) return
        if (snapshot.downloaded) {
            publishReady()
            return
        }
        if (
            !UpdatePromptPolicy.shouldPrompt(
                updateAvailable = snapshot.updateAvailable,
                flexibleAllowed = snapshot.flexibleAllowed,
                alreadyPrompted = alreadyPrompted
            )
        ) {
            return
        }
        alreadyPrompted = true
        try {
            platform.startFlexibleUpdate(snapshot)
        } catch (failure: Throwable) {
            report("startUpdateFlowForResult", failure)
        }
    }

    private fun publishReady() {
        if (readyDelivered) return
        readyDelivered = true
        runCatching(onReadyToInstall)
            .onFailure { report("onReadyToInstall", it) }
    }

    private fun active(): Boolean =
        !destroyed && runCatching(hostIsActive).getOrDefault(false)

    private fun report(operation: String, failure: Throwable) {
        runCatching { onFailure(operation, failure) }
    }
}
