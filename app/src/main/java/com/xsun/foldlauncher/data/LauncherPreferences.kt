package com.xsun.foldlauncher.data

import android.content.Context
import android.content.SharedPreferences
import com.xsun.foldlauncher.model.AppPair
import com.xsun.foldlauncher.model.FolderItem
import com.xsun.foldlauncher.vc.VcConnectionConfig
import com.xsun.foldlauncher.vc.VcPackages
import org.json.JSONArray
import org.json.JSONObject

class LauncherPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var pinnedCoverPackages: Set<String>
        get() {
            if (!prefs.contains(KEY_PINNED_COVER)) {
                return DEFAULT_COVER_PACKAGES
            }
            return prefs.getStringSet(KEY_PINNED_COVER, DEFAULT_COVER_PACKAGES)?.toSet()
                ?: DEFAULT_COVER_PACKAGES
        }
        set(value) {
            prefs.edit().putStringSet(KEY_PINNED_COVER, value.toSet()).apply()
        }

    var pinnedInnerPackages: Set<String>
        get() {
            if (!prefs.contains(KEY_PINNED_INNER)) {
                return DEFAULT_INNER_PACKAGES
            }
            return prefs.getStringSet(KEY_PINNED_INNER, DEFAULT_INNER_PACKAGES)?.toSet()
                ?: DEFAULT_INNER_PACKAGES
        }
        set(value) {
            prefs.edit().putStringSet(KEY_PINNED_INNER, value.toSet()).apply()
        }

    var pinnedDockPackages: Set<String>
        get() {
            if (!prefs.contains(KEY_PINNED_DOCK)) {
                return DEFAULT_DOCK_PACKAGES
            }
            return prefs.getStringSet(KEY_PINNED_DOCK, DEFAULT_DOCK_PACKAGES)?.toSet()
                ?: DEFAULT_DOCK_PACKAGES
        }
        set(value) {
            prefs.edit().putStringSet(KEY_PINNED_DOCK, value.toSet()).apply()
        }

    var hiddenPackages: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN_PACKAGES, emptySet())?.toSet() ?: emptySet()
        set(value) {
            prefs.edit().putStringSet(KEY_HIDDEN_PACKAGES, value.toSet()).apply()
        }

    var launchCounts: Map<String, Int>
        get() {
            val raw = prefs.getString(KEY_LAUNCH_COUNTS, null) ?: return emptyMap()
            return try {
                val json = JSONObject(raw)
                val map = mutableMapOf<String, Int>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = json.optInt(key, 0)
                }
                map
            } catch (_: Exception) {
                emptyMap()
            }
        }
        set(value) {
            val json = JSONObject()
            value.forEach { (pkg, count) ->
                json.put(pkg, count)
            }
            prefs.edit().putString(KEY_LAUNCH_COUNTS, json.toString()).apply()
        }

    fun incrementLaunchCount(packageName: String): Int {
        val current = launchCounts.toMutableMap()
        val updated = (current[packageName] ?: 0) + 1
        current[packageName] = updated
        launchCounts = current
        return updated
    }

    var appPairs: List<AppPair>
        get() {
            val raw = prefs.getString(KEY_APP_PAIRS, null) ?: return DEFAULT_APP_PAIRS
            return try {
                val arr = JSONArray(raw)
                val list = mutableListOf<AppPair>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        AppPair(
                            id = obj.optString("id", "pair_$i"),
                            title = obj.optString("title", "App Pair"),
                            primaryPackage = obj.optString("primaryPackage", ""),
                            secondaryPackage = obj.optString("secondaryPackage", "")
                        )
                    )
                }
                if (list.isEmpty() && !prefs.contains(KEY_APP_PAIRS_INITIALIZED)) {
                    DEFAULT_APP_PAIRS
                } else {
                    list
                }
            } catch (_: Exception) {
                DEFAULT_APP_PAIRS
            }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { pair ->
                val obj = JSONObject().apply {
                    put("id", pair.id)
                    put("title", pair.title)
                    put("primaryPackage", pair.primaryPackage)
                    put("secondaryPackage", pair.secondaryPackage)
                }
                arr.put(obj)
            }
            prefs.edit()
                .putString(KEY_APP_PAIRS, arr.toString())
                .putBoolean(KEY_APP_PAIRS_INITIALIZED, true)
                .apply()
        }

    var folders: List<FolderItem>
        get() {
            val raw = prefs.getString(KEY_FOLDERS, null) ?: return DEFAULT_FOLDERS
            return try {
                val arr = JSONArray(raw)
                val list = mutableListOf<FolderItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val pkgArr = obj.optJSONArray("packageNames") ?: JSONArray()
                    val pkgs = mutableListOf<String>()
                    for (j in 0 until pkgArr.length()) {
                        val pkg = pkgArr.optString(j)
                        if (pkg.isNotBlank()) pkgs.add(pkg)
                    }
                    list.add(
                        FolderItem(
                            id = obj.optString("id", "folder_$i"),
                            name = obj.optString("name", "Folder"),
                            packageNames = pkgs,
                            isCoverFolder = obj.optBoolean("isCoverFolder", true),
                            isInnerFolder = obj.optBoolean("isInnerFolder", true)
                        )
                    )
                }
                list
            } catch (_: Exception) {
                DEFAULT_FOLDERS
            }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { folder ->
                val pkgArr = JSONArray()
                folder.packageNames.forEach { pkgArr.put(it) }
                val obj = JSONObject().apply {
                    put("id", folder.id)
                    put("name", folder.name)
                    put("packageNames", pkgArr)
                    put("isCoverFolder", folder.isCoverFolder)
                    put("isInnerFolder", folder.isInnerFolder)
                }
                arr.put(obj)
            }
            prefs.edit().putString(KEY_FOLDERS, arr.toString()).apply()
        }

    var hideSystemStatusBar: Boolean
        get() = prefs.getBoolean(KEY_HIDE_STATUS_BAR, true)
        set(value) {
            prefs.edit().putBoolean(KEY_HIDE_STATUS_BAR, value).apply()
        }

    var iconShapePresetName: String
        get() = prefs.getString(KEY_ICON_SHAPE_PRESET, "SQUIRCLE") ?: "SQUIRCLE"
        set(value) {
            prefs.edit().putString(KEY_ICON_SHAPE_PRESET, value).apply()
        }

    var quickNoteText: String
        get() = prefs.getString(KEY_QUICK_NOTE_TEXT, DEFAULT_QUICK_NOTE) ?: DEFAULT_QUICK_NOTE
        set(value) {
            prefs.edit().putString(KEY_QUICK_NOTE_TEXT, value).apply()
        }

    var nextEventText: String
        get() = prefs.getString(KEY_NEXT_EVENT_TEXT, "Fold8 Ready · 120Hz") ?: "Fold8 Ready · 120Hz"
        set(value) {
            prefs.edit().putString(KEY_NEXT_EVENT_TEXT, value).apply()
        }

    var autoGuardDexKeyboard: Boolean
        get() = prefs.getBoolean(KEY_AUTO_GUARD_DEX_IME, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_GUARD_DEX_IME, value).apply()
        }

    var floatingHudEnabled: Boolean
        get() = prefs.getBoolean(KEY_FLOATING_HUD_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_FLOATING_HUD_ENABLED, value).apply()
        }

    var radialArcEnabled: Boolean
        get() = prefs.getBoolean(KEY_RADIAL_ARC_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_RADIAL_ARC_ENABLED, value).apply()
        }

    var radialArcRightHanded: Boolean
        get() = prefs.getBoolean(KEY_RADIAL_ARC_RIGHT_HANDED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_RADIAL_ARC_RIGHT_HANDED, value).apply()
        }

    var spatialParallaxEnabled: Boolean
        get() = prefs.getBoolean(KEY_SPATIAL_PARALLAX_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SPATIAL_PARALLAX_ENABLED, value).apply()
        }

    var vcServerUrl: String
        get() = prefs.getString(KEY_VC_SERVER_URL, VcPackages.DEFAULT_LOCAL_SERVER_URL)
            ?: VcPackages.DEFAULT_LOCAL_SERVER_URL
        set(value) {
            prefs.edit().putString(KEY_VC_SERVER_URL, value.trim()).apply()
        }

    var vcAuthToken: String
        get() = prefs.getString(KEY_VC_AUTH_TOKEN, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_VC_AUTH_TOKEN, value.trim()).apply()
        }

    var vcMonitorEnabled: Boolean
        get() = prefs.getBoolean(KEY_VC_MONITOR_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_VC_MONITOR_ENABLED, value).apply()
        }

    var vcNotifyWhenBackgrounded: Boolean
        get() = prefs.getBoolean(KEY_VC_NOTIFY_BACKGROUNDED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_VC_NOTIFY_BACKGROUNDED, value).apply()
        }

    var vcNotifyBackground: Boolean
        get() = vcNotifyWhenBackgrounded
        set(value) {
            vcNotifyWhenBackgrounded = value
        }

    var vcSeenAttentionIds: Set<String>
        get() = prefs.getStringSet(KEY_VC_SEEN_ATTENTION_IDS, emptySet())?.toSet() ?: emptySet()
        set(value) {
            prefs.edit().putStringSet(KEY_VC_SEEN_ATTENTION_IDS, value.toSet()).apply()
        }

    var vcConnectionConfig: VcConnectionConfig
        get() = VcConnectionConfig(
            serverUrl = vcServerUrl,
            authToken = vcAuthToken,
            monitorEnabled = vcMonitorEnabled,
            notifyWhenBackgrounded = vcNotifyWhenBackgrounded
        )
        set(value) {
            prefs.edit()
                .putString(KEY_VC_SERVER_URL, value.serverUrl.trim())
                .putString(KEY_VC_AUTH_TOKEN, value.authToken.trim())
                .putBoolean(KEY_VC_MONITOR_ENABLED, value.monitorEnabled)
                .putBoolean(KEY_VC_NOTIFY_BACKGROUNDED, value.notifyWhenBackgrounded)
                .apply()
        }

    companion object {
        private const val PREFS_NAME = "fold_launcher_prefs"

        private const val KEY_PINNED_COVER = "pinned_cover_packages"
        private const val KEY_PINNED_INNER = "pinned_inner_packages"
        private const val KEY_PINNED_DOCK = "pinned_dock_packages"
        private const val KEY_HIDDEN_PACKAGES = "hidden_packages"
        private const val KEY_LAUNCH_COUNTS = "launch_counts_json"
        private const val KEY_APP_PAIRS = "app_pairs_json"
        private const val KEY_APP_PAIRS_INITIALIZED = "app_pairs_initialized"
        private const val KEY_FOLDERS = "folders_json"
        private const val KEY_HIDE_STATUS_BAR = "hide_system_status_bar"
        private const val KEY_ICON_SHAPE_PRESET = "icon_shape_preset_name"
        private const val KEY_QUICK_NOTE_TEXT = "quick_note_text"
        private const val KEY_NEXT_EVENT_TEXT = "next_event_text"
        private const val KEY_AUTO_GUARD_DEX_IME = "auto_guard_dex_ime"
        private const val KEY_FLOATING_HUD_ENABLED = "floating_hud_enabled"
        private const val KEY_RADIAL_ARC_ENABLED = "radial_arc_enabled"
        private const val KEY_RADIAL_ARC_RIGHT_HANDED = "radial_arc_right_handed"
        private const val KEY_SPATIAL_PARALLAX_ENABLED = "spatial_parallax_enabled"
        private const val KEY_VC_SERVER_URL = "vc_server_url"
        private const val KEY_VC_AUTH_TOKEN = "vc_auth_token"
        private const val KEY_VC_MONITOR_ENABLED = "vc_monitor_enabled"
        private const val KEY_VC_NOTIFY_BACKGROUNDED = "vc_notify_backgrounded"
        private const val KEY_VC_SEEN_ATTENTION_IDS = "vc_seen_attention_ids"

        private const val DEFAULT_QUICK_NOTE =
            "Swipe up for instant Spotlight search + math.\nTap an App Pair on the unfolded display for 1-tap split screen."

        val DEFAULT_COVER_PACKAGES: Set<String> = linkedSetOf(
            "dev.agentconversation.preview",
            "dev.agentconversation",
            "com.termux",
            "com.samsung.android.dialer",
            "com.google.android.dialer",
            "com.android.dialer",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.chrome",
            "com.sec.android.app.sbrowser",
            "com.sec.android.app.camera",
            "com.android.camera2",
            "com.android.settings",
            "com.google.android.inputmethod.latin",
            "com.google.android.gm",
            "com.google.android.apps.maps",
            "com.google.android.youtube"
        )

        val DEFAULT_INNER_PACKAGES: Set<String> = linkedSetOf(
            "dev.agentconversation.preview",
            "dev.agentconversation",
            "com.termux",
            "com.android.chrome",
            "com.google.android.youtube",
            "com.google.android.gm",
            "com.google.android.apps.maps",
            "com.google.android.calendar",
            "com.samsung.android.calendar",
            "com.samsung.android.app.notes",
            "com.google.android.keep",
            "com.google.android.apps.docs",
            "com.sec.android.app.myfiles",
            "com.google.android.apps.nbu.files",
            "com.android.settings",
            "com.sec.android.app.camera",
            "com.google.android.apps.photos"
        )

        val DEFAULT_DOCK_PACKAGES: Set<String> = linkedSetOf(
            "dev.agentconversation.preview",
            "dev.agentconversation",
            "com.termux",
            "com.samsung.android.dialer",
            "com.google.android.dialer",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.chrome",
            "com.sec.android.app.sbrowser",
            "com.sec.android.app.camera",
            "com.android.settings"
        )

        val DEFAULT_APP_PAIRS: List<AppPair> = listOf(
            AppPair(
                id = "pair_vc_termux",
                title = "VC + Termux",
                primaryPackage = "dev.agentconversation.preview",
                secondaryPackage = "com.termux"
            ),
            AppPair(
                id = "pair_vc_chrome",
                title = "VC + Chrome",
                primaryPackage = "dev.agentconversation.preview",
                secondaryPackage = "com.android.chrome"
            ),
            AppPair(
                id = "pair_browser_notes",
                title = "Chrome + Settings",
                primaryPackage = "com.android.chrome",
                secondaryPackage = "com.android.settings"
            ),
            AppPair(
                id = "pair_video_browser",
                title = "YouTube + Chrome",
                primaryPackage = "com.google.android.youtube",
                secondaryPackage = "com.android.chrome"
            ),
            AppPair(
                id = "pair_maps_messages",
                title = "Maps + Messages",
                primaryPackage = "com.google.android.apps.maps",
                secondaryPackage = "com.google.android.apps.messaging"
            )
        )

        val DEFAULT_FOLDERS: List<FolderItem> = listOf(
            FolderItem(
                id = "folder_productivity",
                name = "Productivity",
                packageNames = listOf(
                    "com.android.chrome",
                    "com.google.android.gm",
                    "com.google.android.calendar",
                    "com.samsung.android.app.notes",
                    "com.android.settings"
                ),
                isCoverFolder = true,
                isInnerFolder = true
            ),
            FolderItem(
                id = "folder_media",
                name = "Media & Social",
                packageNames = listOf(
                    "com.google.android.youtube",
                    "com.google.android.apps.photos",
                    "com.sec.android.app.camera",
                    "com.google.android.apps.maps"
                ),
                isCoverFolder = true,
                isInnerFolder = true
            )
        )
    }
}
