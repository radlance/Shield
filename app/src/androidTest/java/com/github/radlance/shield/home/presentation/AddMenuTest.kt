package com.github.radlance.shield.home.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.radlance.shield.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingScrimDismissesMenuWithoutClickingContentBehindIt() {
        var backgroundClicks = 0

        setAddMenuContent(onBackgroundClick = { backgroundClicks++ })

        composeRule.onNodeWithTag(COLLAPSED_FAB_TAG).performClick()
        composeRule.onNodeWithTag(SCRIM_TAG).assertIsDisplayed().performClick()

        composeRule.onNodeWithTag(SCRIM_TAG).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, backgroundClicks) }
    }

    @Test
    fun backPressDismissesMenu() {
        setAddMenuContent()

        composeRule.onNodeWithTag(COLLAPSED_FAB_TAG).performClick()
        composeRule.onNodeWithTag(SCRIM_TAG).assertIsDisplayed()

        Espresso.pressBack()

        composeRule.onNodeWithTag(SCRIM_TAG).assertDoesNotExist()
    }

    @Test
    fun selectingItemDismissesMenuAndInvokesActionOnce() {
        var invocations = 0
        val addSubscription = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.add_subscription)

        setAddMenuContent(onAddSubscription = { invocations++ })

        composeRule.onNodeWithTag(COLLAPSED_FAB_TAG).performClick()
        composeRule.onNodeWithText(addSubscription).performClick()

        composeRule.onNodeWithTag(SCRIM_TAG).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, invocations) }
    }

    @Test
    fun tappingFabWhenPagerIsNotSettledDoesNothing() {
        setAddMenuContent(canExpand = false)

        composeRule.onNodeWithTag(COLLAPSED_FAB_TAG).assertIsDisplayed().performClick()

        composeRule.onNodeWithTag(SCRIM_TAG).assertDoesNotExist()
    }

    @Test
    fun openingMenuHidesBackgroundFromAccessibility() {
        setAddMenuContent()

        composeRule.onNodeWithTag(COLLAPSED_FAB_TAG).performClick()

        composeRule.onNodeWithTag(BACKGROUND_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.HideFromAccessibility, Unit)
        )
        composeRule.onNodeWithContentDescription(CLOSE_MENU_DESCRIPTION).assertIsDisplayed()
    }

    @Test
    fun openMenuPreventsPagerNavigationUntilDismissed() {
        setPagerContent()

        composeRule.onNodeWithTag(COLLAPSED_FAB_TAG).performClick()
        composeRule.onNodeWithTag(SCRIM_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(PAGER_TAG).performTouchInput { swipeLeft() }

        composeRule.onNodeWithText(HOME_PAGE_TEXT).assertIsDisplayed()
        composeRule.onNodeWithTag(SCRIM_TAG).assertIsDisplayed().performClick()

        composeRule.onNodeWithTag(PAGER_TAG).performTouchInput { swipeLeft() }
        composeRule.onNodeWithText(SETTINGS_PAGE_TEXT).assertIsDisplayed()
    }

    private fun setAddMenuContent(
        onBackgroundClick: () -> Unit = {},
        onAddSubscription: () -> Unit = {},
        canExpand: Boolean = true
    ) {
        composeRule.setContent {
            var expanded by remember { mutableStateOf(false) }

            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(BACKGROUND_TAG)
                            .hideFromAccessibilityIf(expanded)
                            .clickable(onClick = onBackgroundClick)
                    )

                    FabMenuScrim(
                        visible = expanded,
                        onDismiss = { expanded = false },
                        modifier = Modifier.fillMaxSize(),
                        dismissLabel = CLOSE_MENU_DESCRIPTION
                    )

                    AddMenu(
                        scrollState = rememberScrollState(),
                        expanded = expanded,
                        canExpand = canExpand,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.align(Alignment.BottomEnd),
                        onAddSubscription = onAddSubscription
                    )
                }
            }
        }
    }

    private fun setPagerContent() {
        composeRule.setContent {
            var expanded by remember { mutableStateOf(false) }
            val pagerState = rememberPagerState(pageCount = { 2 })
            val homePageSettled by remember {
                derivedStateOf {
                    pagerState.settledPage == 0 && !pagerState.isScrollInProgress
                }
            }

            LaunchedEffect(homePageSettled) {
                if (!homePageSettled) expanded = false
            }

            MaterialTheme {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !expanded,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(PAGER_TAG)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(if (it == 0) HOME_PAGE_TEXT else SETTINGS_PAGE_TEXT)
                        if (it == 0) {
                            FabMenuScrim(
                                visible = expanded,
                                onDismiss = { expanded = false },
                                modifier = Modifier.fillMaxSize(),
                                dismissLabel = CLOSE_MENU_DESCRIPTION
                            )
                            AddMenu(
                                scrollState = rememberScrollState(),
                                expanded = expanded,
                                canExpand = homePageSettled,
                                onExpandedChange = { expanded = it },
                                modifier = Modifier.align(Alignment.BottomEnd)
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val BACKGROUND_TAG = "add_menu_background"
        const val PAGER_TAG = "main_pager"
        const val HOME_PAGE_TEXT = "Home page"
        const val SETTINGS_PAGE_TEXT = "Settings page"
        const val CLOSE_MENU_DESCRIPTION = "Close add menu"
    }
}
