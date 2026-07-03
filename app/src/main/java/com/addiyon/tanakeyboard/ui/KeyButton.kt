package com.addiyon.tanakeyboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun KeyButton(
    primaryText: String,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
    onClick: () -> Unit
) {

    Card(
        modifier = modifier
            .height(48.dp)
            .padding(horizontal = 2.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = primaryText,
                fontSize = 18.sp
            )

            secondaryText?.let {
                Text(
                    text = it,
                    fontSize = 10.sp
                )
            }
        }
    }
}