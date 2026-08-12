package com.github.radlance.shield.home.presentation

import com.github.radlance.shield.subscription.domain.ProxyProfile
import com.github.radlance.shield.subscription.domain.Subscription
import com.github.radlance.shield.subscription.domain.SubscriptionGroup
import com.github.radlance.shield.subscription.domain.SubscriptionRepository
import com.github.radlance.shield.subscription.domain.SubscriptionSource
import com.github.radlance.shield.vpn.domain.ServerLatencyTester
import com.github.radlance.shield.vpn.domain.VpnConnectionState
import com.github.radlance.shield.vpn.domain.VpnController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelPinnedOrderTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun publishesOptimisticOrderUntilRepositoryPersistsIt() = runTest {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        runCurrent()

        viewModel.reorderPinned(listOf("second", "first"))
        runCurrent()

        assertEquals(listOf("second", "first", "unpinned"), viewModel.groupIds())
        assertEquals(listOf("first", "second", "unpinned"), repository.groups.value.map {
            it.subscription.id
        })

        repository.gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("second", "first", "unpinned"), viewModel.groupIds())
        assertEquals(listOf("second", "first"), repository.lastRequestedOrder)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun restoresRepositoryOrderAndReportsFailure() = runTest {
        val repository = FakeRepository(failure = IllegalStateException("Write failed"))
        val viewModel = viewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        runCurrent()

        viewModel.reorderPinned(listOf("second", "first"))
        runCurrent()
        assertEquals(listOf("second", "first", "unpinned"), viewModel.groupIds())

        repository.gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("first", "second", "unpinned"), viewModel.groupIds())
        assertEquals("Write failed", viewModel.uiState.value.message)
    }

    private fun viewModel(repository: SubscriptionRepository) = HomeViewModel(
        repository = repository,
        vpnController = FakeVpnController(),
        latencyTester = ServerLatencyTester { null }
    )

    private fun HomeViewModel.groupIds() = uiState.value.groups.map { it.subscription.id }

    private class FakeRepository(
        private val failure: Exception? = null
    ) : SubscriptionRepository {
        val gate = CompletableDeferred<Unit>()
        var lastRequestedOrder: List<String>? = null
            private set
        override val groups = MutableStateFlow(
            listOf(
                group("first", 0),
                group("second", 1),
                group("unpinned", null)
            )
        )
        override val selectedProfileId = MutableStateFlow<String?>(null)

        override suspend fun import(
            name: String,
            source: SubscriptionSource
        ): Result<Subscription> = error("Not used")

        override suspend fun refresh(subscriptionId: String): Result<Unit> = error("Not used")
        override suspend fun refreshAll(): List<Result<Unit>> = error("Not used")
        override suspend fun delete(subscriptionId: String) = Unit
        override suspend fun setPinned(subscriptionId: String, pinned: Boolean) = Unit

        override suspend fun reorderPinned(subscriptionIds: List<String>) {
            lastRequestedOrder = subscriptionIds
            gate.await()
            failure?.let { throw it }
            val byId = groups.value.associateBy { it.subscription.id }
            groups.value = subscriptionIds.mapIndexed { index, id ->
                byId.getValue(id).copy(
                    subscription = byId.getValue(id).subscription.copy(pinOrder = index.toLong())
                )
            } + byId.getValue("unpinned")
        }

        override suspend fun selectProfile(profileId: String) = Unit
        override suspend fun getProfile(profileId: String): ProxyProfile? = null

        private companion object {
            fun group(id: String, pinOrder: Long?) = SubscriptionGroup(
                subscription = Subscription(
                    id = id,
                    name = id,
                    createdAtEpochMillis = 0,
                    pinOrder = pinOrder
                ),
                profiles = emptyList()
            )
        }
    }

    private class FakeVpnController : VpnController {
        override val state: StateFlow<VpnConnectionState> =
            MutableStateFlow(VpnConnectionState.Disconnected)

        override fun connect(profileId: String) = Unit
        override fun switchProfile(profileId: String) = Unit
        override fun disconnect() = Unit
        override fun reload() = Unit
    }
}
