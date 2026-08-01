package com.github.radlance.shield.home.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import com.github.radlance.shield.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddMenu(
    scrollState: ScrollState,
    expanded: Boolean,
    canExpand: Boolean = true,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onAddSubscription: () -> Unit = {},
    onPasteFromClipboard: () -> Unit = {},
    onQrCode: () -> Unit = {},
    onManualInput: () -> Unit = {}
) {
    var isScrollingUp by remember { mutableStateOf(true) }

    LaunchedEffect(scrollState) {
        var previousValue = scrollState.value

        snapshotFlow { scrollState.value }.collect { currentValue ->
            if (currentValue < previousValue) {
                isScrollingUp = true
            } else if (currentValue > previousValue) {
                isScrollingUp = false
            }
            previousValue = currentValue
        }
    }

    val fabVisible by remember {
        derivedStateOf {
            scrollState.value == 0 || !scrollState.canScrollForward || isScrollingUp
        }
    }

    BackHandler(expanded) { onExpandedChange(false) }

    val items =
        listOf(
            Triple(Icons.Filled.Subscriptions, R.string.add_subscription, onAddSubscription),
            Triple(Icons.Filled.ContentPaste, R.string.paste_from_clipboard, onPasteFromClipboard),
            Triple(Icons.Filled.QrCode, R.string.qr_code, onQrCode),
            Triple(Icons.Filled.Edit, R.string.manual_input, onManualInput),
        )

    FloatingActionButtonMenu(
        modifier = modifier,
        expanded = expanded,
        button = {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    if (expanded) TooltipAnchorPosition.Start else TooltipAnchorPosition.Above
                ),
                tooltip = {
                    PlainTooltip {
                        Text(text = "Add menu")
                    }
                },
                state = rememberTooltipState()
            ) {
                ToggleFloatingActionButton(
                    modifier = Modifier
                        .animateFloatingActionButton(
                            visible = fabVisible || expanded,
                            alignment = Alignment.BottomEnd,
                        )
                        .testTag(COLLAPSED_FAB_TAG),
                    checked = expanded,
                    onCheckedChange = { checked ->
                        if (canExpand || !checked) onExpandedChange(checked)
                    }
                ) {
                    val imageVector by remember {
                        derivedStateOf {
                            if (checkedProgress > 0.5f) {
                                Icons.Filled.Close
                            } else Icons.Filled.Add
                        }
                    }
                    Icon(
                        painter = rememberVectorPainter(imageVector),
                        contentDescription = stringResource(
                            if (expanded) R.string.close_add_menu else R.string.open_add_menu
                        ),
                        modifier = Modifier.animateIcon({ checkedProgress }),
                    )
                }
            }
        }
    ) {
        items.forEach { item ->
            FloatingActionButtonMenuItem(
                onClick = {
                    onExpandedChange(false)
                    item.third()
                },
                icon = { Icon(item.first, contentDescription = null) },
                text = { Text(text = stringResource(item.second)) }
            )
        }
    }
}

@Composable
internal fun FabMenuScrim(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        }
                    }
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClickLabel = dismissLabel,
                    role = Role.Button,
                    onClick = onDismiss
                )
                .hideFromAccessibilityIf(dismissLabel == null)
                .testTag(SCRIM_TAG)
        )
    }
}

internal fun Modifier.hideFromAccessibilityIf(hidden: Boolean): Modifier =
    if (hidden) semantics { hideFromAccessibility() } else this

private const val SCRIM_ALPHA = 0.32f
internal const val COLLAPSED_FAB_TAG = "add_menu_fab"
internal const val SCRIM_TAG = "add_menu_scrim"
