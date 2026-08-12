package com.github.radlance.shield.home.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import com.github.radlance.shield.uikit.theme.core.ThemeConfiguration
import com.github.radlance.shield.uikit.theme.ui.ShieldTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ServerListReorderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun draggingPinnedHandleReordersPinnedGroupsAndKeepsExpandedContent() {
        var reorderedIds: List<String>? = null
        var reorderCount = 0
        composeRule.setContent {
            ShieldTheme(themeConfiguration = ThemeConfiguration()) {
                ServerList(
                    groups = listOf(
                        group("first", pinned = true, serverName = "First server"),
                        group("second", pinned = true, serverName = "Second server"),
                        group("unpinned", pinned = false, serverName = "Unpinned server")
                    ),
                    onPinnedOrderChanged = {
                        reorderedIds = it
                        reorderCount++
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        composeRule.onNodeWithTag(
            "pinned_drag_handle_first",
            useUnmergedTree = true
        ).assertExists()
        composeRule.onNodeWithTag(
            "pinned_drag_handle_second",
            useUnmergedTree = true
        ).assertExists()
        composeRule.onNodeWithTag(
            "pinned_drag_handle_unpinned",
            useUnmergedTree = true
        ).assertDoesNotExist()

        composeRule.onNodeWithTag(
            "pinned_drag_handle_first",
            useUnmergedTree = true
        ).performClick()
        composeRule.onNodeWithText("First server").assertExists()

        composeRule.onNodeWithText("First server").assertExists()
        composeRule.onNodeWithText("Second server").assertExists()
        composeRule.onNodeWithText("second").performClick()
        composeRule.onNodeWithText("Second server").assertDoesNotExist()
        val firstHandle = composeRule.onNodeWithTag(
            "pinned_drag_handle_first",
            useUnmergedTree = true
        )
        val firstCenter = firstHandle
            .fetchSemanticsNode().boundsInRoot.center
        val secondCenter = composeRule.onNodeWithTag(
            "pinned_drag_handle_second",
            useUnmergedTree = true
        )
            .fetchSemanticsNode().boundsInRoot.center
        val dragDistance = secondCenter.y - firstCenter.y + 40f
        firstHandle.performTouchInput {
            down(center)
            repeat(12) {
                moveBy(Offset(0f, dragDistance / 12f), delayMillis = 24)
            }
        }

        firstHandle.performTouchInput { up() }

        composeRule.runOnIdle {
            assertEquals(listOf("second", "first"), reorderedIds)
            assertEquals(1, reorderCount)
        }
        composeRule.onNodeWithText("First server").assertExists()
        composeRule.onNodeWithText("Second server").assertDoesNotExist()
    }

    private fun group(id: String, pinned: Boolean, serverName: String) = ServerGroup(
        id = id,
        title = id,
        isPinned = pinned,
        items = listOf(
            ServerItem(
                id = "$id-server",
                leadingIcon = "V",
                title = serverName,
                description = null
            )
        )
    )
}
