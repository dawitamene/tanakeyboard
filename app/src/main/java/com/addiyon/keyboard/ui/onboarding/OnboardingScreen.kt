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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.addiyon.keyboard.ui.settings.KeyboardPrefs

private val GreenCheck = Color(0xFF2E7D32)

/** The distinct full-screen pages of the first-run flow. */
private enum class Phase { Activate, Enable, AllSet, Tour }

/**
 * First-run walkthrough as full-screen pages: Activate -> Enable -> All set,
 * then (first time only) a short feature tour.
 * Each setup page is centered with a single centered call-to-action; the flow
 * auto-advances as the live [KeyboardStatusSnapshot] changes (enabling the
 * IME advances to Enable, making it default advances to All set), so the
 * user never taps a "next" button -- completing the system step IS the next.
 * After the brief All-set confirmation, a swipe-free tour of the keyboard's
 * features runs once (Next/Skip driven, recorded via
 * [KeyboardPrefs.featureTourSeen]); afterwards -- or immediately, for users
 * who already saw it -- the flow calls [onDone].
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
            // The Tour guard keeps a mid-tour status refresh (window focus
            // regain re-reads it) from yanking the user back to All-set.
            status.isDefault && phase != Phase.Tour -> phase = Phase.AllSet
            status.enabled && phase == Phase.Activate -> phase = Phase.Enable
        }
    }

    // Brief confirmation, then the one-time feature tour (or straight on to
    // the next screen if it's already been seen).
    LaunchedEffect(phase) {
        if (phase == Phase.AllSet) {
            kotlinx.coroutines.delay(1600)
            if (KeyboardPrefs.featureTourSeen(context)) onDone()
            else phase = Phase.Tour
        }
    }

    fun finishTour() {
        KeyboardPrefs.setFeatureTourSeen(context)
        onDone()
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

                Phase.Tour -> TourPager(
                    onSkip = ::finishTour,
                    onFinished = ::finishTour
                )
            }
        }
    }
}

/** One page of the feature tour: what to show and how. */
private data class TourPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    /** Optional highlighted demo line (e.g. "selam → ሰላም"). */
    val example: String? = null
)

/**
 * The one-time feature tour: a few Next/Skip-driven pages showcasing what the
 * keyboard can do, in the same centered single-focus style as the setup steps.
 */
@Composable
private fun TourPager(onSkip: () -> Unit, onFinished: () -> Unit) {
    val strings = LocalAppStrings.current
    val pages = listOf(
        TourPage(
            icon = Icons.Default.Translate,
            title = strings.tourTypingTitle,
            description = strings.tourTypingDescription,
            example = strings.tourTypingExample
        ),
        TourPage(
            icon = Icons.Default.AutoAwesome,
            title = strings.tourSuggestionsTitle,
            description = strings.tourSuggestionsDescription
        ),
        TourPage(
            icon = Icons.Default.Palette,
            title = strings.tourPersonalizeTitle,
            description = strings.tourPersonalizeDescription
        )
    )
    var pageIndex by remember { mutableStateOf(0) }
    val lastPage = pageIndex == pages.lastIndex

    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp)) {
        AnimatedContent(
            targetState = pageIndex,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it } + fadeOut())
            },
            label = "tour-page",
            modifier = Modifier.align(Alignment.Center)
        ) { index ->
            val page = pages[index]
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                StepIcon(icon = page.icon)
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                if (page.example != null) {
                    Text(
                        text = page.example,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    text = page.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StepDots(count = pages.size, active = pageIndex)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onSkip) {
                    Text(LocalAppStrings.current.tourSkip)
                }
                Button(
                    onClick = { if (lastPage) onFinished() else pageIndex++ }
                ) {
                    Text(
                        if (lastPage) LocalAppStrings.current.tourStart
                        else LocalAppStrings.current.tourNext
                    )
                }
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
            StepIcon(icon = icon)
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

/**
 * The circular hero glyph at the top of each setup step and tour page: a soft
 * neutral (Sand) disc with a muted stone icon. Deliberately NOT the brand
 * vermillion -- the single filled call-to-action button is meant to be the one
 * accent on the screen, so the icon stays calm instead of competing with it.
 */
@Composable
private fun StepIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp)
        )
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
