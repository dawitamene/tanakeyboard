package com.addiyon.keyboard.features.appshell

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.addiyon.keyboard.ui.design.AddiyonButton
import com.addiyon.keyboard.ui.design.AddiyonOutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.addiyon.keyboard.ui.design.AddiyonContentSection
import com.addiyon.keyboard.ui.design.AddiyonInputField
import com.addiyon.keyboard.ui.design.AddiyonMotion
import com.addiyon.keyboard.ui.design.AddiyonSizes
import com.addiyon.keyboard.ui.design.AddiyonSpacing
import com.addiyon.keyboard.ui.design.addiyonColors
import com.addiyon.keyboard.ui.theme.AddiyonBrandTheme

data class KeyboardAppStatus(val enabled: Boolean, val isDefault: Boolean)

data class KeyboardOnboardingCopy(
    val activateTitle: String,
    val activateDescription: String,
    val openSettings: String,
    val activateFootnote: String,
    val enableTitle: String,
    val enableDescription: String,
    val switchKeyboard: String,
    val stepFormat: String,
    val allSet: String,
    val allSetSubtitle: String,
    val tourSkip: String = "",
    val tourNext: String = "",
    val tourStart: String = ""
)

data class KeyboardTourPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val example: String? = null
)

data class KeyboardHomeCopy(
    val activeStatus: String,
    val enabledStatus: String,
    val disabledStatus: String,
    val enableAction: String,
    val switchAction: String,
    val settingsAction: String,
    val tryItOut: String,
    val tryItOutPlaceholder: String
)

private enum class SetupPhase { Activate, Enable, AllSet, Tour }

abstract class KeyboardSetupActivity : KeyboardAppShellActivity() {
    @Composable
    protected open fun keyboardAppShellCustomization() = KeyboardAppShellCustomization()

    @Composable
    final override fun keyboardAppShellConfig(): KeyboardAppShellConfig =
        packKeyboardAppShellConfig(keyboardProduct, keyboardAppShellCustomization())
}

@Composable
fun KeyboardOnboardingScreen(
    status: KeyboardAppStatus,
    copy: KeyboardOnboardingCopy,
    header: @Composable () -> Unit,
    onOpenSettings: () -> Unit,
    onShowPicker: () -> Unit,
    onDone: () -> Unit,
    tourPages: List<KeyboardTourPage> = emptyList(),
    isTourSeen: () -> Boolean = { true },
    markTourSeen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var phase by remember {
        mutableStateOf(
            when {
                status.isDefault -> SetupPhase.AllSet
                status.enabled -> SetupPhase.Enable
                else -> SetupPhase.Activate
            }
        )
    }
    LaunchedEffect(status) {
        when {
            status.isDefault && phase != SetupPhase.Tour -> phase = SetupPhase.AllSet
            status.enabled && phase == SetupPhase.Activate -> phase = SetupPhase.Enable
        }
    }
    LaunchedEffect(phase) {
        if (phase == SetupPhase.AllSet) {
            kotlinx.coroutines.delay(1600)
            if (tourPages.isEmpty() || isTourSeen()) onDone() else phase = SetupPhase.Tour
        }
    }
    fun finishTour() {
        markTourSeen()
        onDone()
    }
    Box(modifier = modifier.fillMaxSize()) {
        header()
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it } + fadeOut())
            },
            label = "keyboard-onboarding-phase"
        ) { current ->
            when (current) {
                SetupPhase.Activate -> SetupStepPage(
                    icon = Icons.Default.Keyboard,
                    title = copy.activateTitle,
                    description = copy.activateDescription,
                    buttonLabel = copy.openSettings,
                    footnote = copy.activateFootnote,
                    stepLabel = copy.stepFormat.format(1),
                    stepIndex = 0,
                    onClick = onOpenSettings
                )
                SetupPhase.Enable -> SetupStepPage(
                    icon = Icons.Default.SwapHoriz,
                    title = copy.enableTitle,
                    description = copy.enableDescription,
                    buttonLabel = copy.switchKeyboard,
                    footnote = null,
                    stepLabel = copy.stepFormat.format(2),
                    stepIndex = 1,
                    onClick = onShowPicker
                )
                SetupPhase.AllSet -> AllSetPage(copy.allSet, copy.allSetSubtitle)
                SetupPhase.Tour -> TourPager(
                    pages = tourPages,
                    copy = copy,
                    onSkip = ::finishTour,
                    onFinished = ::finishTour
                )
            }
        }
    }
}

