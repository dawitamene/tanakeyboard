package com.addiyon.keyboard.ui.onboarding

import android.content.Context.INPUT_METHOD_SERVICE
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.addiyon.keyboard.KeyboardStatusSnapshot
import com.addiyon.keyboard.R
import com.addiyon.keyboard.ui.AppBrandHeader
import com.addiyon.keyboard.ui.i18n.LanguageToggle
import com.addiyon.keyboard.ui.i18n.LocalAppStrings

private val GreenCheck = Color(0xFF2E7D32)

/** The distinct full-screen pages of the first-run flow. */
private enum class Phase { Activate, Enable, AllSet }

/**
 * First-run walkthrough as full-screen pages: Activate -> Enable -> All set.
 * Each page is centered with a single centered call-to-action; the flow
 * auto-advances as the live [KeyboardStatusSnapshot] changes (enabling the
 * IME advances to Enable, making it default advances to All set), so the
 * user never taps a "next" button -- completing the system step IS the next.
 * After the brief All-set confirmation it calls [onDone].
 */
@Composable
fun OnboardingScreen(
    status: KeyboardStatusSnapshot,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    // Phase is derived from live status but held in state so it only ever
    // moves forward -- e.g. an OEM toggling `enabled` off mid-flow shouldn't
    // yank the user back a page.
    var phase by remember {
        mutableStateOf(
            when {
                status.isDefault -> Phase.AllSet
                status.enabled -> Phase.Enable
                else -> Phase.Activate
            }
        )
    }

    LaunchedEffect(status) {
        when {
            status.isDefault -> phase = Phase.AllSet
            status.enabled && phase == Phase.Activate -> phase = Phase.Enable
        }
    }

    // Brief confirmation, then hand off to the next screen.
    LaunchedEffect(phase) {
        if (phase == Phase.AllSet) {
            kotlinx.coroutines.delay(1600)
            onDone()
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        AppBrandHeader()

        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it } + fadeOut())
            },
            label = "onboarding-phase"
        ) { current ->
            when (current) {
                Phase.Activate -> StepPage(
                    icon = Icons.Default.Keyboard,
                    title = strings.activateTitle,
                    description = strings.activateDescription,
                    buttonLabel = strings.openSettings,
                    footnote = strings.activateFootnote,
                    stepIndex = 0,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    }
                )

                Phase.Enable -> StepPage(
                    icon = Icons.Default.SwapHoriz,
                    title = strings.enableTitle,
                    description = strings.enableDescription,
                    buttonLabel = strings.switchKeyboard,
                    footnote = null,
                    stepIndex = 1,
                    onClick = {
                        (context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                            .showInputMethodPicker()
                    }
                )

                Phase.AllSet -> AllSetPage()
            }
        }
    }
}

@Composable
private fun StepPage(
    icon: ImageVector,
    title: String,
    description: String,
    buttonLabel: String,
    footnote: String?,
    stepIndex: Int,
    onClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp)) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(72.dp)
            )
            Text(
                text = LocalAppStrings.current.stepFormat.format(stepIndex + 1),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onClick) {
                Text(buttonLabel)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (footnote != null) {
                Text(
                    text = footnote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            StepDots(count = 2, active = stepIndex)
        }
    }
}

@Composable
private fun StepDots(count: Int, active: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            val selected = i == active
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(if (selected) 24.dp else 8.dp)
                    .background(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
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
private fun AllSetPage() {
    var shown by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "check-scale"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "check-text"
    )
    LaunchedEffect(Unit) { shown = true }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = GreenCheck,
                modifier = Modifier
                    .size(72.dp)
                    .scale(scale)
            )
            Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier.alpha(textAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = LocalAppStrings.current.allSet,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = LocalAppStrings.current.allSetSubtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    }
}
