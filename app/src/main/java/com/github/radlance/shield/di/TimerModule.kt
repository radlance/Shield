package com.github.radlance.shield.di

import com.github.radlance.shield.timer.data.BaseTimerService
import com.github.radlance.shield.timer.data.LocalTimerRepository
import com.github.radlance.shield.timer.data.TimerService
import com.github.radlance.shield.timer.domain.TimerRepository
import com.github.radlance.shield.timer.presentation.TimerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val timerModule = module {
    single(named("timerScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    single<TimerService> {
        BaseTimerService(
            coroutineScope = get(named("timerScope"))
        )
    }

    single<TimerRepository> {
        LocalTimerRepository(
            timerService = get(),
            externalScope = get(named("timerScope"))
        )
    }

    viewModelOf(::TimerViewModel)
}