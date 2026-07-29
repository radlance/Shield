package com.github.radlance.shield.home.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import com.github.radlance.shield.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddMenu(
    scrollState: ScrollState,
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

    val focusRequester = remember { FocusRequester() }
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }

    BackHandler(fabMenuExpanded) { fabMenuExpanded = false }

    val items =
        listOf(
            Triple(Icons.Filled.Subscriptions, R.string.add_subscription, onAddSubscription),
            Triple(Icons.Filled.ContentPaste, R.string.paste_from_clipboard, onPasteFromClipboard),
            Triple(Icons.Filled.QrCode, R.string.qr_code, onQrCode),
            Triple(Icons.Filled.Edit, R.string.manual_input, onManualInput),
        )

    FloatingActionButtonMenu(
        modifier = modifier,
        expanded = fabMenuExpanded,
        button = {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    if (fabMenuExpanded) {
                        TooltipAnchorPosition.Start
                    } else TooltipAnchorPosition.Above
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
                            visible = fabVisible || fabMenuExpanded,
                            alignment = Alignment.BottomEnd,
                        )
                        .focusRequester(focusRequester),
                    checked = fabMenuExpanded,
                    onCheckedChange = {
                        fabMenuExpanded = !fabMenuExpanded
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
                        contentDescription = null,
                        modifier = Modifier.animateIcon({ checkedProgress }),
                    )
                }
            }
        }
    ) {
        items.forEach { item ->
            FloatingActionButtonMenuItem(
                onClick = {
                    fabMenuExpanded = false
                    item.third()
                },
                icon = { Icon(item.first, contentDescription = null) },
                text = { Text(text = stringResource(item.second)) }
            )
        }
    }
}
