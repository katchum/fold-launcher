package com.xsun.foldlauncher.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.xsun.foldlauncher.service.FoldAccessibilityService
import com.xsun.foldlauncher.service.SystemTweaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.TimeZone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Hardware 6-bit `RGB222` (64-color) Memory-In-Pixel (MIP) palette (`~200 PPI` transflective LCD spec:
 * each R, G, B channel is strictly quantized to 2 bits: `0x00`, `0x55`, `0xAA`, `0xFF`).
 */
private val MipBlack = Color(0xFF000000)      // (0, 0, 0)
private val MipDarkTeal = Color(0xFF005555)   // (0, 1, 1)
private val MipDarkBlue = Color(0xFF000055)   // (0, 0, 1)
private val MipDarkGray = Color(0xFF555555)   // (1, 1, 1)
private val MipSilver = Color(0xFFAAAAAA)     // (2, 2, 2)
private val MipWhite = Color(0xFFFFFFFF)      // (3, 3, 3)
private val MipCyan = Color(0xFF00FFFF)       // (0, 3, 3)
private val MipGreen = Color(0xFF00FF55)      // (0, 3, 1)
private val MipAmber = Color(0xFFFFAA00)      // (3, 2, 0)
private val MipRed = Color(0xFFFF5555)        // (3, 1, 1)

/**
 * Simulates a ~200 PPI reflective Memory-In-Pixel (MIP) LCD panel on a ~480 PPI AMOLED screen:
 * - Coarse `3.dp` x `3.dp` discrete pixel cell matrix with `1px` dark inter-pixel cell gaps
 * - Optional 2x2 ordered Bayer checkerboard dithering (zero smooth alpha gradients)
 */
private fun Modifier.mip200PpiMatrix(
    ditherColor: Color = MipDarkTeal,
    enableCheckerboardDither: Boolean = false
): Modifier = this.drawBehind {
    val cellPx = floor(3.dp.toPx()).coerceAtLeast(3f)
    val gapPx = 1f
    val cols = (size.width / cellPx).toInt()
    val rows = (size.height / cellPx).toInt()

    if (enableCheckerboardDither) {
        for (ry in 0 until rows) {
            for (cx in 0 until cols) {
                if ((cx + ry) % 2 == 0) {
                    drawRect(
                        color = ditherColor,
                        topLeft = Offset(cx * cellPx, ry * cellPx),
                        size = Size(cellPx - gapPx, cellPx - gapPx)
                    )
                }
            }
        }
    }

    // Draw crisp 200-PPI MIP pixel cell grid lines
    var x = cellPx - gapPx
    while (x < size.width) {
        drawRect(
            color = MipBlack,
            topLeft = Offset(x, 0f),
            size = Size(gapPx, size.height)
        )
        x += cellPx
    }
    var y = cellPx - gapPx
    while (y < size.height) {
        drawRect(
            color = MipBlack,
            topLeft = Offset(0f, y),
            size = Size(size.width, gapPx)
        )
        y += cellPx
    }
}

/**
 * Five quick-navigation travel modes selectable in [OneUiQuickNavigationBar]:
 * - [BUS], [BIKE], [WALK], [DRIVE]: launch Google Maps directly with the route/navigation started.
 * - [RIDE]: launches Uber (`com.ubercab`) and Lyft (`me.lyft.android`) side-by-side in split-screen
 *   with the destination pre-filled and the price estimate screen open.
 */
enum class QuickNavMode(
    val key: String,
    val label: String,
    val icon: String,
    val shortBadge: String,
    val mapsModeCode: String?,
    val mapsTravelMode: String?
) {
    BUS("bus", "Bus", "BUS", "[BUS]", "r", "transit"),
    BIKE("bike", "Bike", "BIK", "[BIK]", "b", "bicycling"),
    WALK("walk", "Walk", "WLK", "[WLK]", "w", "walking"),
    DRIVE("drive", "Drive", "DRV", "[DRV]", "d", "driving"),
    RIDE("ride", "Ride (Uber + Lyft)", "RDE", "[RDE]", null, null);

    fun next(): QuickNavMode {
        val all = values()
        return all[(ordinal + 1) % all.size]
    }

    companion object {
        fun fromKey(key: String?): QuickNavMode {
            if (key.isNullOrBlank()) return DRIVE
            return values().firstOrNull {
                it.key.equals(key, ignoreCase = true) || it.name.equals(key, ignoreCase = true)
            } ?: DRIVE
        }
    }
}

/**
 * Autocomplete destination item with coordinates (`latitude`, `longitude`) and distance in miles
 * (`distanceMiles`) from the user's current position so nearby results always rank first.
 */
data class NavLocationSuggestion(
    val title: String,
    val subtitle: String,
    val fullQuery: String = if (subtitle.isNotBlank() && !subtitle.equals("Recent destination", ignoreCase = true)) {
        "$title, $subtitle"
    } else {
        title
    },
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isRecent: Boolean = false,
    val distanceMiles: Double? = null
) {
    val formattedDistanceBadge: String?
        get() {
            val d = distanceMiles ?: return null
            if (d.isNaN() || d.isInfinite() || d < 0.0) return null
            return if (d < 10.0) {
                String.format(Locale.US, "%.1fMI", d)
            } else {
                String.format(Locale.US, "%.0fMI", d)
            }
        }
}

object QuickNavigationLauncher {
    const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
    const val UBER_PACKAGE = "com.ubercab"
    const val LYFT_PACKAGE = "me.lyft.android"

    private const val PREFS_NAME = "fold8_quick_nav_prefs"
    private const val KEY_SELECTED_MODE = "quick_nav_selected_mode"
    private const val KEY_RECENT_PLACES = "quick_nav_recent_places"
    private const val KEY_CACHED_USER_LAT = "quick_nav_cached_user_lat"
    private const val KEY_CACHED_USER_LON = "quick_nav_cached_user_lon"
    private const val KEY_CACHED_USER_TS = "quick_nav_cached_user_ts"

    // Default SF Bay Area coordinates when running in America/Los_Angeles before GPS/IP resolves
    private const val DEFAULT_SF_LAT = 37.7965
    private const val DEFAULT_SF_LON = -122.3979

    @Volatile
    private var memoryCachedCoords: Pair<Double, Double>? = null

    @Volatile
    private var memoryCachedCoordsTimeMs: Long = 0L

    private fun urlEncode(value: String): String {
        return try {
            URLEncoder.encode(value.trim(), "UTF-8").replace("+", "%20")
        } catch (_: Exception) {
            value.trim().replace(" ", "%20")
        }
    }

