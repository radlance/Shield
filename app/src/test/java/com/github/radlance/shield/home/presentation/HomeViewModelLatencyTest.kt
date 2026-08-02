package com.github.radlance.shield.home.presentation

import com.github.radlance.shield.subscription.domain.Subscription
import com.github.radlance.shield.subscription.domain.SubscriptionGroup
import com.github.radlance.shield.subscription.domain.SubscriptionRepository
import com.github.radlance.shield.subscription.domain.SubscriptionSource
import com.github.radlance.shield.subscription.domain.VlessProfile
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelLatencyTest {
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
    fun limitsConcurrentChecksAndPublishesResults() = runTest {
        val profiles = (1..40).map(::profile)
        val repository = FakeSubscriptionRepository(profiles)
        val gate = CompletableDeferred<Unit>()
        val tester = TrackingLatencyTester(gate)
        val viewModel = HomeViewModel(repository, FakeVpnController(), tester)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        runCurrent()

        viewModel.pingSubscription(SUBSCRIPTION_ID)
        runCurrent()
        viewModel.pingSubscription(SUBSCRIPTION_ID)
        runCurrent()

        assertEquals(8, tester.started)
        assertEquals(8, tester.maximumActive)
        assertTrue(viewModel.uiState.value.serverLatencies.values.all {
            it == ServerLatency.Pinging
        })
        assertTrue(SUBSCRIPTION_ID in viewModel.uiState.value.pingingSubscriptionIds)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(40, tester.started)
        assertTrue(viewModel.uiState.value.serverLatencies.values.all {
            it == ServerLatency.Available(25)
        })
        assertFalse(SUBSCRIPTION_ID in viewModel.uiState.value.pingingSubscriptionIds)
    }

    @Test
    fun deletingSubscriptionCancelsChecksAndClearsStates() = runTest {
        val profiles = listOf(profile(1), profile(2))
        val repository = FakeSubscriptionRepository(profiles)
        val gate = CompletableDeferred<Unit>()
        val tester = TrackingLatencyTester(gate)
        val viewModel = HomeViewModel(repository, FakeVpnController(), tester)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        runCurrent()

        viewModel.pingSubscription(SUBSCRIPTION_ID)
        runCurrent()
        viewModel.delete(SUBSCRIPTION_ID)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.serverLatencies.isEmpty())
        assertFalse(SUBSCRIPTION_ID in viewModel.uiState.value.pingingSubscriptionIds)
        assertEquals(0, tester.active)
    }

    @Test
    fun mapsMissingAndFailedChecksToUnavailable() = runTest {
        val profiles = listOf(profile(1), profile(2), profile(3))
        val repository = FakeSubscriptionRepository(profiles)
        val tester = ServerLatencyTester { current ->
            when (current.id) {
                "profile-1" -> 42
                "profile-2" -> null
                else -> error("Connection failed")
            }
        }
        val viewModel = HomeViewModel(repository, FakeVpnController(), tester)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        runCurrent()

        viewModel.pingSubscription(SUBSCRIPTION_ID)
        advanceUntilIdle()

        assertEquals(
            ServerLatency.Available(42),
            viewModel.uiState.value.serverLatencies["profile-1"]
        )
        assertEquals(
            ServerLatency.Unavailable,
            viewModel.uiState.value.serverLatencies["profile-2"]
        )
        assertEquals(
            ServerLatency.Unavailable,
            viewModel.uiState.value.serverLatencies["profile-3"]
        )
    }

    private fun profile(index: Int) = VlessProfile(
        id = "profile-$index",
        subscriptionId = SUBSCRIPTION_ID,
        name = "Server $index",
        server = "192.0.2.$index",
        port = 443,
        uuid = "00000000-0000-0000-0000-${index.toString().padStart(12, '0')}"
    )

    private class TrackingLatencyTester(
        private val gate: CompletableDeferred<Unit>
    ) : ServerLatencyTester {
        var started = 0
            private set
        var active = 0
            private set
        var maximumActive = 0
            private set

        override suspend fun measure(profile: VlessProfile): Long {
            started++
            active++
            maximumActive = maxOf(maximumActive, active)
            return try {
                gate.await()
                25
            } finally {
                active--
            }
        }
    }

    private class FakeSubscriptionRepository(
        profiles: List<VlessProfile>
    ) : SubscriptionRepository {
        override val groups = MutableStateFlow(
            listOf(
                SubscriptionGroup(
                    subscription = Subscription(
                        id = SUBSCRIPTION_ID,
                        name = "Test",
                        createdAtEpochMillis = 0
                    ),
                    profiles = profiles
                )
            )
        )
        override val selectedProfileId = MutableStateFlow<String?>(null)

        override suspend fun import(
            name: String,
            source: SubscriptionSource
        ): Result<Subscription> = error("Not used")

        override suspend fun refresh(subscriptionId: String): Result<Unit> =
            error("Not used")

        override suspend fun refreshAll(): List<Result<Unit>> = error("Not used")
        override suspend fun delete(subscriptionId: String) = Unit
        override suspend fun selectProfile(profileId: String) = Unit
        override suspend fun getProfile(profileId: String): VlessProfile? = groups.value
            .flatMap(SubscriptionGroup::profiles)
            .firstOrNull { it.id == profileId }
    }

    private class FakeVpnController : VpnController {
        override val state: StateFlow<VpnConnectionState> =
            MutableStateFlow(VpnConnectionState.Disconnected)

        override fun connect(profileId: String) = Unit
        override fun switchProfile(profileId: String) = Unit
        override fun disconnect() = Unit
        override fun reload() = Unit
    }

    private companion object {
        const val SUBSCRIPTION_ID = "subscription"
    }
}
