package com.addiyon.tanakeyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import com.addiyon.tanakeyboard.TanaKeyboardService
import com.addiyon.tanakeyboard.layout.AmharicLayout
import com.addiyon.tanakeyboard.layout.EnglishLayout

@Composable
fun KeyboardScreen(
    service: TanaKeyboardService
) {

    val ic = service.currentInputConnection

    val isAmharic = service.isAmharic
    val isShift = service.isShiftEnabled

    val layout = if (isAmharic) {
        AmharicLayout
    } else {
        EnglishLayout
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(Color(0xFFD1D5DB))
    ) {

        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {

            val metrics = computeKeyboardMetrics(rows = layout.rows, availableWidth = maxWidth)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {

                layout.rows.forEach { row ->
                    KeyRow(
                        row = row,
                        isShift = isShift,
                        isAmharic = isAmharic,
                        metrics = metrics,
                        service = service,
                        ic = ic
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}