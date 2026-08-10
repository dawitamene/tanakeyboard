// ui/AppBrandHeader.kt
package com.addiyon.keyboard.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.addiyon.keyboard.AiAccountActivity
import com.addiyon.keyboard.R
import com.addiyon.keyboard.ui.i18n.LanguageToggle
import com.addiyon.keyboard.ui.i18n.LocalAppStrings
import com.addiyon.keyboard.ui.settings.KeyboardPrefs
import com.addiyon.keyboard.ui.theme.PoppinsFamily

/**
 * The app's brand header: logo + "Addiyon Keyboard" wordmark on the left, the
 * in-place language switcher on the right. Shared by the Settings home and the
 * onboarding step screens so the three stay visually identical -- change the
 * title size/weight or switcher styling HERE and every screen follows.
 *
 */
@Composable
fun AppBrandHeader(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val isLoggedIn = !KeyboardPrefs.aiJwt(context).isNullOrBlank()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(64.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_addiyon_icon),
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Addiyon Keyboard",
            style = MaterialTheme.typography.titleMedium,
            fontFamily = PoppinsFamily,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        LanguageToggle(compact = true)
        if (isLoggedIn) {
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    context.startActivity(
                        Intent(context, AiAccountActivity::class.java).apply {
                            putExtra(AiAccountActivity.EXTRA_MODE, AiAccountActivity.MODE_DASHBOARD)
                        }
                    )
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = strings.aiAccountTitle,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPageTopBar(
    title: String,
    onBack: () -> Unit,
    backContentDescription: String,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backContentDescription
                )
            }
        },
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}
