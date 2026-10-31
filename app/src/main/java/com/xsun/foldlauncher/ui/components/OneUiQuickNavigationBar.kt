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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
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
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Five quick-navigation travel modes selectable from [OneUiQuickNavigationBar]:
 * - [BUS], [BIKE], [WALK], [DRIVE]: launch Google Maps directly with the route/navigation started.
 * - [RIDE]: launches Lyft (`me.lyft.android`) and Uber (`com.ubercab`) side-by-side in split-screen
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
    BUS("bus", "Bus", "🚌", "🚌 Bus", "r", "transit"),
    BIKE("bike", "Bike", "🚲", "🚲 Bike", "b", "bicycling"),
    WALK("walk", "Walk", "🚶", "🚶 Walk", "w", "walking"),
    DRIVE("drive", "Drive", "🚗", "🚗 Drive", "d", "driving"),
    RIDE("ride", "Ride (Uber + Lyft)", "🚕", "🚕 Ride", null, null);

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
                String.format(Locale.US, "%.1f mi", d)
            } else {
                String.format(Locale.US, "%.0f mi", d)
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
     * always biases and sorts by proximity to the user's real location instead of East Coast / global matches.
     */
    @SuppressLint("MissingPermission")
    fun resolveUserCoordinates(context: Context): Pair<Double, Double> {
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

        val now = System.currentTimeMillis()
        val mem = memoryCachedCoords
        if (mem != null && (now - memoryCachedCoordsTimeMs) < 30 * 60 * 1000L) {
            return mem
        }

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

        if (Looper.myLooper() != Looper.getMainLooper()) {
            runCatching {
                val url = URL("http://ip-api.com/json/?fields=status,lat,lon")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 900
                    readTimeout = 900
                    setRequestProperty("User-Agent", "Fold8Launcher/0.14.0")
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
     * Builds the Uber deep link that pre-fills pickup = `my_location`, sets dropoff coordinates,
     * nickname, and formatted address, and opens directly to the ride options & price estimate sheet.
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
        return "uber://?client_id=fold8_launcher&action=setPickup&pickup=my_location$coordPart&dropoff[nickname]=$encName&dropoff[formatted_address]=$encAddr"
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
        return "https://m.uber.com/ul/?client_id=fold8_launcher&action=setPickup&pickup=my_location$coordPart&dropoff[nickname]=$encName&dropoff[formatted_address]=$encAddr"
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
     * Queries live nearby location suggestions for [query] biased to the user's current coordinates.
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

        val photonItems = queryPhotonAutocomplete(
            query = trimmed,
            maxResults = (maxResults * 2).coerceAtLeast(8),
            userLat = userLat,
            userLon = userLon
        )
        candidates.addAll(photonItems)

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
                setRequestProperty("User-Agent", "Fold8Launcher/0.14.0")
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
     * - `RIDE`: launches **Lyft and Uber side-by-side in split-screen** with the destination set and price estimate open!
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

    private fun buildTiledFreeformOptionsBundle(bounds: Rect): Bundle? {
        return runCatching {
            ActivityOptions.makeBasic().setLaunchBounds(bounds).toBundle()
        }.getOrNull()
    }

    private fun findPackageAcrossProfiles(
        context: Context,
        packageName: String
    ): Pair<ComponentName, UserHandle>? {
        return runCatching {
            val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
            val userManager = context.getSystemService(UserManager::class.java)
            val myUser = Process.myUserHandle()
            val profiles = userManager?.userProfiles ?: listOf(myUser)
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

    private fun resolveBestRideIntent(
        context: Context,
        pkg: String,
        deepLinkUri: Uri,
        universalWebUri: Uri
    ): Intent? {
        val pm = context.packageManager

        val directSchemeIntent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
            setPackage(pkg)
        }
        if (runCatching { pm.resolveActivity(directSchemeIntent, 0) }.getOrNull() != null) {
            return directSchemeIntent
        }

        val directWebIntent = Intent(Intent.ACTION_VIEW, universalWebUri).apply {
            setPackage(pkg)
        }
        if (runCatching { pm.resolveActivity(directWebIntent, 0) }.getOrNull() != null) {
            return directWebIntent
        }

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

        return runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()
    }

    private fun copyDestinationToClipboard(context: Context, destination: NavLocationSuggestion) {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("Ride Destination", destination.fullQuery))
        }
    }

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
     * Launches Lyft (`me.lyft.android`) and Uber (`com.ubercab`) side-by-side on Samsung One UI
     * with full Work Profile (`UserHandle{10}`) + Personal Profile (`UserHandle{0}`) support.
     */
    fun launchUberAndLyftSplitScreen(
        context: Context,
        destination: NavLocationSuggestion
    ) {
        SystemTweaker.ensureAccessibilityServiceEnabled(context)
        copyDestinationToClipboard(context, destination)

        val mainHandler = Handler(Looper.getMainLooper())
        val launcherPkg = context.packageName

        // Step 1 (0ms): Launch Lyft FIRST as a pure fullscreen standard task (mWindowingMode=1, mActivityType=1)
        launchSingleRideApp(
            context = context,
            isUber = false,
            destination = destination,
            tiledBounds = null,
            adjacentFlag = false
        )

        // Step 2 (+1050ms): Launch Uber SECOND directly above Lyft so Uber stays in the foreground
        // while its deep link initializes and opens the destination price estimate sheet.
        mainHandler.postDelayed({
            val launchContext = FoldAccessibilityService.instance ?: context
            launchSingleRideApp(
                context = launchContext,
                isUber = true,
                destination = destination,
                tiledBounds = null,
                adjacentFlag = false
            )

            // Step 3 (+2250ms, +2950ms, +3650ms total): Dock Uber -> SideStage and Lyft -> MainStage
            // via FoldAccessibilityService.ensureSplitScreenActive().
            val checkDelaysMs = longArrayOf(1200L, 1900L, 2600L)
            for (delayMs in checkDelaysMs) {
                mainHandler.postDelayed({
                    val svc = FoldAccessibilityService.instance ?: return@postDelayed
                    if (svc.isSplitScreenActive()) return@postDelayed
                    val fgPkg = FoldAccessibilityService.foregroundPackage.value
                    if (fgPkg != launcherPkg) {
                        svc.ensureSplitScreenActive()
                    }
                }, delayMs)
            }
        }, 1050L)
    }
}

/**
 * Top-of-Home-Screen Quick Navigation & Ride Compare Bar (Clean Samsung One UI Edition):
 * - Frosted One UI rounded search pill with left mode selector (`🚌 Bus`, `🚲 Bike`, `🚶 Walk`, `🚗 Drive`, `🚕 Ride`),
 * - Nearby-biased live autocomplete sorted by distance in miles (`1.8 mi`),
 * - 1-tap launch into Google Maps turn-by-turn navigation or Lyft + Uber side-by-side split-screen.
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

    LaunchedEffect(context) {
        withContext(Dispatchers.IO) {
            QuickNavigationLauncher.resolveUserCoordinates(context)
        }
    }

    LaunchedEffect(destinationText, dropdownOpen, isInputFocused) {
        if (!dropdownOpen && !isInputFocused && destinationText.isBlank()) {
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
        coroutineScope.launch {
            QuickNavigationLauncher.executeSingleRide(context, isUber, dest)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Main One UI Rounded Navigation Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xD91A1B20))
                .border(
                    width = 1.dp,
                    color = if (isInputFocused || dropdownOpen) {
                        Color(0xFF38BDF8).copy(alpha = 0.65f)
                    } else {
                        Color.White.copy(alpha = 0.14f)
                    },
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left expandable Mode Selector Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selectedMode == QuickNavMode.RIDE) {
                            Color(0xFF7C3AED).copy(alpha = 0.28f)
                        } else {
                            Color(0xFF0381FE).copy(alpha = 0.24f)
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (selectedMode == QuickNavMode.RIDE) {
                            Color(0xFFC084FC).copy(alpha = 0.55f)
                        } else {
                            Color(0xFF38BDF8).copy(alpha = 0.45f)
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        modeMenuExpanded = !modeMenuExpanded
                        dropdownOpen = true
                    }
                    .padding(horizontal = 11.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedMode.shortBadge,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (modeMenuExpanded) "▴" else "▾",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

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
                            "Uber + Lyft destination..."
                        } else {
                            "Navigate to destination..."
                        },
                        color = Color.White.copy(alpha = 0.46f),
                        fontSize = 14.sp,
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
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(Color(0xFF38BDF8)),
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

            // Clear button when text is present
            if (destinationText.isNotEmpty() || dropdownOpen) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
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
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Right Go / Launch button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        if (destinationText.isNotBlank()) {
                            if (selectedMode == QuickNavMode.RIDE) Color(0xFF8B5CF6) else Color(0xFF0381FE)
                        } else {
                            Color.White.copy(alpha = 0.08f)
                        }
                    )
                    .clickable(enabled = destinationText.isNotBlank()) {
                        triggerLaunch(selectedMode)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selectedMode == QuickNavMode.RIDE) "Compare ↗" else "Go ↗",
                    color = if (destinationText.isNotBlank()) Color.White else Color.White.copy(alpha = 0.4f),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Expandable 5-Mode Selector Strip (Bus, Bike, Walk, Drive, Ride)
        AnimatedVisibility(
            visible = modeMenuExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xF017181E))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (mode in QuickNavMode.values()) {
                        val isSelected = mode == selectedMode
                        val chipBg = when {
                            isSelected && mode == QuickNavMode.RIDE -> Color(0xFF8B5CF6).copy(alpha = 0.32f)
                            isSelected -> Color(0xFF0381FE).copy(alpha = 0.30f)
                            else -> Color.White.copy(alpha = 0.06f)
                        }
                        val chipBorder = when {
                            isSelected && mode == QuickNavMode.RIDE -> Color(0xFFC084FC)
                            isSelected -> Color(0xFF38BDF8)
                            else -> Color.White.copy(alpha = 0.10f)
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(chipBg)
                                .border(1.dp, chipBorder, RoundedCornerShape(14.dp))
                                .clickable {
                                    selectedMode = mode
                                    QuickNavigationLauncher.saveSelectedMode(context, mode)
                                    modeMenuExpanded = false
                                    if (destinationText.isNotBlank()) {
                                        triggerLaunch(mode)
                                    }
                                }
                                .padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = mode.icon,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = mode.label,
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                // Direct Uber / Lyft / Both split buttons when Ride mode is active or a destination is typed
                val rideReady = destinationText.isNotBlank() || selectedSuggestion != null
                if (selectedMode == QuickNavMode.RIDE || rideReady) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = if (rideReady) 0.10f else 0.04f))
                                .clickable(enabled = rideReady) { triggerSingleRideLaunch(isUber = true) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Uber Only ↗",
                                color = Color.White.copy(alpha = if (rideReady) 0.9f else 0.38f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = if (rideReady) 0.10f else 0.04f))
                                .clickable(enabled = rideReady) { triggerSingleRideLaunch(isUber = false) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Lyft Only ↗",
                                color = Color.White.copy(alpha = if (rideReady) 0.9f else 0.38f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Subtle One UI Accessibility setup pill only when RIDE mode is selected and Accessibility is off
        val a11yActive = FoldAccessibilityService.instance != null ||
            SystemTweaker.isAccessibilityServiceEnabled(context)
        if (selectedMode == QuickNavMode.RIDE && !a11yActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xD9231F32))
                    .border(1.dp, Color(0xFFC084FC).copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .clickable {
                        if (!SystemTweaker.ensureAccessibilityServiceEnabled(context)) {
                            SystemTweaker.openAccessibilitySettings(context)
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enable Fold8 Companion in Accessibility for automatic split-screen",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Enable ↗",
                    color = Color(0xFFC084FC),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Suggested Locations Dropdown (Nearby-sorted with distance badge)
        AnimatedVisibility(
            visible = (dropdownOpen || isInputFocused) && suggestions.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xF217181E))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                    .padding(vertical = 4.dp)
            ) {
                suggestions.forEachIndexed { index, suggestion ->
                    val distBadge = suggestion.formattedDistanceBadge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                triggerLaunch(selectedMode, suggestion)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (suggestion.isRecent) "🕘" else "📍",
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = suggestion.title,
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (distBadge != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• $distBadge",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            if (suggestion.subtitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = suggestion.subtitle,
                                    color = Color.White.copy(alpha = 0.58f),
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedMode == QuickNavMode.RIDE) "Uber+Lyft ↗" else "${selectedMode.icon} Go",
                            color = if (selectedMode == QuickNavMode.RIDE) {
                                Color(0xFFC084FC)
                            } else {
                                Color(0xFF38BDF8)
                            },
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (index < suggestions.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = 0.5.dp,
                            color = Color.White.copy(alpha = 0.07f)
                        )
                    }
                }
            }
        }
    }
}
