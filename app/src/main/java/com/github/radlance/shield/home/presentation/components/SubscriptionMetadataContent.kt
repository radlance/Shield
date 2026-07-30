package com.github.radlance.shield.home.presentation.components

import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.github.radlance.shield.subscription.domain.SubscriptionAccessStatus
import com.github.radlance.shield.subscription.domain.SubscriptionMetadata
import com.github.radlance.shield.uikit.tokens.icons
import com.github.radlance.shield.uikit.tokens.spacing
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SubscriptionMetadataContent(
    metadata: SubscriptionMetadata,
    accessStatus: SubscriptionAccessStatus
) {
    val hasTraffic = metadata.uploadBytes != null ||
        metadata.downloadBytes != null ||
        metadata.totalBytes != null
    val hasActions = metadata.webPageUrl != null || metadata.supportUrl != null

    Surface(
        shape = shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s),
            modifier = Modifier.padding(MaterialTheme.spacing.sm)
        ) {
            if (hasTraffic || hasActions) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s)
                ) {
                    if (hasTraffic) {
                        TrafficProgress(
                            metadata = metadata,
                            accessStatus = accessStatus,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    ProviderActions(
                        webPageUrl = metadata.webPageUrl,
                        supportUrl = metadata.supportUrl
                    )
                }
            }

            metadata.announcement?.let { announcement ->
                if (hasTraffic || hasActions) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Announcement(
                    text = announcement,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TrafficProgress(
    metadata: SubscriptionMetadata,
    accessStatus: SubscriptionAccessStatus,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val usedBytes = metadata.usedBytes ?: 0L
    val totalBytes = metadata.totalBytes?.takeIf { it > 0 }
    val progress = totalBytes
        ?.let { (usedBytes.toDouble() / it).coerceIn(0.0, 1.0).toFloat() }
        ?: 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "SubscriptionTrafficProgress"
    )
    val indicatorColor = when {
        accessStatus == SubscriptionAccessStatus.TRAFFIC_EXHAUSTED ->
            MaterialTheme.colorScheme.error
        progress >= 0.8f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s),
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Rounded.DataUsage,
            contentDescription = null,
            tint = indicatorColor,
            modifier = Modifier.size(MaterialTheme.icons.medium)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = buildString {
                        append(Formatter.formatFileSize(context, usedBytes))
                        append(" / ")
                        append(
                            totalBytes?.let { Formatter.formatFileSize(context, it) } ?: "∞"
                        )
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = indicatorColor
                )
            }

            LinearWavyProgressIndicator(
                progress = { animatedProgress },
                color = indicatorColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ProviderActions(
    webPageUrl: String?,
    supportUrl: String?
) {
    val uriHandler = LocalUriHandler.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        webPageUrl?.let { url ->
            FilledTonalIconButton(
                onClick = { runCatching { uriHandler.openUri(url) } }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = "Website"
                )
            }
        }

        supportUrl?.let { url ->
            FilledTonalIconButton(
                onClick = { runCatching { uriHandler.openUri(url) } }
            ) {
                Icon(
                    imageVector = Icons.Rounded.SupportAgent,
                    contentDescription = "Support"
                )
            }
        }
    }
}

@Composable
private fun Announcement(
    text: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember(text) { mutableStateOf(false) }
    var hasOverflow by remember(text) { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "AnnouncementChevronRotation"
    )

    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s),
        modifier = modifier
            .clip(shapes.large)
            .clickable(
                enabled = hasOverflow || expanded,
                onClick = { expanded = !expanded }
            )
            .padding(
                horizontal = MaterialTheme.spacing.s,
                vertical = MaterialTheme.spacing.xs
            )
    ) {
        Icon(
            imageVector = Icons.Rounded.Campaign,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(MaterialTheme.icons.medium)
        )

        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                (
                    expandVertically(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        ),
                        expandFrom = Alignment.Top
                    )
                    ) togetherWith (
                    shrinkVertically(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        ),
                        shrinkTowards = Alignment.Top
                    )
                    )
            },
            label = "AnnouncementExpansion",
            modifier = Modifier.weight(1f)
        ) { isExpanded ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (isExpanded) Int.MAX_VALUE else ANNOUNCEMENT_LINES,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = {
                    if (!isExpanded) hasOverflow = it.hasVisualOverflow
                }
            )
        }

        if (hasOverflow || expanded) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(MaterialTheme.icons.large)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = rotationAngle }
                )
            }
        }
    }
}

internal fun SubscriptionMetadata.hasVisibleData(): Boolean =
    uploadBytes != null ||
        downloadBytes != null ||
        totalBytes != null ||
        announcement != null ||
        supportUrl != null ||
        webPageUrl != null

internal fun SubscriptionMetadata.needsAttention(): Boolean {
    val used = usedBytes ?: return false
    val total = totalBytes?.takeIf { it > 0 } ?: return false
    return used.toDouble() / total >= 0.8
}

internal fun subscriptionSummary(metadata: SubscriptionMetadata): String? {
    val expiry = metadata.expiresAtEpochSeconds?.let(::formatEpochSeconds)
    val updateInterval = metadata.updateIntervalHours?.let { "Every $it h" }
    return listOfNotNull(expiry, updateInterval)
        .joinToString(" | ")
        .takeIf(String::isNotBlank)
}

private fun formatEpochSeconds(epochSeconds: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM)
        .format(Date(epochSeconds.coerceAtMost(Long.MAX_VALUE / 1_000) * 1_000))

private const val ANNOUNCEMENT_LINES = 3
