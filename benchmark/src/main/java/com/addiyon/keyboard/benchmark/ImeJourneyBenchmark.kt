package com.addiyon.keyboard.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class ImeJourneyBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun typingLanguageEmojiAndDeleteJourney() {
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                FrameTimingMetric(),
                MemoryUsageMetric(MemoryUsageMetric.Mode.Last),
                TraceSectionMetric(
                    sectionName = "Addiyon.prediction_request",
                    mode = TraceSectionMetric.Mode.Average
                )
            ),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            iterations = 10,
            setupBlock = {
                selectTargetIme()
                startEditor()
            },
            measureBlock = {
                runImeCriticalJourney()
            }
        )
    }
}
