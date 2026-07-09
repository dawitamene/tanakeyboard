// ui/AppBrandHeader.kt
package com.addiyon.keyboard.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.addiyon.keyboard.R
import com.addiyon.keyboard.ui.i18n.LanguageToggle
import com.addiyon.keyboard.ui.theme.PoppinsFamily

/**
 * The app's brand header: logo + "Addiyon Keyboard" wordmark on the left, the
 * in-place language switcher on the right. Shared by the Settings home and the
 * onboarding step screens so the three stay visually identical -- change the
 * title size/weight or switcher styling HERE and every screen follows.
 *
 * Callers own the surrounding padding via [modifier] (the Settings header sits
 * inside a horizontally-padded Column and adds only vertical padding; the
 * onboarding header pads all sides itself).
 */
@Composable
fun AppBrandHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
    }
}
