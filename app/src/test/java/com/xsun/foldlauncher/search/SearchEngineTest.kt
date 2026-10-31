package com.xsun.foldlauncher.search

import com.xsun.foldlauncher.model.AppEntry
import com.xsun.foldlauncher.posture.DevicePosture
import com.xsun.foldlauncher.ui.animation.AnimationPhysicsMath
import com.xsun.foldlauncher.ui.animation.RadialTriggerCorner
import com.xsun.foldlauncher.ui.components.QuickNavMode
import com.xsun.foldlauncher.ui.components.QuickNavigationLauncher
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

    @Test
    fun animationPhysicsMath_computesPostureRankWaveAndCornerArcTrigger() {
        assertEquals(0, AnimationPhysicsMath.postureExpansionRank(DevicePosture.COMPACT))
        assertEquals(1, AnimationPhysicsMath.postureExpansionRank(DevicePosture.FLEX_TABLETOP))
        assertEquals(2, AnimationPhysicsMath.postureExpansionRank(DevicePosture.EXPANDED))

        val peak = AnimationPhysicsMath.computeGaussianWaveIntensity(index = 5, centerIndex = 5f)
        val neighbor = AnimationPhysicsMath.computeGaussianWaveIntensity(index = 6, centerIndex = 5f)
        assertEquals(1.0f, peak, 0.001f)
        assertTrue(neighbor in 0.4f..0.8f)

        val corner = AnimationPhysicsMath.detectCornerArcTrigger(
            startX = 950f,
            startY = 1900f,
            dragDx = -60f,
            dragDy = -60f,
            widthPx = 1000f,
            heightPx = 2000f
        )
        assertEquals(RadialTriggerCorner.BOTTOM_RIGHT, corner)
    }

    @Test
    fun qwertyTypoTolerance_uverRanksUberThenUberEatsThenUserscripts() {
        val apps = sampleApps + listOf(
            AppEntry(
                label = "Uber",
                packageName = "com.ubercab",
                activityName = ".MainActivity",
                launchCount = 5
            ),
            AppEntry(
                label = "Uber Eats",
                packageName = "com.ubercab.eats",
                activityName = ".MainActivity",
                launchCount = 5
            ),
            AppEntry(
                label = "Userscripts",
                packageName = "com.userscripts.app",
                activityName = ".MainActivity",
                launchCount = 5
            ),
            AppEntry(
                label = "Termux",
                packageName = "com.termux",
                activityName = ".HomeActivity",
                launchCount = 5
            ),
            AppEntry(
                label = "Tailscale",
                packageName = "com.tailscale.ipn",
                activityName = ".MainActivity",
                launchCount = 5
            )
        )

        val uverResults = SearchEngine.rankApps(apps, "uver")
        val uverLabels = uverResults.map { it.label }
        assertEquals(listOf("Uber", "Uber Eats", "Userscripts"), uverLabels.take(3))

        // Transposition ("ubre" -> "Uber"), fat-finger insertion ("ubver" -> "Uber"),
        // missing char ("trmux" -> "Termux", "tailscle" -> "Tailscale", "sttings" -> "Settings")
        assertEquals("Uber", SearchEngine.rankApps(apps, "ubre").first().label)
        assertEquals("Uber", SearchEngine.rankApps(apps, "ubver").first().label)
        assertEquals("Termux", SearchEngine.rankApps(apps, "trmux").first().label)
        assertEquals("Tailscale", SearchEngine.rankApps(apps, "tailscle").first().label)
        assertEquals("Settings", SearchEngine.rankApps(apps, "sttings").first().label)
        assertEquals("Gmail", SearchEngine.rankApps(apps, "gmial").first().label)
    }

    @Test
    fun quickNavDeepLinks_formatsGoogleMapsAndUberLyftRideEstimateUrls() {
        val busUri = QuickNavigationLauncher.buildGoogleMapsPrimaryUri(QuickNavMode.BUS, "SFO Airport")
        assertTrue(busUri.contains("travelmode=transit"))
        assertTrue(busUri.contains("dir_action=navigate"))

        val bikeUri = QuickNavigationLauncher.buildGoogleMapsPrimaryUri(QuickNavMode.BIKE, "Golden Gate Park")
        assertEquals("google.navigation:q=Golden%20Gate%20Park&mode=b", bikeUri)

        val walkUri = QuickNavigationLauncher.buildGoogleMapsPrimaryUri(QuickNavMode.WALK, "Ferry Building")
        assertEquals("google.navigation:q=Ferry%20Building&mode=w", walkUri)

        val driveUri = QuickNavigationLauncher.buildGoogleMapsPrimaryUri(QuickNavMode.DRIVE, "1600 Amphitheatre Pkwy")
        assertEquals("google.navigation:q=1600%20Amphitheatre%20Pkwy&mode=d", driveUri)

        val uberDeepLink = QuickNavigationLauncher.buildUberDeepLinkUri(
            destinationName = "SFO Airport",
            formattedAddress = "San Francisco International Airport, CA",
            latitude = 37.6213,
            longitude = -122.3790
        )
        assertTrue(uberDeepLink.startsWith("uber://?action=setPickup&pickup=my_location"))
        assertTrue(uberDeepLink.contains("dropoff[latitude]=37.6213"))
        assertTrue(uberDeepLink.contains("dropoff[longitude]=-122.379"))

        val lyftDeepLink = QuickNavigationLauncher.buildLyftDeepLinkUri(
            formattedAddress = "San Francisco International Airport, CA",
            latitude = 37.6213,
            longitude = -122.3790
        )
        assertTrue(lyftDeepLink.startsWith("lyft://ridetype?id=lyft"))
        assertTrue(lyftDeepLink.contains("destination[latitude]=37.6213"))
        assertTrue(lyftDeepLink.contains("destination[longitude]=-122.379"))
    }
}
