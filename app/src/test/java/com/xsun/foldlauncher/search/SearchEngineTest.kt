package com.xsun.foldlauncher.search

import com.xsun.foldlauncher.model.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    private val sampleApps = listOf(
        AppEntry(
            label = "YouTube",
            packageName = "com.google.android.youtube",
            activityName = ".HomeActivity",
            launchCount = 12
        ),
        AppEntry(
            label = "Google Maps",
            packageName = "com.google.android.apps.nbu.files",
            activityName = ".MapsActivity",
            launchCount = 5
        ),
        AppEntry(
            label = "Gmail",
            packageName = "com.google.android.gm",
            activityName = ".ConversationListActivityGmail",
            launchCount = 20
        ),
        AppEntry(
            label = "Settings",
            packageName = "com.android.settings",
            activityName = ".Settings",
            launchCount = 8
        ),
        AppEntry(
            label = "GitHub",
            packageName = "com.github.android",
            activityName = ".MainActivity",
            launchCount = 15
        )
    )

    @Test
    fun emptyQuery_sortsByLaunchFrequencyThenAlphabetical() {
        val ranked = SearchEngine.rankApps(sampleApps, "")
        assertEquals("Gmail", ranked[0].label)
        assertEquals("GitHub", ranked[1].label)
        assertEquals("YouTube", ranked[2].label)
    }

    @Test
    fun prefixAndExactMatch_prioritizedFirst() {
        val ranked = SearchEngine.rankApps(sampleApps, "git")
        assertTrue(ranked.isNotEmpty())
        assertEquals("GitHub", ranked.first().label)
    }

    @Test
    fun acronymMatch_ranksMultiWordAppHigh() {
        val ranked = SearchEngine.rankApps(sampleApps, "gm")
        // Both Gmail (prefix "gm") and Google Maps (acronym "gm") should appear at top
        val topLabels = ranked.take(2).map { it.label }
        assertTrue(topLabels.contains("Gmail"))
        assertTrue(topLabels.contains("Google Maps"))
    }

    @Test
    fun subsequenceFuzzyMatch_findsYouTubeForYt() {
        val ranked = SearchEngine.rankApps(sampleApps, "yt")
        assertTrue(ranked.isNotEmpty())
        assertEquals("YouTube", ranked.first().label)
    }

    @Test
    fun inlineMathEvaluator_computesArithmeticCleanly() {
        assertEquals("540", SearchEngine.evaluateInlineMath("120 * 4.5"))
        assertEquals("420", SearchEngine.evaluateInlineMath("(1920 - 1080) / 2"))
        assertEquals("3.1429", SearchEngine.evaluateInlineMath("22 / 7"))
        assertNull(SearchEngine.evaluateInlineMath("youtube"))
        assertNull(SearchEngine.evaluateInlineMath("10 / 0"))
    }
}
