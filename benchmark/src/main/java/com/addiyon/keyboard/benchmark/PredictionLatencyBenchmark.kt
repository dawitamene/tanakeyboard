package com.addiyon.keyboard.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class PredictionLatencyBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun warmEnglishNextWordRequestToPublication() {
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = predictionMetrics(),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            iterations = 10,
            setupBlock = {
                selectTargetIme()
                startEditor()
                command("language_english")
            },
            measureBlock = {
                runEnglishPredictionJourney()
            }
        )
    }

    @Test
    fun warmAmharicNextWordRequestToPublication() {
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = predictionMetrics(),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            iterations = 10,
            setupBlock = {
                selectTargetIme()
                startEditor()
                command("language_amharic")
            },
            measureBlock = {
                runAmharicPredictionJourney()
            }
        )
    }

    private fun predictionMetrics(): List<TraceSectionMetric> =
        listOf(
            TraceSectionMetric(
                sectionName = "Addiyon.prediction_request",
                mode = TraceSectionMetric.Mode.First
            ),
            TraceSectionMetric(
                sectionName = "Addiyon.prediction_queue",
                mode = TraceSectionMetric.Mode.First
            ),
            TraceSectionMetric(
                sectionName = "Addiyon.ngram_query",
                mode = TraceSectionMetric.Mode.First
            ),
            TraceSectionMetric(
                sectionName = "Addiyon.prediction_publication",
                mode = TraceSectionMetric.Mode.First
            )
        )
}
