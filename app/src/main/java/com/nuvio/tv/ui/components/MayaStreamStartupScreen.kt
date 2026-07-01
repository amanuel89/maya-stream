package com.nuvio.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
fun MayaStreamStartupScreen(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null
) {
    val displayTitle = title ?: stringResource(R.string.maya_startup_title)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = NuvioTheme.spacing.xxl)
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(NuvioTheme.spacing.xxxl)
                    .graphicsLayer { clip = false },
                color = NuvioTheme.colors.Primary,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.xl))
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleLarge,
                color = NuvioTheme.colors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = NuvioTheme.colors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
