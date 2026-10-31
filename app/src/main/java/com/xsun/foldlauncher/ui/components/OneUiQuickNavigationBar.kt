package com.xsun.foldlauncher.ui.components

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
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

/**
 * Five quick-navigation travel modes selectable from the left expandable button in [OneUiQuickNavigationBar]:
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
    BUS("bus", "Bus", "🚌", "🚌 Bus", "r", "transit"),
    BIKE("bike", "Bike", "🚲", "🚲 Bike", "b", "bicycling"),
    WALK("walk", "Walk", "🚶", "🚶 Walk", "w", "walking"),
    DRIVE("drive", "Drive", "🚗", "🚗 Drive", "d", "driving"),
    RIDE("ride", "Ride (Uber + Lyft)", "🚕", "🚕 Ride", null, null);

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
 * Autocomplete destination item with optional coordinates (`latitude`, `longitude`) so both
 * Google Maps turn-by-turn routes and Uber + Lyft price estimate deep links resolve accurately.
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
    val isRecent: Boolean = false
)

object QuickNavigationLauncher {
    const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
    const val UBER_PACKAGE = "com.ubercab"
    const val LYFT_PACKAGE = "me.lyft.android"

    private const val PREFS_NAME = "fold8_quick_nav_prefs"
    private const val KEY_SELECTED_MODE = "quick_nav_selected_mode"
    private const val KEY_RECENT_PLACES = "quick_nav_recent_places"

    private fun urlEncode(value: String): String {
        return try {
            URLEncoder.encode(value.trim(), "UTF-8").replace("+", "%20")
        } catch (_: Exception) {
            value.trim().replace(" ", "%20")
        }
    }

    /**
     * Builds the primary Google Maps URI to start navigation/route immediately for [mode].
     * - For `DRIVE` (`d`), `BIKE` (`b`), and `WALK` (`w`): uses `google.navigation:q=<dest>&mode=<d|b|w>`
     *   which immediately starts turn-by-turn navigation in the Google Maps app.
     * - For `BUS` (`transit`): uses `https://www.google.com/maps/dir/?api=1&destination=<dest>&travelmode=transit&dir_action=navigate`
     *   so Google Maps opens directly into live transit route directions started.
     */
    fun buildGoogleMapsPrimaryUri(
        mode: QuickNavMode,
        destinationQuery: String,
        latitude: Double? = null,
        longitude: Double? = null
    ): String {
        val target = if (latitude != null && longitude != null && destinationQuery.isBlank()) {
            "$latitude,$longitude"
        } else {
            destinationQuery.trim()
        }
        val encoded = urlEncode(target)
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
        destinationQuery: String
    ): String {
        val encoded = urlEncode(destinationQuery.trim())
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
                it.title.equals(suggestion.title, ignoreCase = true)
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
     * Queries live location suggestions for [query] by combining:
     * 1. Matching recent destinations from SharedPreferences,
     * 2. On-device `android.location.Geocoder` (Google Play Services), and
     * 3. Photon OpenStreetMap GeoJSON autocomplete fallback (`https://photon.komoot.io/api/`).
     */
    suspend fun fetchSuggestions(
        context: Context,
        query: String,
        maxResults: Int = 5
    ): List<NavLocationSuggestion> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val recents = loadRecentDestinations(context)
        if (trimmed.length < 2) {
            return@withContext recents.take(maxResults)
        }

        val results = mutableListOf<NavLocationSuggestion>()
        for (recent in recents) {
            if (recent.title.contains(trimmed, ignoreCase = true) ||
                recent.fullQuery.contains(trimmed, ignoreCase = true)
            ) {
                results.add(recent)
            }
        }

        // 1. Native Android Geocoder lookup
        val geocoderItems = queryAndroidGeocoder(context, trimmed, maxResults)
        for (item in geocoderItems) {
            if (results.none { it.fullQuery.equals(item.fullQuery, ignoreCase = true) }) {
                results.add(item)
            }
        }

        // 2. Fallback to Photon OSM autocomplete if Geocoder returned fewer than 3 suggestions
        if (results.size < 3) {
            val photonItems = queryPhotonAutocomplete(trimmed, maxResults)
            for (item in photonItems) {
                if (results.none { it.fullQuery.equals(item.fullQuery, ignoreCase = true) }) {
                    results.add(item)
                }
            }
        }

        results.take(maxResults)
    }

    @Suppress("DEPRECATION")
    private fun queryAndroidGeocoder(
        context: Context,
        query: String,
        maxResults: Int
    ): List<NavLocationSuggestion> {
        return runCatching {
            if (!Geocoder.isPresent()) return emptyList()
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocationName(query, maxResults) ?: return emptyList()
            addresses.mapNotNull { addr ->
                val fullLine = (0..addr.maxAddressLineIndex)
                    .mapNotNull { idx -> addr.getAddressLine(idx)?.trim()?.takeIf { it.isNotEmpty() } }
                    .joinToString(", ")
                val feature = addr.featureName?.trim()
                val thoroughfare = listOfNotNull(addr.subThoroughfare, addr.thoroughfare)
                    .joinToString(" ")
                    .trim()
                val localityParts = listOfNotNull(addr.locality, addr.adminArea, addr.postalCode)
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
                    fullLine.isNotBlank() && !fullLine.equals(primaryTitle, ignoreCase = true) ->
                        fullLine.removePrefix(primaryTitle).trimStart(',', ' ').ifBlank { localityParts }
                    else -> localityParts
                }
                val lat = if (addr.hasLatitude()) addr.latitude else null
                val lon = if (addr.hasLongitude()) addr.longitude else null
                NavLocationSuggestion(
                    title = primaryTitle,
                    subtitle = subtitle,
                    fullQuery = fullLine.ifBlank {
                        if (subtitle.isNotBlank()) "$primaryTitle, $subtitle" else primaryTitle
                    },
                    latitude = lat,
                    longitude = lon
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun android.location.Address.SubThoroughfareOrEmpty(): String = subThoroughfare?.trim() ?: ""

    private fun queryPhotonAutocomplete(
        query: String,
        maxResults: Int
    ): List<NavLocationSuggestion> {
        return runCatching {
            val encoded = urlEncode(query)
            val url = URL("https://photon.komoot.io/api/?q=$encoded&limit=$maxResults&lang=en")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1500
                readTimeout = 1500
                setRequestProperty("User-Agent", "Fold8Launcher/1.0")
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
                    props.optString("state").trim().takeIf { it.isNotEmpty() },
                    props.optString("country").trim().takeIf { it.isNotEmpty() }
                ).joinToString(", ")

                val title = name.ifBlank { street }.ifBlank { continue }
                val subtitle = listOfNotNull(
                    street.takeIf { it.isNotBlank() && !it.equals(title, ignoreCase = true) },
                    cityState.takeIf { it.isNotBlank() }
                ).joinToString(", ")

                list.add(
                    NavLocationSuggestion(
                        title = title,
                        subtitle = subtitle,
                        fullQuery = if (subtitle.isNotBlank()) "$title, $subtitle" else title,
                        latitude = lat?.takeIf { !it.isNaN() },
                        longitude = lon?.takeIf { !it.isNaN() }
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    /**
     * Executes the selected [mode] for [destination]:
     * - `BUS`, `BIKE`, `WALK`, `DRIVE`: opens Google Maps with navigation / transit route started.
     * - `RIDE`: resolves coordinates if missing, then launches **Uber and Lyft side-by-side in
     *   split-screen** (`FLAG_ACTIVITY_LAUNCH_ADJACENT`) with the destination set and price estimate open!
     */
    suspend fun executeNavigation(
        context: Context,
        mode: QuickNavMode,
        destination: NavLocationSuggestion
    ) {
        val resolved = if (destination.latitude == null || destination.longitude == null) {
            withContext(Dispatchers.IO) {
                val geocoded = queryAndroidGeocoder(context, destination.fullQuery, 1).firstOrNull()
                    ?: queryPhotonAutocomplete(destination.fullQuery, 1).firstOrNull()
                if (geocoded != null && geocoded.latitude != null && geocoded.longitude != null) {
                    destination.copy(
                        latitude = geocoded.latitude,
                        longitude = geocoded.longitude,
                        fullQuery = geocoded.fullQuery.ifBlank { destination.fullQuery }
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
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val startedMaps = runCatching {
            context.startActivity(mapsIntent)
            true
        }.getOrDefault(false)

        if (!startedMaps) {
            val webUri = Uri.parse(buildGoogleMapsWebFallbackUri(mode, destination.fullQuery))
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
     * Launches Uber (`com.ubercab`) and Lyft (`me.lyft.android`) side-by-side in Android split-screen
     * (`FLAG_ACTIVITY_LAUNCH_ADJACENT`) with the dropoff destination set and price estimates open.
     */
    private fun launchUberAndLyftSplitScreen(
        context: Context,
        destination: NavLocationSuggestion
    ) {
        val pm = context.packageManager
        val isUberInstalled = runCatching {
            pm.getLaunchIntentForPackage(UBER_PACKAGE) != null
        }.getOrDefault(false)
        val isLyftInstalled = runCatching {
            pm.getLaunchIntentForPackage(LYFT_PACKAGE) != null
        }.getOrDefault(false)

        val uberUri = Uri.parse(
            if (isUberInstalled) {
                buildUberDeepLinkUri(
                    destinationName = destination.title,
                    formattedAddress = destination.fullQuery,
                    latitude = destination.latitude,
                    longitude = destination.longitude
                )
            } else {
                buildUberWebFallbackUri(
                    destinationName = destination.title,
                    formattedAddress = destination.fullQuery,
                    latitude = destination.latitude,
                    longitude = destination.longitude
                )
            }
        )

        val lyftUri = Uri.parse(
            if (isLyftInstalled) {
                buildLyftDeepLinkUri(
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

        val uberIntent = Intent(Intent.ACTION_VIEW, uberUri).apply {
            if (isUberInstalled) {
                setPackage(UBER_PACKAGE)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val lyftAdjacentIntent = Intent(Intent.ACTION_VIEW, lyftUri).apply {
            if (isLyftInstalled) {
                setPackage(LYFT_PACKAGE)
            }
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            )
        }

        val uberLaunched = runCatching {
            context.startActivity(uberIntent)
            true
        }.getOrElse {
            // Fallback to Uber Universal HTTPS URL if uber:// scheme was rejected
            runCatching {
                val fallbackIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        buildUberWebFallbackUri(
                            destinationName = destination.title,
                            formattedAddress = destination.fullQuery,
                            latitude = destination.latitude,
                            longitude = destination.longitude
                        )
                    )
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            }.getOrDefault(false)
        }

        val delayMs = if (uberLaunched) 160L else 0L
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching {
                context.startActivity(lyftAdjacentIntent)
            }.onFailure {
                runCatching {
                    val fallbackLyft = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            buildLyftWebFallbackUri(
                                formattedAddress = destination.fullQuery,
                                latitude = destination.latitude,
                                longitude = destination.longitude
                            )
                        )
                    ).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                                Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                        )
                    }
                    context.startActivity(fallbackLyft)
                }
            }
        }, delayMs)
    }
}

/**
 * Top-of-Home-Screen Quick Navigation & Ride Compare Bar (`v1.0.0`):
 * - Left button expands the 5 travel options (`🚌 Bus`, `🚲 Bike`, `🚶 Walk`, `🚗 Drive`, `🚕 Ride`).
 * - Center target location textbox shows live suggested locations in a dropdown below.
 * - Picking a suggestion or pressing `Go` starts Google Maps navigation for `Bus`/`Bike`/`Walk`/`Drive`,
 *   or opens **Uber + Lyft side-by-side** with the destination set and price estimate open for `Ride`.
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
    var suggestions by remember { mutableStateOf<List<NavLocationSuggestion>>(emptyList()) }
    var selectedSuggestion by remember { mutableStateOf<NavLocationSuggestion?>(null) }

    // Debounced live location autocomplete
    LaunchedEffect(destinationText, isInputFocused) {
        if (!isInputFocused && destinationText.isBlank()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        if (destinationText.isNotBlank()) {
            delay(180L)
        }
        suggestions = QuickNavigationLauncher.fetchSuggestions(
            context = context,
            query = destinationText,
            maxResults = 5
        )
    }

    fun triggerLaunch(mode: QuickNavMode, targetSuggestion: NavLocationSuggestion? = null) {
        val raw = targetSuggestion?.fullQuery ?: destinationText.trim()
        if (raw.isBlank()) return
        val dest = targetSuggestion ?: selectedSuggestion?.takeIf {
            it.title.equals(raw, ignoreCase = true) || it.fullQuery.equals(raw, ignoreCase = true)
        } ?: NavLocationSuggestion(
            title = raw.substringBefore(",").trim(),
            subtitle = raw.substringAfter(",", "").trim(),
            fullQuery = raw
        )
        modeMenuExpanded = false
        isInputFocused = false
        keyboardController?.hide()
        focusManager.clearFocus()
        coroutineScope.launch {
            QuickNavigationLauncher.executeNavigation(context, mode, dest)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Primary Pill Bar: Left Expandable Mode Selector + Target Location Textbox + Go Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xD917181E))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left button: expands the 5 travel options (Bus, Bike, Walk, Drive, Ride)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selectedMode == QuickNavMode.RIDE) {
                            Color(0xFF8B5CF6).copy(alpha = 0.26f)
                        } else {
                            Color(0xFF0381FE).copy(alpha = 0.22f)
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
                    .clickable { modeMenuExpanded = !modeMenuExpanded }
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
                        }
                )
            }

            // Clear button when text is present
            if (destinationText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable {
                            destinationText = ""
                            selectedSuggestion = null
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xF017181E))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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
        }

        // Suggested Locations Dropdown
        AnimatedVisibility(
            visible = isInputFocused && suggestions.isNotEmpty(),
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                destinationText = suggestion.title
                                selectedSuggestion = suggestion
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
                            Text(
                                text = suggestion.title,
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
