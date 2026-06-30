@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.RemoteCatalogEntry
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
fun RemoteCatalogSection(
    title: String,
    subtitle: String,
    entries: List<RemoteCatalogEntry>,
    installedUrls: Set<String>,
    isLoading: Boolean,
    installingEntryId: String?,
    onInstall: (RemoteCatalogEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = NuvioTheme.colors.TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextSecondary
            )
        }

        when {
            isLoading -> {
                Text(
                    text = "Loading catalog…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuvioTheme.colors.TextTertiary,
                    modifier = Modifier.padding(vertical = NuvioTheme.spacing.md)
                )
            }
            entries.isEmpty() -> {
                Text(
                    text = "No catalog entries available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuvioTheme.colors.TextTertiary,
                    modifier = Modifier.padding(vertical = NuvioTheme.spacing.md)
                )
            }
            else -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
                    contentPadding = PaddingValues(end = NuvioTheme.spacing.xl)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        val isInstalled = installedUrls.any { it.equals(entry.url, ignoreCase = true) }
                        val isInstalling = installingEntryId == entry.id
                        RemoteCatalogCard(
                            entry = entry,
                            isInstalled = isInstalled,
                            isInstalling = isInstalling,
                            onInstall = { onInstall(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteCatalogCard(
    entry: RemoteCatalogEntry,
    isInstalled: Boolean,
    isInstalling: Boolean,
    onInstall: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "catalogCardScale")
    val borderColor by animateColorAsState(
        if (focused) NuvioTheme.colors.Secondary else NuvioTheme.colors.Divider,
        label = "catalogCardBorder"
    )

    Surface(
        onClick = {
            if (!isInstalled && !isInstalling) onInstall()
        },
        enabled = !isInstalled && !isInstalling,
        modifier = Modifier
            .height(148.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, borderColor),
                shape = RoundedCornerShape(4.dp)
            )
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.BackgroundElevated
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NuvioTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleMedium,
                color = NuvioTheme.colors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.description.orEmpty().ifBlank { entry.url },
                style = MaterialTheme.typography.bodySmall,
                color = NuvioTheme.colors.TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when {
                    isInstalling -> "Installing…"
                    isInstalled -> "Installed"
                    else -> "Install"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (isInstalled) NuvioTheme.colors.TextTertiary else NuvioTheme.colors.Secondary
            )
        }
    }
}
