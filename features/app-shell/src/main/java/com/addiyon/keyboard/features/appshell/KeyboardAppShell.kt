package com.addiyon.keyboard.features.appshell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.addiyon.keyboard.product.KeyboardProduct

object KeyboardShellDestinations {
    const val SETTINGS = "SETTINGS"
    const val THEMES = "THEMES"
    const val GUIDE = "GUIDE"
    const val FEEDBACK = "FEEDBACK"
    const val PREFERENCES = "PREFERENCES"
    const val KEYBOARD_HEIGHT = "KEYBOARD_HEIGHT"
    const val TEST_KEYBOARD = "TEST_KEYBOARD"
    const val PERSONAL_DICTIONARY = "PERSONAL_DICTIONARY"
    const val ABOUT = "ABOUT"
}

enum class KeyboardSettingsGroup {
    AI,
    PRIMARY,
    STORE,
    SUPPORT
}

sealed interface KeyboardShellMenuTarget {
    data class Destination(val id: String) : KeyboardShellMenuTarget

    class Action(val onClick: () -> Unit) : KeyboardShellMenuTarget
}

data class KeyboardShellMenuEntry(
    val icon: ImageVector,
    val label: String,
    val group: KeyboardSettingsGroup,
    val target: KeyboardShellMenuTarget,
    val badge: String? = null
)

class KeyboardDestinationScope internal constructor(
    val onBack: () -> Unit,
    val navigate: (String) -> Unit,
    val finish: () -> Unit,
    val openedFromKeyboard: Boolean
)

data class KeyboardShellDestination(
    val id: String,
    val parentId: String = KeyboardShellDestinations.SETTINGS,
    val content: @Composable (KeyboardDestinationScope) -> Unit
)

data class KeyboardAppShellConfig(
    val product: KeyboardProduct,
    val onboardingCopy: KeyboardOnboardingCopy,
    val header: @Composable () -> Unit,
    val destinations: List<KeyboardShellDestination>,
    val menuEntries: List<KeyboardShellMenuEntry>,
    val onOpenInputSettings: () -> Unit,
    val onShowInputMethodPicker: () -> Unit,
    val tourPages: List<KeyboardTourPage> = emptyList(),
    val isTourSeen: () -> Boolean = { true },
    val markTourSeen: () -> Unit = {},
    val settingsOverlay: @Composable () -> Unit = {}
) {
    init {
        require("app-shell" in product.featureIds)
        require(destinations.map { it.id }.distinct().size == destinations.size)
        require(destinations.none { it.id == KeyboardShellDestinations.SETTINGS })
        val destinationIds = destinations.mapTo(mutableSetOf()) { it.id }
        destinationIds += KeyboardShellDestinations.SETTINGS
        require(destinations.all { it.parentId in destinationIds })
        require(
            menuEntries.all { entry ->
                val target = entry.target
                target !is KeyboardShellMenuTarget.Destination || target.id in destinationIds
            }
        )
    }
}

sealed interface KeyboardBackTarget {
    data class Navigate(val destinationId: String) : KeyboardBackTarget

    data object Finish : KeyboardBackTarget
}

object KeyboardShellNavigation {
    fun resolveDestination(
        requestedDestinationId: String?,
        keyboardIsDefault: Boolean,
        destinationIds: Set<String>
    ): String = when {
        !keyboardIsDefault -> ONBOARDING
        requestedDestinationId == KeyboardShellDestinations.SETTINGS ->
            KeyboardShellDestinations.SETTINGS
        requestedDestinationId in destinationIds -> checkNotNull(requestedDestinationId)
        else -> KeyboardShellDestinations.SETTINGS
    }

    fun backTarget(
        currentDestinationId: String,
        keyboardEntryDestinationId: String?,
        openedFromKeyboard: Boolean,
        parentDestinationId: String
    ): KeyboardBackTarget = if (
        openedFromKeyboard && currentDestinationId == keyboardEntryDestinationId
    ) {
        KeyboardBackTarget.Finish
    } else {
        KeyboardBackTarget.Navigate(parentDestinationId)
    }

    const val ONBOARDING = "ONBOARDING"
}