    /**
     * Computes Haversine great-circle distance in statute miles between two WGS84 coordinates.
     */
    fun computeDistanceMiles(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadiusMiles = 3958.7613
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2.0).pow(2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt((1.0 - a).coerceAtLeast(0.0)))
        return earthRadiusMiles * c
    }

    /**
     * Resolves the user's current `(latitude, longitude)` so location search (`"best buy"`, `"target"`, etc.)
     * always biases and sorts by proximity to the user's real location instead of East Coast / global matches:
     * 1. Android `LocationManager` (`gps`, `fused`, `network`, `passive`) if location permission is granted.
     * 2. Cached or live IP geolocation (`http://ip-api.com/json/?fields=status,lat,lon`) on `Dispatchers.IO`.
     * 3. TimeZone-aware fallback (`37.7965, -122.3979` San Francisco for `America/Los_Angeles`).
     */
    @SuppressLint("MissingPermission")
    fun resolveUserCoordinates(context: Context): Pair<Double, Double> {
        // 1. Check Android LocationManager if coarse/fine location permission is granted
        runCatching {
            val hasCoarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasFine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasCoarse || hasFine) {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                if (lm != null) {
                    val providers = listOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                    var bestLoc: Location? = null
                    for (provider in providers) {
                        val loc = runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: continue
                        if (bestLoc == null || loc.time > bestLoc.time) {
                            bestLoc = loc
                        }
                    }
                    if (bestLoc != null && (!bestLoc.latitude.isNaN() && !bestLoc.longitude.isNaN())) {
                        val pair = bestLoc.latitude to bestLoc.longitude
                        cacheUserCoordinates(context, pair.first, pair.second)
                        return pair
                    }
                }
            }
        }

        // 2. Check fresh in-memory cache (< 30 minutes)
        val now = System.currentTimeMillis()
        val mem = memoryCachedCoords
        if (mem != null && (now - memoryCachedCoordsTimeMs) < 30 * 60 * 1000L) {
            return mem
        }

        // 3. Check SharedPreferences cache (< 6 hours)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTs = prefs.getLong(KEY_CACHED_USER_TS, 0L)
        if (prefs.contains(KEY_CACHED_USER_LAT) && prefs.contains(KEY_CACHED_USER_LON) && (now - savedTs) < 6 * 3600 * 1000L) {
            val lat = prefs.getFloat(KEY_CACHED_USER_LAT, DEFAULT_SF_LAT.toFloat()).toDouble()
            val lon = prefs.getFloat(KEY_CACHED_USER_LON, DEFAULT_SF_LON.toFloat()).toDouble()
            val pair = lat to lon
            memoryCachedCoords = pair
            memoryCachedCoordsTimeMs = now
            return pair
        }

        // 4. Lightweight IP Geolocation lookup (only runs on background thread during fetchSuggestions)
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runCatching {
                val url = URL("http://ip-api.com/json/?fields=status,lat,lon")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 900
                    readTimeout = 900
                    setRequestProperty("User-Agent", "Fold8Launcher/0.10.1")
                }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    if (json.optString("status") == "success") {
                        val lat = json.optDouble("lat", Double.NaN)
                        val lon = json.optDouble("lon", Double.NaN)
                        if (!lat.isNaN() && !lon.isNaN()) {
                            cacheUserCoordinates(context, lat, lon)
                            return lat to lon
                        }
                    }
                }
            }
        }

        // 5. Fallback to stored coordinates or SF Bay Area
        if (prefs.contains(KEY_CACHED_USER_LAT) && prefs.contains(KEY_CACHED_USER_LON)) {
            val lat = prefs.getFloat(KEY_CACHED_USER_LAT, DEFAULT_SF_LAT.toFloat()).toDouble()
            val lon = prefs.getFloat(KEY_CACHED_USER_LON, DEFAULT_SF_LON.toFloat()).toDouble()
            return lat to lon
        }
        val tzId = TimeZone.getDefault().id
        return if (tzId.contains("New_York") || tzId.contains("Eastern")) {
            40.7128 to -74.0060
        } else if (tzId.contains("Chicago") || tzId.contains("Central")) {
            41.8781 to -87.6298
        } else {
            DEFAULT_SF_LAT to DEFAULT_SF_LON
        }
    }

    private fun cacheUserCoordinates(context: Context, lat: Double, lon: Double) {
        val now = System.currentTimeMillis()
        memoryCachedCoords = lat to lon
        memoryCachedCoordsTimeMs = now
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putFloat(KEY_CACHED_USER_LAT, lat.toFloat())
                .putFloat(KEY_CACHED_USER_LON, lon.toFloat())
                .putLong(KEY_CACHED_USER_TS, now)
                .apply()
        }
    }

    /**
     * Builds the primary Google Maps URI to start navigation/route immediately for [mode].
     * - When [latitude] and [longitude] are provided (from a resolved [NavLocationSuggestion]), navigates
     *   directly to `$latitude,$longitude` so Google Maps starts turn-by-turn routing to the exact local place
     *   without failing on multi-part address strings.
     * - For `DRIVE` (`d`), `BIKE` (`b`), and `WALK` (`w`): uses `google.navigation:q=<target>&mode=<d|b|w>`.
     * - For `BUS` (`transit`): uses `https://www.google.com/maps/dir/?api=1&destination=<target>&travelmode=transit&dir_action=navigate`.
     */
    fun buildGoogleMapsPrimaryUri(
        mode: QuickNavMode,
        destinationQuery: String,
        latitude: Double? = null,
        longitude: Double? = null
    ): String {
        val target = if (latitude != null && longitude != null) {
            "$latitude,$longitude"
        } else {
            destinationQuery.trim()
        }
        val encoded = if (latitude != null && longitude != null) {
            target
        } else {
            urlEncode(target)
        }
        return when (mode) {
            QuickNavMode.BUS ->
                "https://www.google.com/maps/dir/?api=1&destination=$encoded&travelmode=transit&dir_action=navigate"
            QuickNavMode.BIKE ->
                "google.navigation:q=$encoded&mode=b"
            QuickNavMode.WALK ->
                "google.navigation:q=$encoded&mode=w"
            QuickNavMode.DRIVE, QuickNavMode.RIDE ->
                "google.navigation:q=$encoded&mode=d"
        }
    }

    /**
     * Universal Google Maps Directions URL fallback with `dir_action=navigate`.
     */
    fun buildGoogleMapsWebFallbackUri(
        mode: QuickNavMode,
        destinationQuery: String,
        latitude: Double? = null,
        longitude: Double? = null
    ): String {
        val encoded = if (latitude != null && longitude != null) {
            "$latitude,$longitude"
        } else {
            urlEncode(destinationQuery.trim())
        }
        val travelMode = mode.mapsTravelMode ?: "driving"
        return "https://www.google.com/maps/dir/?api=1&destination=$encoded&travelmode=$travelMode&dir_action=navigate"
    }

    /**
     * Builds the Uber deep link that pre-fills pickup = `my_location`, sets dropoff address/nickname
     * and coordinates, and opens directly to the ride options & price estimate sheet.
     */
    fun buildUberDeepLinkUri(
        destinationName: String,
        formattedAddress: String = destinationName,
        latitude: Double? = null,
        longitude: Double? = null
    ): String {
        val encName = urlEncode(destinationName.ifBlank { formattedAddress })
        val encAddr = urlEncode(formattedAddress.ifBlank { destinationName })
        val coordPart = if (latitude != null && longitude != null) {
            "&dropoff[latitude]=$latitude&dropoff[longitude]=$longitude"
        } else {
            ""
        }
        return "uber://?action=setPickup&pickup=my_location&dropoff[nickname]=$encName&dropoff[formatted_address]=$encAddr$coordPart"
    }

    /**
     * Builds the Uber Universal Link (`https://m.uber.com/ul/...`) with destination & price estimate open.
     */
    fun buildUberWebFallbackUri(
        destinationName: String,
        formattedAddress: String = destinationName,
        latitude: Double? = null,
        longitude: Double? = null
    ): String {
        val encName = urlEncode(destinationName.ifBlank { formattedAddress })
        val encAddr = urlEncode(formattedAddress.ifBlank { destinationName })
        val coordPart = if (latitude != null && longitude != null) {
            "&dropoff[latitude]=$latitude&dropoff[longitude]=$longitude"
        } else {
            ""
        }
        return "https://m.uber.com/ul/?action=setPickup&pickup=my_location&dropoff[nickname]=$encName&dropoff[formatted_address]=$encAddr$coordPart"
    }

    /**
     * Builds the Lyft `ridetype` deep link (`lyft://ridetype?id=lyft&destination[latitude]=...`)
     * which opens Lyft directly with the destination set and the price estimate screen open.
     */
    fun buildLyftDeepLinkUri(
        formattedAddress: String,
        latitude: Double? = null,
        longitude: Double? = null
    ): String {
        val encAddr = urlEncode(formattedAddress)
        return if (latitude != null && longitude != null) {
            "lyft://ridetype?id=lyft&destination[latitude]=$latitude&destination[longitude]=$longitude&destination[address]=$encAddr"
        } else {
            "lyft://ridetype?id=lyft&destination[address]=$encAddr"
        }
    }

    /**
     * Builds the Lyft web / universal link fallback with destination & price estimate open.
     */
    fun buildLyftWebFallbackUri(
        formattedAddress: String,
        latitude: Double? = null,
        longitude: Double? = null
    ): String {
        val encAddr = urlEncode(formattedAddress)
        return if (latitude != null && longitude != null) {
            "https://ride.lyft.com/ridetype?id=lyft&destination[latitude]=$latitude&destination[longitude]=$longitude&destination[address]=$encAddr"
        } else {
            "https://ride.lyft.com/?destination[address]=$encAddr"
        }
    }

    fun getSavedMode(context: Context): QuickNavMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return QuickNavMode.fromKey(prefs.getString(KEY_SELECTED_MODE, QuickNavMode.DRIVE.key))
    }

    fun saveSelectedMode(context: Context, mode: QuickNavMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_MODE, mode.key)
            .apply()
    }

    fun loadRecentDestinations(context: Context): List<NavLocationSuggestion> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_RECENT_PLACES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<NavLocationSuggestion>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val title = obj.optString("title").trim()
                if (title.isEmpty()) continue
                val subtitle = obj.optString("subtitle", "Recent destination")
                val fullQuery = obj.optString("fullQuery", title)
                val lat = if (obj.has("lat")) obj.optDouble("lat") else null
                val lon = if (obj.has("lon")) obj.optDouble("lon") else null
                list.add(
                    NavLocationSuggestion(
                        title = title,
                        subtitle = subtitle,
                        fullQuery = fullQuery,
                        latitude = lat?.takeIf { !it.isNaN() },
                        longitude = lon?.takeIf { !it.isNaN() },
                        isRecent = true
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    fun saveRecentDestination(context: Context, suggestion: NavLocationSuggestion) {
        val existing = loadRecentDestinations(context).toMutableList()
        existing.removeAll {
            it.fullQuery.equals(suggestion.fullQuery, ignoreCase = true) ||
                (it.title.equals(suggestion.title, ignoreCase = true) && it.subtitle.equals(suggestion.subtitle, ignoreCase = true))
        }
        existing.add(0, suggestion.copy(isRecent = true))
        val trimmed = existing.take(6)
        runCatching {
            val arr = JSONArray()
            for (item in trimmed) {
                val obj = JSONObject().apply {
                    put("title", item.title)
                    put("subtitle", item.subtitle)
                    put("fullQuery", item.fullQuery)
                    if (item.latitude != null) put("lat", item.latitude)
                    if (item.longitude != null) put("lon", item.longitude)
                }
                arr.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RECENT_PLACES, arr.toString())
                .apply()
        }
    }

    /**
     * Queries live nearby location suggestions for [query] biased to the user's current coordinates:
     * 1. Matching recent destinations from SharedPreferences,
     * 2. Location-biased Photon OSM GeoJSON autocomplete (`&lat=<userLat>&lon=<userLon>`),
     * 3. Bounded `android.location.Geocoder` (`userLat ± 0.55°, userLon ± 0.55°`),
     * 4. Sorted by Haversine distance (`distanceMiles`) from `(userLat, userLon)` so local places
     *    (e.g. San Francisco Best Buy `1.8MI`) always rank above distant East Coast / global matches!
     */
    suspend fun fetchSuggestions(
        context: Context,
        query: String,
        maxResults: Int = 5
    ): List<NavLocationSuggestion> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val (userLat, userLon) = resolveUserCoordinates(context)
        val recents = loadRecentDestinations(context).map { recent ->
            if (recent.latitude != null && recent.longitude != null) {
                recent.copy(
                    distanceMiles = computeDistanceMiles(userLat, userLon, recent.latitude, recent.longitude)
                )
            } else {
                recent
            }
        }
        if (trimmed.length < 2) {
            return@withContext recents.take(maxResults)
        }

        val matchingRecents = recents.filter { recent ->
            recent.title.contains(trimmed, ignoreCase = true) ||
                recent.fullQuery.contains(trimmed, ignoreCase = true)
        }.take(2)

        val candidates = mutableListOf<NavLocationSuggestion>()

        // 1. Query Photon with explicit userLat/userLon bias
        val photonItems = queryPhotonAutocomplete(
            query = trimmed,
            maxResults = (maxResults * 2).coerceAtLeast(8),
            userLat = userLat,
            userLon = userLon
        )
        candidates.addAll(photonItems)

        // 2. Query Android Geocoder bounded to nearby region
        val geocoderItems = queryAndroidGeocoder(
            context = context,
            query = trimmed,
            maxResults = maxResults,
            userLat = userLat,
            userLon = userLon
        )
        for (item in geocoderItems) {
            if (candidates.none { isSamePlace(it, item) }) {
                candidates.add(item)
            }
        }

        // Sort candidates by proximity to user (nearest first!)
        val sortedNearby = candidates.sortedBy { it.distanceMiles ?: Double.MAX_VALUE }

        val finalResults = mutableListOf<NavLocationSuggestion>()
        finalResults.addAll(matchingRecents)
        for (item in sortedNearby) {
            if (finalResults.none { isSamePlace(it, item) }) {
                finalResults.add(item)
            }
            if (finalResults.size >= maxResults) break
        }

        finalResults.take(maxResults)
    }

    private fun isSamePlace(a: NavLocationSuggestion, b: NavLocationSuggestion): Boolean {
        if (a.fullQuery.equals(b.fullQuery, ignoreCase = true)) return true
        val latA = a.latitude
        val lonA = a.longitude
        val latB = b.latitude
        val lonB = b.longitude
        if (latA != null && lonA != null && latB != null && lonB != null) {
            val dist = computeDistanceMiles(latA, lonA, latB, lonB)
            if (dist < 0.08 && a.title.equals(b.title, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun queryAndroidGeocoder(
        context: Context,
        query: String,
        maxResults: Int,
        userLat: Double,
        userLon: Double
    ): List<NavLocationSuggestion> {
        return runCatching {
            if (!Geocoder.isPresent()) return emptyList()
            val geocoder = Geocoder(context, Locale.getDefault())
            // First search within a ~45-mile bounding box around userLat/userLon
            val delta = 0.65
            val bounded = runCatching {
                geocoder.getFromLocationName(
                    query,
                    maxResults,
                    userLat - delta,
                    userLon - delta,
                    userLat + delta,
                    userLon + delta
                )
            }.getOrNull()
            val addresses = if (!bounded.isNullOrEmpty()) {
                bounded
            } else {
                geocoder.getFromLocationName(query, maxResults) ?: return emptyList()
            }
            addresses.mapNotNull { addr ->
                val fullLine = (0..addr.maxAddressLineIndex)
                    .mapNotNull { idx -> addr.getAddressLine(idx)?.trim()?.takeIf { it.isNotEmpty() } }
                    .joinToString(", ")
                val feature = addr.featureName?.trim()
                val thoroughfare = listOfNotNull(addr.subThoroughfare, addr.thoroughfare)
                    .joinToString(" ")
                    .trim()
                val localityParts = listOfNotNull(addr.locality, addr.adminArea)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")

                val primaryTitle = when {
                    !feature.isNullOrBlank() && !feature.equals(addr.SubThoroughfareOrEmpty(), ignoreCase = true) ->
                        feature
                    thoroughfare.isNotBlank() -> thoroughfare
                    fullLine.isNotBlank() -> fullLine.substringBefore(",").trim()
                    else -> return@mapNotNull null
                }
                val subtitle = when {
                    thoroughfare.isNotBlank() && localityParts.isNotBlank() && !primaryTitle.equals(thoroughfare, ignoreCase = true) ->
                        "$thoroughfare, $localityParts"
                    fullLine.isNotBlank() && !fullLine.equals(primaryTitle, ignoreCase = true) ->
                        fullLine.removePrefix(primaryTitle).trimStart(',', ' ').ifBlank { localityParts }
                    else -> localityParts
                }
                val lat = if (addr.hasLatitude()) addr.latitude else null
                val lon = if (addr.hasLongitude()) addr.longitude else null
                val distMiles = if (lat != null && lon != null) {
                    computeDistanceMiles(userLat, userLon, lat, lon)
                } else {
                    null
                }
                NavLocationSuggestion(
                    title = primaryTitle,
                    subtitle = subtitle,
                    fullQuery = fullLine.ifBlank {
                        if (subtitle.isNotBlank()) "$primaryTitle, $subtitle" else primaryTitle
                    },
                    latitude = lat,
                    longitude = lon,
                    distanceMiles = distMiles
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun android.location.Address.SubThoroughfareOrEmpty(): String = subThoroughfare?.trim() ?: ""

    private fun queryPhotonAutocomplete(
        query: String,
        maxResults: Int,
        userLat: Double,
        userLon: Double
    ): List<NavLocationSuggestion> {
        return runCatching {
            val encoded = urlEncode(query)
            val url = URL(
                "https://photon.komoot.io/api/?q=$encoded&lat=$userLat&lon=$userLon&zoom=13&location_bias_scale=0.9&limit=$maxResults&lang=en"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1600
                readTimeout = 1600
                setRequestProperty("User-Agent", "Fold8Launcher/0.10.1")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return emptyList()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val features = root.optJSONArray("features") ?: return emptyList()
            val list = mutableListOf<NavLocationSuggestion>()
            for (i in 0 until features.length()) {
                val feat = features.getJSONObject(i)
                val props = feat.optJSONObject("properties") ?: continue
                val geom = feat.optJSONObject("geometry")
                val coords = geom?.optJSONArray("coordinates")
                val lon = if (coords != null && coords.length() >= 2) coords.optDouble(0) else null
                val lat = if (coords != null && coords.length() >= 2) coords.optDouble(1) else null

                val name = props.optString("name").trim()
                val street = listOfNotNull(
                    props.optString("housenumber").trim().takeIf { it.isNotEmpty() },
                    props.optString("street").trim().takeIf { it.isNotEmpty() }
                ).joinToString(" ")
                val cityState = listOfNotNull(
                    props.optString("city").trim().takeIf { it.isNotEmpty() },
                    props.optString("state").trim().takeIf { it.isNotEmpty() }
                ).joinToString(", ")

                val title = name.ifBlank { street }
                if (title.isBlank()) continue
                val subtitle = listOfNotNull(
                    street.takeIf { it.isNotBlank() && !it.equals(title, ignoreCase = true) },
                    cityState.takeIf { it.isNotBlank() }
                ).joinToString(", ")

                val validLat = lat?.takeIf { !it.isNaN() }
                val validLon = lon?.takeIf { !it.isNaN() }
                val distMiles = if (validLat != null && validLon != null) {
                    computeDistanceMiles(userLat, userLon, validLat, validLon)
                } else {
                    null
                }

                list.add(
                    NavLocationSuggestion(
                        title = title,
                        subtitle = subtitle,
                        fullQuery = if (subtitle.isNotBlank()) "$title, $subtitle" else title,
                        latitude = validLat,
                        longitude = validLon,
                        distanceMiles = distMiles
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    /**
     * Executes the selected [mode] for [destination]:
     * - Resolves nearby coordinates first if `latitude`/`longitude` are missing,
     * - `BUS`, `BIKE`, `WALK`, `DRIVE`: opens Google Maps with navigation / transit route started to the exact coordinates,
     * - `RIDE`: launches **Uber and Lyft side-by-side in split-screen** (`StageCoordinator` top + bottom stages)
     *   with the destination set and price estimate open!
     */
    suspend fun executeNavigation(
        context: Context,
        mode: QuickNavMode,
        destination: NavLocationSuggestion
    ) {
        val resolved = if (destination.latitude == null || destination.longitude == null) {
            withContext(Dispatchers.IO) {
                val (userLat, userLon) = resolveUserCoordinates(context)
                val candidates = mutableListOf<NavLocationSuggestion>()
                candidates.addAll(queryPhotonAutocomplete(destination.fullQuery, 6, userLat, userLon))
                candidates.addAll(queryAndroidGeocoder(context, destination.fullQuery, 4, userLat, userLon))
                val nearest = candidates
                    .filter { it.latitude != null && it.longitude != null }
                    .minByOrNull { it.distanceMiles ?: Double.MAX_VALUE }
                if (nearest != null) {
                    destination.copy(
                        title = nearest.title.ifBlank { destination.title },
                        subtitle = nearest.subtitle.ifBlank { destination.subtitle },
                        fullQuery = nearest.fullQuery.ifBlank { destination.fullQuery },
                        latitude = nearest.latitude,
                        longitude = nearest.longitude,
                        distanceMiles = nearest.distanceMiles
                    )
                } else {
                    destination
                }
            }
        } else {
            destination
        }

        saveRecentDestination(context, resolved)

        withContext(Dispatchers.Main) {
            if (mode == QuickNavMode.RIDE) {
                launchUberAndLyftSplitScreen(context, resolved)
            } else {
                launchGoogleMapsNavigation(context, mode, resolved)
            }
        }
    }

    /**
     * Resolves nearby coordinates for [destination] if missing and launches a single ride-share app
     * (`isUber == true` -> Uber, `isUber == false` -> Lyft) with the destination pre-filled.
     */
    suspend fun executeSingleRide(
        context: Context,
        isUber: Boolean,
        destination: NavLocationSuggestion
    ) {
        val resolved = if (destination.latitude == null || destination.longitude == null) {
            withContext(Dispatchers.IO) {
                val (userLat, userLon) = resolveUserCoordinates(context)
                val candidates = mutableListOf<NavLocationSuggestion>()
                candidates.addAll(queryPhotonAutocomplete(destination.fullQuery, 6, userLat, userLon))
                candidates.addAll(queryAndroidGeocoder(context, destination.fullQuery, 4, userLat, userLon))
                val nearest = candidates
                    .filter { it.latitude != null && it.longitude != null }
                    .minByOrNull { it.distanceMiles ?: Double.MAX_VALUE }
                if (nearest != null) {
                    destination.copy(
                        title = nearest.title.ifBlank { destination.title },
                        subtitle = nearest.subtitle.ifBlank { destination.subtitle },
                        fullQuery = nearest.fullQuery.ifBlank { destination.fullQuery },
                        latitude = nearest.latitude,
                        longitude = nearest.longitude,
                        distanceMiles = nearest.distanceMiles
                    )
                } else {
                    destination
                }
            }
        } else {
            destination
        }

        saveRecentDestination(context, resolved)

        withContext(Dispatchers.Main) {
            copyDestinationToClipboard(context, resolved)
            launchSingleRideApp(
                context = context,
                isUber = isUber,
                destination = resolved,
                tiledBounds = null,
                adjacentFlag = false
            )
        }
    }

    private fun launchGoogleMapsNavigation(
        context: Context,
        mode: QuickNavMode,
        destination: NavLocationSuggestion
    ) {
        val primaryUri = Uri.parse(
            buildGoogleMapsPrimaryUri(
                mode = mode,
                destinationQuery = destination.fullQuery,
                latitude = destination.latitude,
                longitude = destination.longitude
            )
        )
        val mapsIntent = Intent(Intent.ACTION_VIEW, primaryUri).apply {
            setPackage(GOOGLE_MAPS_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val startedMaps = runCatching {
            context.startActivity(mapsIntent)
            true
        }.getOrDefault(false)

        if (!startedMaps) {
            val webUri = Uri.parse(
                buildGoogleMapsWebFallbackUri(
                    mode = mode,
                    destinationQuery = destination.fullQuery,
                    latitude = destination.latitude,
                    longitude = destination.longitude
                )
            )
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, webUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }
    }

    /**
     * Computes non-overlapping tiled multi-window [Rect] bounds (`firstPane` + `secondPane`):
     * - Unfolded Z Fold 8 / landscape (`width >= height`): Left half + Right half.
     * - Portrait slab / cover screen (`width < height`): Top half + Bottom half.
     */
    fun computeTiledSplitBounds(context: Context): Pair<Rect, Rect> {
        val dm = context.resources.displayMetrics
        val width = dm.widthPixels.coerceAtLeast(720)
        val height = dm.heightPixels.coerceAtLeast(1280)
        val density = dm.density.coerceAtLeast(1f)
        val topInset = (38f * density).toInt()
        val bottomInset = (52f * density).toInt()
        val sidePad = (6f * density).toInt()
        val gap = (6f * density).toInt()

        return if (width >= height) {
            val midX = width / 2
            val leftRect = Rect(sidePad, topInset, midX - (gap / 2), height - bottomInset)
            val rightRect = Rect(midX + (gap / 2), topInset, width - sidePad, height - bottomInset)
            leftRect to rightRect
        } else {
            val usableHeight = (height - topInset - bottomInset).coerceAtLeast(600)
            val midY = topInset + (usableHeight / 2)
            val topRect = Rect(sidePad, topInset, width - sidePad, midY - (gap / 2))
            val bottomRect = Rect(sidePad, midY + (gap / 2), width - sidePad, height - bottomInset)
            topRect to bottomRect
        }
    }

    /**
     * Builds a safe [ActivityOptions] bundle with explicit screen [bounds] using ONLY the public
     * `ActivityOptions.makeBasic().setLaunchBounds(bounds)` API.
     *
     * IMPORTANT: Never set `android.activity.windowingMode` (`setLaunchWindowingMode(5)`) from a
     * non-system app, because AOSP `SafeActivityOptions.checkPermissions()` throws `SecurityException`
     * if `launchWindowingMode != WINDOWING_MODE_UNDEFINED`.
     */
    private fun buildTiledFreeformOptionsBundle(bounds: Rect): Bundle? {
        return runCatching {
            ActivityOptions.makeBasic().setLaunchBounds(bounds).toBundle()
        }.getOrNull()
    }

    /**
     * Searches both Personal Profile (`UserHandle{0}`) and Managed Work Profiles (`UserHandle{10}`)
     * via [LauncherApps] + [UserManager] to locate [packageName] even when installed only in Work Profile.
     */
    private fun findPackageAcrossProfiles(
        context: Context,
        packageName: String
    ): Pair<ComponentName, UserHandle>? {
        return runCatching {
            val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
            val userManager = context.getSystemService(UserManager::class.java)
            val myUser = Process.myUserHandle()
            val profiles = userManager?.userProfiles ?: listOf(myUser)
            // Check personal profile first, then work profiles
            val orderedProfiles = profiles.sortedBy { if (it == myUser) 0 else 1 }
            for (profile in orderedProfiles) {
                val activities = runCatching {
                    launcherApps.getActivityList(packageName, profile)
                }.getOrDefault(emptyList())
                val first = activities.firstOrNull()
                if (first != null) {
                    return first.componentName to profile
                }
            }
            null
        }.getOrNull()
    }

    /**
     * Resolves the best deep-link [Intent] for [pkg] (`com.ubercab` or `me.lyft.android`) across
     * Personal Profile and Work Profile (`CrossProfileIntentForwarderActivity`):
     * 1. Direct scheme (`uber://` / `lyft://`) with `.setPackage(pkg)` in Personal Profile,
     * 2. Universal HTTPS link (`https://m.uber.com/ul/...` / `https://ride.lyft.com/...`) with `.setPackage(pkg)`,
     * 3. Work Profile `IntentForwarderActivity` / `CrossProfileIntentForwarderActivity` that forwards
     *    the deep link URI directly into Work Profile Uber/Lyft (`UserHandle{10}`),
     * 4. Standard Personal Profile launch intent (`pm.getLaunchIntentForPackage(pkg)`).
     */
    private fun resolveBestRideIntent(
        context: Context,
        pkg: String,
        deepLinkUri: Uri,
        universalWebUri: Uri
    ): Intent? {
        val pm = context.packageManager

        // 1. Native scheme in Personal Profile
        val directSchemeIntent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
            setPackage(pkg)
        }
        if (runCatching { pm.resolveActivity(directSchemeIntent, 0) }.getOrNull() != null) {
            return directSchemeIntent
        }

        // 2. Universal HTTPS link in Personal Profile
        val directWebIntent = Intent(Intent.ACTION_VIEW, universalWebUri).apply {
            setPackage(pkg)
        }
        if (runCatching { pm.resolveActivity(directWebIntent, 0) }.getOrNull() != null) {
            return directWebIntent
        }

        // 3. Check for Cross-Profile Intent Forwarder (forwards uber:// or https://m.uber.com/ul/ to Work Profile User 10)
        for (candidateUri in listOf(deepLinkUri, universalWebUri)) {
            val probe = Intent(Intent.ACTION_VIEW, candidateUri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            val resolvedList = runCatching {
                pm.queryIntentActivities(probe, PackageManager.MATCH_ALL) +
                    pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
            }.getOrDefault(emptyList())
            val forwarder = resolvedList.firstOrNull { ri ->
                val actPkg = ri.activityInfo?.packageName.orEmpty()
                val actName = ri.activityInfo?.name.orEmpty()
                actPkg.equals(pkg, ignoreCase = true) ||
                    actName.contains("IntentForwarder", ignoreCase = true) ||
                    actName.contains("CrossProfile", ignoreCase = true)
            }
            if (forwarder?.activityInfo != null) {
                return Intent(Intent.ACTION_VIEW, candidateUri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    component = ComponentName(
                        forwarder.activityInfo.packageName,
                        forwarder.activityInfo.name
                    )
                }
            }
        }

        // 4. Personal Profile main launch intent
        return runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()
    }

    private fun copyDestinationToClipboard(context: Context, destination: NavLocationSuggestion) {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("Ride Destination", destination.fullQuery))
        }
    }

    /**
     * Launches a single ride-share app (Uber or Lyft) with destination deep link, supporting both
     * Personal Profile (`UserHandle{0}`) and Work Profile (`UserHandle{10}`), optionally inside
     * [tiledBounds] (`WINDOWING_MODE_FREEFORM = 5`).
     */
    fun launchSingleRideApp(
        context: Context,
        isUber: Boolean,
        destination: NavLocationSuggestion,
        tiledBounds: Rect? = null,
        adjacentFlag: Boolean = false
    ): Boolean {
        val pkg = if (isUber) UBER_PACKAGE else LYFT_PACKAGE
        val deepLinkUri = Uri.parse(
            if (isUber) {
                buildUberDeepLinkUri(
                    destinationName = destination.title,
                    formattedAddress = destination.fullQuery,
                    latitude = destination.latitude,
                    longitude = destination.longitude
                )
            } else {
                buildLyftDeepLinkUri(
                    formattedAddress = destination.fullQuery,
                    latitude = destination.latitude,
                    longitude = destination.longitude
                )
            }
        )
        val universalWebUri = Uri.parse(
            if (isUber) {
                buildUberWebFallbackUri(
                    destinationName = destination.title,
                    formattedAddress = destination.fullQuery,
                    latitude = destination.latitude,
                    longitude = destination.longitude
                )
            } else {
                buildLyftWebFallbackUri(
                    formattedAddress = destination.fullQuery,
                    latitude = destination.latitude,
                    longitude = destination.longitude
                )
            }
        )

        val optionsBundle = tiledBounds?.let { buildTiledFreeformOptionsBundle(it) }
        val baseFlags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            if (adjacentFlag || tiledBounds != null) {
                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            } else {
                0
            }

        // 1. Try resolved deep-link / CrossProfileIntentForwarder Intent first
        val resolvedIntent = resolveBestRideIntent(context, pkg, deepLinkUri, universalWebUri)
        if (resolvedIntent != null) {
            resolvedIntent.addFlags(baseFlags)
            val started = runCatching {
                if (optionsBundle != null) {
                    context.startActivity(resolvedIntent, optionsBundle)
                } else {
                    context.startActivity(resolvedIntent)
                }
                true
            }.getOrElse {
                runCatching {
                    context.startActivity(resolvedIntent)
                    true
                }.getOrDefault(false)
            }
            if (started) return true
        }

        // 2. Check if installed in Work Profile (UserHandle{10}) or Personal Profile via LauncherApps
        val profileMatch = findPackageAcrossProfiles(context, pkg)
        if (profileMatch != null) {
            val (component, userHandle) = profileMatch
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            if (launcherApps != null) {
                val startedProfileApp = runCatching {
                    launcherApps.startMainActivity(component, userHandle, tiledBounds, optionsBundle)
                    true
                }.getOrElse {
                    runCatching {
                        launcherApps.startMainActivity(component, userHandle, tiledBounds, null)
                        true
                    }.getOrDefault(false)
                }
                if (startedProfileApp) return true
            }
        }

        // 3. Fallback to Universal Web URL
        val webFallbackIntent = Intent(Intent.ACTION_VIEW, universalWebUri).apply {
            addFlags(baseFlags)
        }
        return runCatching {
            if (optionsBundle != null) {
                context.startActivity(webFallbackIntent, optionsBundle)
            } else {
                context.startActivity(webFallbackIntent)
            }
            true
        }.getOrElse {
            runCatching {
                context.startActivity(webFallbackIntent)
                true
            }.getOrDefault(false)
        }
    }

    /**
     * Launches Uber (`com.ubercab`) and Lyft (`me.lyft.android`) side-by-side on Samsung One UI
     * with full Work Profile (`UserHandle{10}`) + Personal Profile (`UserHandle{0}`) support.
     *
     * Verified against Samsung One UI `WindowManagerShell` / `StageCoordinator` (`ShellSplitScreen`)
     * and `WindowOrganizerController`:
     * 1. `FLAG_ACTIVITY_LAUNCH_ADJACENT` from a Home launcher or Service context is ignored by
     *    `WindowManager` (`W/WindowManager: Launch adjacent ignored due to missing source activity`).
     * 2. Passing `setLaunchBounds(Rect)` or `windowingMode = 5/6` puts a task into non-fullscreen
     *    mode, which causes `WindowOrganizerController.reparentChildrenTasksHierarchyOp`
     *    (`mWindowingMode=[1, 0] mActivityType=[1]`) to skip it when entering split-screen!
     * 3. When `GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN` (`StageCoordinator.moveToStage`) is invoked, One UI
     *    executes an atomic `WindowContainerTransaction` (`CHANGE_TRANSIT_REQUEST_FULLSCREEN_TO_SPLIT`):
     *    - `{reparent: <focused top task> to top of Task{name=side}}` (docks the focused top task into `SideStage`),
     *    - `{childrenTasksReparent: from=null to=Task{name=main} mToTop=true mReparentTopOnly=true mWindowingMode=[1, 0] mActivityType=[1] mClearWindowingMode=true}`
     *      (automatically reparents the top remaining `mWindowingMode=[1, 0] mActivityType=[1]` task in
     *      `DefaultTaskDisplayArea` into `MainStage`).
     * 4. Therefore, we:
     *    - Launch **Uber** at `0ms` as a pure fullscreen standard task (`tiledBounds = null`, `adjacentFlag = false`,
     *      `windowingMode = 1`, `activityType = 1`),
     *    - Launch **Lyft** at `+550ms` via `FoldAccessibilityService.instance ?: context` (`BAL_ALLOW_ACCESSIBILITY`)
     *      as a pure fullscreen standard task (`tiledBounds = null`, `adjacentFlag = false`) directly above Uber
     *      so the top two `mWindowingMode=1, mActivityType=1` tasks in `DefaultTaskDisplayArea` are
     *      `[0: Lyft (top), 1: Uber (second)]`,
     *    - Trigger `FoldAccessibilityService.instance?.toggleSplitScreen()` once at `+1250ms` after Lyft's
     *      `OPEN` transition completes, atomically docking **Uber into `MainStage` (top/left)** and
     *      **Lyft into `SideStage` (bottom/right)**!
     */
    fun launchUberAndLyftSplitScreen(
        context: Context,
        destination: NavLocationSuggestion
    ) {
        SystemTweaker.ensureAccessibilityServiceEnabled(context)
        copyDestinationToClipboard(context, destination)

        val mainHandler = Handler(Looper.getMainLooper())
        val launcherPkg = context.packageName

        // Step 1 (0ms): Launch Uber as a pure fullscreen standard task (mWindowingMode=1, mActivityType=1)
        launchSingleRideApp(
            context = context,
            isUber = true,
            destination = destination,
            tiledBounds = null,
            adjacentFlag = false
        )

        // Step 2 (+550ms): Launch Lyft as a pure fullscreen standard task directly above Uber
        // using FoldAccessibilityService context (when bound) for BAL_ALLOW_ACCESSIBILITY exemption.
        mainHandler.postDelayed({
            val launchContext = FoldAccessibilityService.instance ?: context
            launchSingleRideApp(
                context = launchContext,
                isUber = false,
                destination = destination,
                tiledBounds = null,
                adjacentFlag = false
            )

            // Step 3 (+1250ms total): Once Lyft's OPEN transition has settled in the foreground,
            // invoke GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN once. StageCoordinator moves Lyft -> SideStage
            // and WindowOrganizerController.reparentChildrenTasksHierarchyOp moves Uber -> MainStage!
            fun attemptAtomicSplitDock(attempt: Int) {
                val fgPkg = FoldAccessibilityService.foregroundPackage.value
                val rideInForeground = fgPkg.isNotBlank() && fgPkg != launcherPkg
                if (!rideInForeground && attempt < 4) {
                    mainHandler.postDelayed({ attemptAtomicSplitDock(attempt + 1) }, 160L)
                    return
                }
                FoldAccessibilityService.instance?.toggleSplitScreen()
            }

            mainHandler.postDelayed({ attemptAtomicSplitDock(0) }, 700L)
        }, 550L)
    }
}

/**
 * Top-of-Home-Screen Quick Navigation & Ride Compare Bar (`v0.10.1` 200-PPI 64-Color `RGB222` MIP Edition):
 * - Authentic `~200 PPI` Memory-In-Pixel (MIP) hardware rendering:
 *   - Strict 6-bit `RGB222` 64-color palette (`#000000`, `#005555`, `#555555`, `#AAAAAA`, `#FFFFFF`, `#00FFFF`, `#00FF55`, `#FFAA00`),
 *   - Coarse `3.dp` MIP LCD pixel cell matrix + 2x2 Bayer checkerboard dither header,
 *   - Stepped pixel-art chamfered corners (`CutCornerShape(2.dp)`), zero smooth gradients.
 * - Upgraded 1-Tap Interaction:
 *   - Always-accessible 5-mode hardware selector bar (`BUS`, `BIK`, `WLK`, `DRV`, `RDE:U+L`) + left mode button,
 *   - Nearby-biased live autocomplete sorted by distance in miles (`1.8MI`),
 *   - Dropdown remains open until explicitly dismissed or launched (no focus-loss race condition),
 *   - 1-tap launch into Google Maps turn-by-turn or Uber + Lyft side-by-side split-screen!
 */
@Composable
fun OneUiQuickNavigationBar(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var selectedMode by remember { mutableStateOf(QuickNavigationLauncher.getSavedMode(context)) }
    var modeMenuExpanded by remember { mutableStateOf(false) }
    var destinationText by remember { mutableStateOf("") }
    var isInputFocused by remember { mutableStateOf(false) }
    var dropdownOpen by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<NavLocationSuggestion>>(emptyList()) }
    var selectedSuggestion by remember { mutableStateOf<NavLocationSuggestion?>(null) }
    var statusBannerText by remember { mutableStateOf<String?>(null) }

    val activeAccent = when (selectedMode) {
        QuickNavMode.RIDE -> MipAmber
        QuickNavMode.BUS, QuickNavMode.BIKE, QuickNavMode.WALK -> MipGreen
        QuickNavMode.DRIVE -> MipCyan
    }
    val mipShape = CutCornerShape(2.dp)

    // Pre-warm user coordinates on launch so first search is instant
    LaunchedEffect(context) {
        withContext(Dispatchers.IO) {
            QuickNavigationLauncher.resolveUserCoordinates(context)
        }
    }

    LaunchedEffect(statusBannerText) {
        if (statusBannerText != null) {
            delay(2800L)
            statusBannerText = null
        }
    }

    // Debounced nearby-biased location autocomplete
    LaunchedEffect(destinationText, dropdownOpen) {
        if (!dropdownOpen && destinationText.isBlank()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        if (destinationText.isNotBlank()) {
            delay(150L)
        }
        suggestions = QuickNavigationLauncher.fetchSuggestions(
            context = context,
            query = destinationText,
            maxResults = 5
        )
    }

    fun resolveCurrentDestination(targetSuggestion: NavLocationSuggestion? = null): NavLocationSuggestion? {
        val raw = targetSuggestion?.fullQuery ?: destinationText.trim()
        if (raw.isBlank()) return null
        val topNearbyMatch = suggestions.firstOrNull { !it.isRecent } ?: suggestions.firstOrNull()
        return targetSuggestion
            ?: selectedSuggestion?.takeIf {
                it.title.equals(raw, ignoreCase = true) || it.fullQuery.equals(raw, ignoreCase = true)
            }
            ?: topNearbyMatch?.takeIf { destinationText.trim().length >= 2 }
            ?: NavLocationSuggestion(
                title = raw.substringBefore(",").trim(),
                subtitle = raw.substringAfter(",", "").trim(),
                fullQuery = raw
            )
    }

    fun triggerLaunch(mode: QuickNavMode, targetSuggestion: NavLocationSuggestion? = null) {
        val dest = resolveCurrentDestination(targetSuggestion) ?: return
        selectedSuggestion = dest
        destinationText = dest.title
        modeMenuExpanded = false
        dropdownOpen = false
        isInputFocused = false
        keyboardController?.hide()
        focusManager.clearFocus()
        statusBannerText = if (mode == QuickNavMode.RIDE) {
            "TILED UBER+LYFT >> ${dest.title.uppercase(Locale.US)}"
        } else {
            "NAV [${mode.icon}] >> ${dest.title.uppercase(Locale.US)}"
        }
        coroutineScope.launch {
            QuickNavigationLauncher.executeNavigation(context, mode, dest)
        }
    }

    fun triggerSingleRideLaunch(isUber: Boolean, targetSuggestion: NavLocationSuggestion? = null) {
        val dest = resolveCurrentDestination(targetSuggestion) ?: return
        selectedSuggestion = dest
        destinationText = dest.title
        modeMenuExpanded = false
        dropdownOpen = false
        isInputFocused = false
        keyboardController?.hide()
        focusManager.clearFocus()
        statusBannerText = "${if (isUber) "UBER" else "LYFT"} >> ${dest.title.uppercase(Locale.US)}"
        coroutineScope.launch {
            QuickNavigationLauncher.executeSingleRide(context, isUber, dest)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Outer 200-PPI 64-Color RGB222 MIP Terminal Frame
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(mipShape)
                .background(MipBlack)
                .mip200PpiMatrix(ditherColor = MipDarkBlue, enableCheckerboardDither = false)
                .border(2.dp, if (isInputFocused || dropdownOpen) activeAccent else MipDarkGray, mipShape)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Row 1: 2x2 Bayer-Dithered MIP Header Bar + Always-Visible 1-Tap 5-Mode Hardware Key Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MipBlack)
                    .mip200PpiMatrix(ditherColor = MipDarkTeal, enableCheckerboardDither = true)
                    .border(1.dp, MipDarkGray, mipShape)
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (mode in QuickNavMode.values()) {
                    val isSelected = mode == selectedMode
                    val modeColor = when (mode) {
                        QuickNavMode.RIDE -> MipAmber
                        QuickNavMode.DRIVE -> MipCyan
                        else -> MipGreen
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(mipShape)
                            .background(if (isSelected) modeColor else MipBlack)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) MipWhite else MipDarkGray,
                                shape = mipShape
                            )
                            .clickable {
                                val wasSelected = selectedMode == mode
                                selectedMode = mode
                                QuickNavigationLauncher.saveSelectedMode(context, mode)
                                if (destinationText.isNotBlank() && (wasSelected || selectedSuggestion != null)) {
                                    triggerLaunch(mode, selectedSuggestion)
                                }
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (mode == QuickNavMode.RIDE) "RDE:U+L" else mode.icon,
                            color = if (isSelected) MipBlack else MipSilver,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
            }

            // Row 2: Left Expand/Cycle Button + MIP Command Input Field + [CLR] + [GO / U+L]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left button: expands mode details or cycles mode
                Box(
                    modifier = Modifier
                        .clip(mipShape)
                        .background(MipDarkBlue)
                        .border(1.dp, activeAccent, mipShape)
                        .clickable {
                            modeMenuExpanded = !modeMenuExpanded
                            dropdownOpen = true
                        }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${selectedMode.icon}${if (modeMenuExpanded) "^" else "v"}",
                        color = activeAccent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = ">",
                    color = activeAccent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.width(5.dp))

                // Center Target Location Textbox
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (destinationText.isEmpty()) {
                        Text(
                            text = if (selectedMode == QuickNavMode.RIDE) {
                                "DEST FOR UBER+LYFT..."
                            } else {
                                "SEARCH NEARBY DEST..."
                            },
                            color = MipSilver,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = destinationText,
                        onValueChange = { updated ->
                            destinationText = updated
                            selectedSuggestion = null
                            dropdownOpen = true
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MipWhite,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        cursorBrush = SolidColor(activeAccent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = { triggerLaunch(selectedMode) }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state ->
                                isInputFocused = state.isFocused
                                if (state.isFocused) {
                                    dropdownOpen = true
                                }
                            }
                    )
                }

                // Clear / Close button when text or dropdown is active
                if (destinationText.isNotEmpty() || dropdownOpen) {
                    Box(
                        modifier = Modifier
                            .clip(mipShape)
                            .background(MipBlack)
                            .border(1.dp, MipDarkGray, mipShape)
                            .clickable {
                                if (destinationText.isNotEmpty()) {
                                    destinationText = ""
                                    selectedSuggestion = null
                                } else {
                                    dropdownOpen = false
                                    modeMenuExpanded = false
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CLR",
                            color = MipRed,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Right Launch Button ([GO] or [U+L])
                val canLaunch = destinationText.isNotBlank()
                Box(
                    modifier = Modifier
                        .clip(mipShape)
                        .background(if (canLaunch) activeAccent else MipBlack)
                        .border(
                            width = 1.dp,
                            color = if (canLaunch) MipWhite else MipDarkGray,
                            shape = mipShape
                        )
                        .clickable(enabled = canLaunch) {
                            triggerLaunch(selectedMode)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedMode == QuickNavMode.RIDE) "U+L" else "GO>",
                        color = if (canLaunch) MipBlack else MipDarkGray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Persistent In-Bar Ride Quick-Switch Strip ([UBER: <dest>] [LYFT: <dest>] [TILE BOTH])
            // Visible whenever RIDE mode is selected or a destination is active so the user can 1-tap
            // jump directly to Uber, Lyft, or Tiled Multi-Window Both at any time!
            val activeDestTitle = (selectedSuggestion?.title ?: destinationText.trim()).uppercase(Locale.US)
            if (activeDestTitle.isNotBlank() || selectedMode == QuickNavMode.RIDE) {
                val shortDest = if (activeDestTitle.length > 9) {
                    activeDestTitle.take(8) + ".."
                } else if (activeDestTitle.isNotEmpty()) {
                    activeDestTitle
                } else {
                    "DEST"
                }
                val rideReady = destinationText.isNotBlank() || selectedSuggestion != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1-Tap Direct Uber Button (supports Work Profile User 10 + Personal Profile)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(mipShape)
                            .background(if (rideReady) MipDarkBlue else MipBlack)
                            .border(1.dp, if (rideReady) MipCyan else MipDarkGray, mipShape)
                            .clickable(enabled = rideReady) {
                                triggerSingleRideLaunch(isUber = true)
                            }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[UBER:$shortDest]",
                            color = if (rideReady) MipCyan else MipDarkGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 1-Tap Direct Lyft Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(mipShape)
                            .background(if (rideReady) MipDarkBlue else MipBlack)
                            .border(1.dp, if (rideReady) MipGreen else MipDarkGray, mipShape)
                            .clickable(enabled = rideReady) {
                                triggerSingleRideLaunch(isUber = false)
                            }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[LYFT:$shortDest]",
                            color = if (rideReady) MipGreen else MipDarkGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 1-Tap Tiled Multi-Window Both Button (Top/Bottom or Left/Right Freeform)
                    Box(
                        modifier = Modifier
                            .clip(mipShape)
                            .background(if (rideReady) MipAmber else MipBlack)
                            .border(1.dp, if (rideReady) MipWhite else MipDarkGray, mipShape)
                            .clickable(enabled = rideReady) {
                                selectedMode = QuickNavMode.RIDE
                                QuickNavigationLauncher.saveSelectedMode(context, QuickNavMode.RIDE)
                                triggerLaunch(QuickNavMode.RIDE)
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[TILE BOTH]",
                            color = if (rideReady) MipBlack else MipDarkGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
            }

            // Optional Status Banner when launching
            if (statusBannerText != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MipDarkTeal)
                        .border(1.dp, activeAccent, mipShape)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusBannerText ?: "",
                        color = MipWhite,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Expandable Mode Description / Quick-Action Strip when left button is tapped
        AnimatedVisibility(
            visible = modeMenuExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp)
                    .clip(mipShape)
                    .background(MipBlack)
                    .mip200PpiMatrix()
                    .border(2.dp, activeAccent, mipShape)
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (mode in QuickNavMode.values()) {
                    val isSelected = mode == selectedMode
                    val modeColor = if (mode == QuickNavMode.RIDE) MipAmber else MipCyan
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) MipDarkTeal else MipBlack)
                            .border(1.dp, if (isSelected) modeColor else MipDarkGray, mipShape)
                            .clickable {
                                selectedMode = mode
                                QuickNavigationLauncher.saveSelectedMode(context, mode)
                                modeMenuExpanded = false
                                if (destinationText.isNotBlank()) {
                                    triggerLaunch(mode)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "[${mode.icon}] ${mode.label.uppercase(Locale.US)}",
                            color = if (isSelected) modeColor else MipWhite,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (mode == QuickNavMode.RIDE) "SPLIT TOP+BOT" else "GMAPS ROUTE",
                            color = MipSilver,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Nearby-Sorted Suggestions Dropdown (200-PPI 64-Color RGB222 MIP Matrix)
        AnimatedVisibility(
            visible = (dropdownOpen || isInputFocused) && suggestions.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp)
                    .clip(mipShape)
                    .background(MipBlack)
                    .mip200PpiMatrix()
                    .border(2.dp, activeAccent, mipShape)
            ) {
                // Dropdown MIP header showing GPS/nearby bias status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MipDarkBlue)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (destinationText.isBlank()) "RECENT DESTINATIONS" else "NEARBY MATCHES (TAP TO GO)",
                        color = MipCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "[HIDE]",
                        color = MipSilver,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clickable {
                            dropdownOpen = false
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = MipDarkGray)

                suggestions.forEachIndexed { index, suggestion ->
                    val distBadge = suggestion.formattedDistanceBadge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                triggerLaunch(selectedMode, suggestion)
                            }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Distance / History Badge in 6-bit RGB222 color
                        Box(
                            modifier = Modifier
                                .clip(mipShape)
                                .background(if (suggestion.isRecent) MipDarkBlue else MipDarkTeal)
                                .border(
                                    1.dp,
                                    if (suggestion.isRecent) MipAmber else MipGreen,
                                    mipShape
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when {
                                    suggestion.isRecent && distBadge != null -> distBadge
                                    suggestion.isRecent -> "HST"
                                    distBadge != null -> distBadge
                                    else -> "LOC"
                                },
                                color = if (suggestion.isRecent) MipAmber else MipGreen,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Spacer(modifier = Modifier.width(7.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = suggestion.title.uppercase(Locale.US),
                                color = MipWhite,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (suggestion.subtitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = suggestion.subtitle.uppercase(Locale.US),
                                    color = MipSilver,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // 1-Tap Direct Ride Split Button [U+L] right on every suggestion row
                        if (selectedMode != QuickNavMode.RIDE) {
                            Box(
                                modifier = Modifier
                                    .clip(mipShape)
                                    .background(MipBlack)
                                    .border(1.dp, MipAmber, mipShape)
                                    .clickable {
                                        selectedMode = QuickNavMode.RIDE
                                        QuickNavigationLauncher.saveSelectedMode(context, QuickNavMode.RIDE)
                                        triggerLaunch(QuickNavMode.RIDE, suggestion)
                                    }
                                    .padding(horizontal = 5.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "U+L",
                                    color = MipAmber,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        // Primary Mode Launch Badge
                        Box(
                            modifier = Modifier
                                .clip(mipShape)
                                .background(activeAccent)
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (selectedMode == QuickNavMode.RIDE) "U+L>" else "${selectedMode.icon}>",
                                color = MipBlack,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    if (index < suggestions.lastIndex) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MipDarkGray
                        )
                    }
                }
            }
        }
    }
}
