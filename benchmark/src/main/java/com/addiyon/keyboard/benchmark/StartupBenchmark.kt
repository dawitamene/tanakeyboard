package com.addiyon.keyboard.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartupWithProfile() = measureStartup(
        StartupMode.COLD,
        CompilationMode.Partial(BaselineProfileMode.Require)
    )

    @Test
    fun coldStartupWithoutProfile() = measureStartup(
        StartupMode.COLD,
        CompilationMode.None()
    )

    @Test
    fun warmStartup() = measureStartup(StartupMode.WARM, CompilationMode.DEFAULT)

    @Test
    fun hotStartup() = measureStartup(StartupMode.HOT, CompilationMode.DEFAULT)

    private fun measureStartup(
        mode: StartupMode,
        compilation: CompilationMode,
    ) {
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = compilation,
            startupMode = mode,
            iterations = 5,
            setupBlock = { pressHome() },
            measureBlock = { startActivityAndWait() }
        )
    }
}