@Composable
fun KeyboardAppShell(
    config: KeyboardAppShellConfig,
    status: KeyboardAppStatus,
    requestedDestinationId: String?,
    onDestinationRequestConsumed: () -> Unit,
    onFinish: () -> Unit,
    onOnboardingCompleted: () -> Unit,
    onSettingsShown: () -> Unit,
    overlay: @Composable () -> Unit = {}
) {
    val destinationsById = config.destinations.associateBy { it.id }
    val destinationIds = destinationsById.keys
    val requestedIsValid = status.isDefault && (
        requestedDestinationId == KeyboardShellDestinations.SETTINGS ||
            requestedDestinationId in destinationIds
        )
    var currentDestinationId by rememberSaveable {
        mutableStateOf(
            KeyboardShellNavigation.resolveDestination(
                requestedDestinationId,
                status.isDefault,
                destinationIds
            )
        )
    }
    var openedFromKeyboard by rememberSaveable { mutableStateOf(requestedIsValid) }
    var keyboardEntryDestinationId by rememberSaveable {
        mutableStateOf(requestedDestinationId?.takeIf { requestedIsValid })
    }

    fun navigate(destinationId: String) {
        if (destinationId == KeyboardShellDestinations.SETTINGS || destinationId in destinationIds) {
            currentDestinationId = destinationId
        }
    }

    fun goBack() {
        val destination = destinationsById[currentDestinationId]
        val target = KeyboardShellNavigation.backTarget(
            currentDestinationId = currentDestinationId,
            keyboardEntryDestinationId = keyboardEntryDestinationId,
            openedFromKeyboard = openedFromKeyboard,
            parentDestinationId = destination?.parentId ?: KeyboardShellDestinations.SETTINGS
        )
        when (target) {
            KeyboardBackTarget.Finish -> onFinish()
            is KeyboardBackTarget.Navigate -> currentDestinationId = target.destinationId
        }
    }

    LaunchedEffect(status.isDefault) {
        if (!status.isDefault) {
            currentDestinationId = KeyboardShellNavigation.ONBOARDING
        } else if (currentDestinationId == KeyboardShellNavigation.ONBOARDING) {
            currentDestinationId = KeyboardShellDestinations.SETTINGS
        }
    }

    LaunchedEffect(requestedDestinationId, status.isDefault) {
        if (requestedDestinationId != null && status.isDefault) {
            val validRequest = requestedDestinationId == KeyboardShellDestinations.SETTINGS ||
                requestedDestinationId in destinationIds
            if (validRequest) {
                currentDestinationId = checkNotNull(requestedDestinationId)
                openedFromKeyboard = true
                keyboardEntryDestinationId = requestedDestinationId
            }
            onDestinationRequestConsumed()
        }
    }

    LaunchedEffect(currentDestinationId, destinationIds) {
        val currentIsValid = currentDestinationId == KeyboardShellNavigation.ONBOARDING ||
            currentDestinationId == KeyboardShellDestinations.SETTINGS ||
            currentDestinationId in destinationIds
        if (!currentIsValid) currentDestinationId = KeyboardShellDestinations.SETTINGS
    }

    LaunchedEffect(currentDestinationId) {
        if (currentDestinationId == KeyboardShellDestinations.SETTINGS) onSettingsShown()
    }

    BackHandler(
        enabled = currentDestinationId != KeyboardShellDestinations.SETTINGS &&
            currentDestinationId != KeyboardShellNavigation.ONBOARDING,
        onBack = ::goBack
    )

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentDestinationId) {
            KeyboardShellNavigation.ONBOARDING -> KeyboardOnboardingScreen(
                status = status,
                copy = config.onboardingCopy,
                header = config.header,
                onOpenSettings = config.onOpenInputSettings,
                onShowPicker = config.onShowInputMethodPicker,
                onDone = {
                    onOnboardingCompleted()
                    currentDestinationId = KeyboardShellDestinations.SETTINGS
                },
                tourPages = config.tourPages,
                isTourSeen = config.isTourSeen,
                markTourSeen = config.markTourSeen
            )
            KeyboardShellDestinations.SETTINGS -> {
                fun items(group: KeyboardSettingsGroup) = config.menuEntries
                    .filter { it.group == group }
                    .map { entry ->
                        KeyboardSettingsMenuItem(
                            icon = entry.icon,
                            label = entry.label,
                            onClick = {
                                when (val target = entry.target) {
                                    is KeyboardShellMenuTarget.Destination -> navigate(target.id)
                                    is KeyboardShellMenuTarget.Action -> target.onClick()
                                }
                            },
                            badge = entry.badge
                        )
                    }
                KeyboardSettingsMenuScreen(
                    header = config.header,
                    aiItems = items(KeyboardSettingsGroup.AI),
                    primaryItems = items(KeyboardSettingsGroup.PRIMARY),
                    storeItems = items(KeyboardSettingsGroup.STORE),
                    supportItems = items(KeyboardSettingsGroup.SUPPORT)
                )
                config.settingsOverlay()
            }
            else -> destinationsById[currentDestinationId]?.content(
                KeyboardDestinationScope(
                    onBack = ::goBack,
                    navigate = ::navigate,
                    finish = onFinish,
                    openedFromKeyboard = openedFromKeyboard &&
                        currentDestinationId == keyboardEntryDestinationId
                )
            )
        }
        overlay()
    }
}