@Composable
fun KeyboardProductHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailingContent: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(AddiyonSizes.appHeader)
            .padding(horizontal = AddiyonSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_addiyon_app_shell),
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        trailingContent()
    }
}

@Composable
fun KeyboardProductHomeScreen(
    productName: String,
    status: KeyboardAppStatus,
    copy: KeyboardHomeCopy,
    onOpenSettings: () -> Unit,
    onShowPicker: () -> Unit,
    modifier: Modifier = Modifier,
    productContent: @Composable () -> Unit = {}
) {
    var text by remember { mutableStateOf(TextFieldValue("")) }
    Column(modifier = modifier.fillMaxSize()) {
        KeyboardProductHeader(title = productName)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(AddiyonSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.md)
        ) {
            AddiyonContentSection(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AddiyonSpacing.md)) {
                    Text(
                        text = when {
                            status.isDefault -> copy.activeStatus
                            status.enabled -> copy.enabledStatus
                            else -> copy.disabledStatus
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (!status.enabled) {
                        AddiyonOutlinedButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = AddiyonSpacing.xs)
                        ) { Text(copy.enableAction) }
                    } else if (!status.isDefault) {
                        AddiyonOutlinedButton(
                            onClick = onShowPicker,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = AddiyonSpacing.xs)
                        ) { Text(copy.switchAction) }
                    }
                }
            }
            AddiyonButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(copy.settingsAction)
            }
            productContent()
            Text(copy.tryItOut, style = MaterialTheme.typography.titleMedium)
            AddiyonInputField(
                value = text,
                onValueChange = { text = it },
                placeholder = copy.tryItOutPlaceholder,
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )
        }
    }
}

@Composable
private fun TourPager(
    pages: List<KeyboardTourPage>,
    copy: KeyboardOnboardingCopy,
    onSkip: () -> Unit,
    onFinished: () -> Unit
) {
    var pageIndex by remember { mutableStateOf(0) }
    val lastPage = pageIndex == pages.lastIndex
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AddiyonSpacing.xxl)
    ) {
        AnimatedContent(
            targetState = pageIndex,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it } + fadeOut())
            },
            label = "keyboard-tour-page",
            modifier = Modifier.align(Alignment.Center)
        ) { index ->
            val page = pages[index]
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.lg)
            ) {
                SetupStepIcon(page.icon)
                Text(page.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                if (page.example != null) {
                    Text(
                        page.example,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    page.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = AddiyonSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.md)
        ) {
            TourDots(count = pages.size, active = pageIndex)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onSkip) { Text(copy.tourSkip) }
                FilledTonalButton(
                    onClick = { if (lastPage) onFinished() else pageIndex++ }
                ) { Text(if (lastPage) copy.tourStart else copy.tourNext) }
            }
        }
    }
}

@Composable
private fun SetupStepPage(
    icon: ImageVector,
    title: String,
    description: String,
    buttonLabel: String,
    footnote: String?,
    stepLabel: String,
    stepIndex: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AddiyonSpacing.xxl)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.lg)
        ) {
            SetupStepIcon(icon)
            Text(
                stepLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(AddiyonSpacing.xs))
            AddiyonButton(onClick = onClick) { Text(buttonLabel) }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = AddiyonSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.md)
        ) {
            if (footnote != null) {
                Text(
                    footnote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            TourDots(count = 2, active = stepIndex)
        }
    }
}

@Composable
private fun SetupStepIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(AddiyonSizes.minimumTouchTarget * 2)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.addiyonColors.icon,
            modifier = Modifier.size(AddiyonSizes.iconHero)
        )
    }
}

@Composable
private fun TourDots(count: Int, active: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AddiyonSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val selected = index == active
            Box(
                modifier = Modifier
                    .height(AddiyonSpacing.xs)
                    .width(if (selected) AddiyonSpacing.xl else AddiyonSpacing.xs)
                    .background(
                        color = if (selected) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun AllSetPage(title: String, subtitle: String) {
    var shown by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "keyboard-onboarding-check-scale"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = AddiyonMotion.emphasis),
        label = "keyboard-onboarding-check-text"
    )
    LaunchedEffect(Unit) { shown = true }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.addiyonColors.success,
            modifier = Modifier
                .size(72.dp)
                .scale(scale)
        )
        Spacer(Modifier.height(AddiyonSpacing.lg))
        Column(
            modifier = Modifier.alpha(textAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = AddiyonSpacing.xxs)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
