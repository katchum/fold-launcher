package com.xsun.foldlauncher.data

import android.app.Activity
import android.app.ActivityOptions
import android.app.SearchManager
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import com.xsun.foldlauncher.model.AppEntry
import com.xsun.foldlauncher.model.AppPair
import com.xsun.foldlauncher.model.AppShortcutItem
import java.util.concurrent.ConcurrentHashMap

class AppRepository(
    private val context: Context,
    private val preferences: LauncherPreferences = LauncherPreferences(context)
) {
    private val appContext: Context = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val launcherApps: LauncherApps? =
        appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
    private val userManager: UserManager? =
        appContext.getSystemService(Context.USER_SERVICE) as? UserManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()
    private val shortcutCache = ConcurrentHashMap<String, List<AppShortcutItem>>()

    private var onPackagesChangedListener: (() -> Unit)? = null
    private var isObserving = false

    private val launcherAppsCallback = object : LauncherApps.Callback() {
        private fun evictPackage(pkg: String?) {
            if (pkg == null) return
            iconCache.keys.removeAll { it == pkg || it.startsWith("$pkg#") }
            shortcutCache.keys.removeAll { it == pkg || it.startsWith("$pkg#") }
        }

        override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
            evictPackage(packageName)
            notifyPackagesChanged()
        }

        override fun onPackageAdded(packageName: String?, user: UserHandle?) {
            evictPackage(packageName)
            notifyPackagesChanged()
        }

        override fun onPackageChanged(packageName: String?, user: UserHandle?) {
            evictPackage(packageName)
            notifyPackagesChanged()
        }

        override fun onPackagesAvailable(
            packageNames: Array<out String>?,
            user: UserHandle?,
            replacing: Boolean
        ) {
            packageNames?.forEach { evictPackage(it) }
            notifyPackagesChanged()
        }

        override fun onPackagesUnavailable(
            packageNames: Array<out String>?,
            user: UserHandle?,
            replacing: Boolean
        ) {
            packageNames?.forEach { evictPackage(it) }
            notifyPackagesChanged()
        }
    }

    private val packageBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val pkg = intent?.data?.schemeSpecificPart
            if (pkg != null) {
                iconCache.keys.removeAll { it == pkg || it.startsWith("$pkg#") }
                shortcutCache.keys.removeAll { it == pkg || it.startsWith("$pkg#") }
            }
            notifyPackagesChanged()
        }
    }

    private val managedProfileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            notifyPackagesChanged()
        }
    }

    fun startObservingPackages(onChanged: () -> Unit) {
        onPackagesChangedListener = onChanged
        if (isObserving) return
        isObserving = true

        try {
            launcherApps?.registerCallback(launcherAppsCallback, mainHandler)
        } catch (_: Exception) {
        }

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
            ContextCompat.registerReceiver(
                appContext,
                packageBroadcastReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (_: Exception) {
        }

        try {
            val profileFilter = IntentFilter().apply {
                addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
                addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
                addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
                addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
            }
            ContextCompat.registerReceiver(
                appContext,
                managedProfileReceiver,
                profileFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (_: Exception) {
        }
    }

    fun stopObservingPackages() {
        onPackagesChangedListener = null
        if (!isObserving) return
        isObserving = false

        try {
            launcherApps?.unregisterCallback(launcherAppsCallback)
        } catch (_: Exception) {
        }

        try {
            appContext.unregisterReceiver(packageBroadcastReceiver)
        } catch (_: Exception) {
        }

        try {
            appContext.unregisterReceiver(managedProfileReceiver)
        } catch (_: Exception) {
        }
    }

    private fun notifyPackagesChanged() {
        mainHandler.post {
            onPackagesChangedListener?.invoke()
        }
    }

    /**
     * Queries all launchable apps across all user profiles (Personal `profile 0` + Work Profile `profile 10` / managed profiles)
     * with high-resolution icons, dynamic/manifest shortcuts, categories, and persisted user pin/launch metadata.
     */
    fun loadInstalledApps(includeHidden: Boolean = true): List<AppEntry> {
        val selfPackage = appContext.packageName
        val pinnedCover = preferences.pinnedCoverPackages
        val pinnedInner = preferences.pinnedInnerPackages
        val pinnedDock = preferences.pinnedDockPackages
        val hidden = preferences.hiddenPackages
        val counts = preferences.launchCounts
        val myUser = Process.myUserHandle()

        val results = LinkedHashMap<String, AppEntry>()

        val profileSet = linkedSetOf<UserHandle>()
        try {
            launcherApps?.profiles?.let { profileSet.addAll(it) }
        } catch (_: Exception) {
        }
        try {
            userManager?.userProfiles?.let { profileSet.addAll(it) }
        } catch (_: Exception) {
        }
        if (profileSet.isEmpty()) {
            profileSet.add(myUser)
        }
        val profiles: List<UserHandle> = profileSet.toList()

        val la = launcherApps
        if (la != null) {
            for (profile in profiles) {
                val isWork = profile != myUser
                val serial = try {
                    userManager?.getSerialNumberForUser(profile) ?: (if (isWork) 10L else 0L)
                } catch (_: Exception) {
                    if (isWork) 10L else 0L
                }

                val activities: List<LauncherActivityInfo> = try {
                    la.getActivityList(null, profile)
                } catch (_: Exception) {
                    emptyList()
                }
                for (info in activities) {
                    val pkg = info.applicationInfo.packageName
                    if (pkg == selfPackage && !isWork) continue
                    val entryKey = if (isWork) "$pkg#work_$serial" else pkg
                    if (!includeHidden && (entryKey in hidden || pkg in hidden)) continue
                    if (results.containsKey(entryKey)) continue

                    val label = info.label?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: pkg
                    val activityName = info.componentName?.className ?: ""
                    val iconBitmap = iconCache.getOrPut(entryKey) {
                        val drawable = try {
                            info.getIcon(0)
                        } catch (_: Exception) {
                            try {
                                info.applicationInfo.loadIcon(packageManager)
                            } catch (_: Exception) {
                                try {
                                    info.getBadgedIcon(0)
                                } catch (_: Exception) {
                                    try {
                                        packageManager.getApplicationIcon(pkg)
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                            }
                        }
                        drawable?.let { drawableToImageBitmap(it) } ?: createFallbackBitmap()
                    }
                    val category = resolveCategoryName(info.applicationInfo, pkg)
                    val shortcuts = shortcutCache.getOrPut(entryKey) {
                        queryAppShortcuts(pkg, profile, isWork)
                    }

                    results[entryKey] = AppEntry(
                        label = label,
                        packageName = pkg,
                        activityName = activityName,
                        icon = iconBitmap,
                        launchCount = counts[entryKey] ?: counts[pkg] ?: 0,
                        isPinnedCover = entryKey in pinnedCover || (!isWork && pkg in pinnedCover),
                        isPinnedInner = entryKey in pinnedInner || (!isWork && pkg in pinnedInner),
                        isDockPinned = entryKey in pinnedDock || (!isWork && pkg in pinnedDock),
                        category = category,
                        shortcuts = shortcuts,
                        userHandle = profile,
                        userSerialNumber = serial,
                        isWorkProfile = isWork
                    )
                }
            }
        }

        // Fallback via PackageManager.queryIntentActivities if LauncherApps returned nothing
        if (results.isEmpty()) {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = try {
                packageManager.queryIntentActivities(mainIntent, 0)
            } catch (_: Exception) {
                emptyList()
            }
            for (ri in resolveInfos) {
                val activityInfo = ri.activityInfo ?: continue
                val pkg = activityInfo.packageName ?: continue
                if (pkg == selfPackage) continue
                if (!includeHidden && pkg in hidden) continue
                if (results.containsKey(pkg)) continue

                val label = try {
                    ri.loadLabel(packageManager)?.toString()?.trim()
                } catch (_: Exception) {
                    null
                }.takeUnless { it.isNullOrEmpty() } ?: pkg

                val iconBitmap = iconCache.getOrPut(pkg) {
                    val drawable = try {
                        ri.loadIcon(packageManager)
                    } catch (_: Exception) {
                        null
                    }
                    drawable?.let { drawableToImageBitmap(it) } ?: createFallbackBitmap()
                }
                val category = resolveCategoryName(activityInfo.applicationInfo, pkg)

                results[pkg] = AppEntry(
                    label = label,
                    packageName = pkg,
                    activityName = activityInfo.name ?: "",
                    icon = iconBitmap,
                    launchCount = counts[pkg] ?: 0,
                    isPinnedCover = pkg in pinnedCover,
                    isPinnedInner = pkg in pinnedInner,
                    isDockPinned = pkg in pinnedDock,
                    category = category,
                    shortcuts = emptyList(),
                    userHandle = myUser,
                    userSerialNumber = 0L,
                    isWorkProfile = false
                )
            }
        }

        return results.values.sortedWith(
            compareBy<AppEntry> { it.normalizedLabel }.thenBy { it.isWorkProfile }
        )
    }

    /**
     * Queries available dynamic and manifest shortcuts for [packageName].
     * Note: Android allows the active default home launcher to query shortcuts via [LauncherApps].
     */
    fun queryAppShortcuts(
        packageName: String,
        userHandle: UserHandle = Process.myUserHandle(),
        isWorkProfile: Boolean = userHandle != Process.myUserHandle()
    ): List<AppShortcutItem> {
        val la = launcherApps ?: return emptyList()
        return try {
            if (!la.hasShortcutHostPermission()) return emptyList()
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            }
            val infos = la.getShortcuts(query, userHandle) ?: return emptyList()
            infos
                .filter { it.isEnabled }
                .sortedBy { it.rank }
                .take(4)
                .map { info ->
                    val shortText = info.shortLabel?.toString()?.trim()
                        .takeUnless { it.isNullOrEmpty() } ?: "Shortcut"
                    val longText = info.longLabel?.toString()?.trim()
                        .takeUnless { it.isNullOrEmpty() } ?: shortText
                    AppShortcutItem(
                        id = info.id,
                        packageName = info.`package`,
                        shortLabel = shortText,
                        longLabel = longText,
                        userHandle = userHandle,
                        isWorkProfile = isWorkProfile
                    )
                }
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Launches an app in its target [UserHandle] profile (Personal `profile 0` or Work Profile `profile 10`)
     * with a smooth scale-up animation from [sourceBounds].
     */
    fun launchApp(app: AppEntry, sourceBounds: Rect? = null): Boolean {
        return launchPackage(
            packageName = app.packageName,
            activityName = app.activityName,
            sourceBounds = sourceBounds,
            userHandle = app.userHandle ?: Process.myUserHandle()
        )
    }

    fun launchPackage(
        packageName: String,
        activityName: String = "",
        sourceBounds: Rect? = null,
        userHandle: UserHandle = Process.myUserHandle()
    ): Boolean {
        val optionsBundle = try {
            if (sourceBounds != null && !sourceBounds.isEmpty) {
                ActivityOptions.makeScaleUpAnimation(
                    null,
                    sourceBounds.left,
                    sourceBounds.top,
                    sourceBounds.width(),
                    sourceBounds.height()
                ).toBundle()
            } else {
                ActivityOptions.makeCustomAnimation(
                    appContext,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
                ).toBundle()
            }
        } catch (_: Exception) {
            null
        }

        val la = launcherApps
        if (la != null) {
            try {
                val component = if (activityName.isNotBlank()) {
                    ComponentName(packageName, activityName)
                } else {
                    la.getActivityList(packageName, userHandle).firstOrNull()?.componentName
                }
                if (component != null) {
                    la.startMainActivity(component, userHandle, sourceBounds, optionsBundle)
                    return true
                }
            } catch (_: Exception) {
            }
        }

        if (userHandle != Process.myUserHandle()) {
            return false
        }

        return try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                if (sourceBounds != null) {
                    setSourceBounds(sourceBounds)
                }
            } ?: return false
            appContext.startActivity(launchIntent, optionsBundle)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Launches a single app in split-screen / adjacent multi-window mode on the Z Fold 8 inner screen.
     */
    fun launchSplitScreenSingle(app: AppEntry): Boolean {
        val targetUser = app.userHandle ?: Process.myUserHandle()
        if (targetUser != Process.myUserHandle()) {
            return launchApp(app)
        }
        return try {
            val intent = packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                )
            } ?: return false
            appContext.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Launches a saved Z Fold 8 split-screen [AppPair] (primary app first, followed by secondary
     * app with `FLAG_ACTIVITY_LAUNCH_ADJACENT` after a 120ms multi-window staging delay).
     */
    fun launchAppPair(pair: AppPair): Boolean {
        val primaryIntent = packageManager.getLaunchIntentForPackage(pair.primaryPackage)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return false

        val secondaryIntent = packageManager.getLaunchIntentForPackage(pair.secondaryPackage)?.apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            )
        } ?: return false

        return try {
            appContext.startActivity(primaryIntent)
            mainHandler.postDelayed({
                try {
                    appContext.startActivity(secondaryIntent)
                } catch (_: Exception) {
                }
            }, 120L)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Launches an [AppShortcutItem] via [LauncherApps.startShortcut], falling back to launching the app.
     */
    fun launchShortcut(shortcut: AppShortcutItem): Boolean {
        val targetUser = shortcut.userHandle ?: Process.myUserHandle()
        val la = launcherApps
        if (la != null) {
            try {
                la.startShortcut(
                    shortcut.packageName,
                    shortcut.id,
                    null,
                    null,
                    targetUser
                )
                return true
            } catch (_: Exception) {
            }
        }
        return launchPackage(shortcut.packageName, userHandle = targetUser)
    }

    /**
     * Opens system App Info for [app] in its owning profile (`Personal` or `Work Profile`).
     */
    fun openAppInfo(app: AppEntry) {
        val targetUser = app.userHandle ?: Process.myUserHandle()
        val la = launcherApps
        if (la != null) {
            try {
                val component = if (app.activityName.isNotBlank()) {
                    ComponentName(app.packageName, app.activityName)
                } else {
                    la.getActivityList(app.packageName, targetUser).firstOrNull()?.componentName
                }
                if (component != null) {
                    la.startAppDetailsActivity(component, targetUser, null, null)
                    return
                }
            } catch (_: Exception) {
            }
        }
        openAppInfo(app.packageName)
    }

    /**
     * Opens system App Info (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`) for [packageName].
     */
    fun openAppInfo(packageName: String) {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    /**
     * Requests package uninstallation (`Intent.ACTION_DELETE`) for [packageName].
     */
    fun requestUninstall(packageName: String) {
        try {
            val intent = Intent(
                Intent.ACTION_DELETE,
                Uri.parse("package:$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    /**
     * Launches a web search for [query] via `Intent.ACTION_WEB_SEARCH` with browser URL fallback.
     */
    fun launchWebSearch(query: String) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return

        try {
            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, cleaned)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(searchIntent)
            return
        } catch (_: Exception) {
        }

        try {
            val url = "https://www.google.com/search?q=${Uri.encode(cleaned)}"
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(browserIntent)
        } catch (_: Exception) {
        }
    }

    /**
     * Opens the system wallpaper picker chooser.
     */
    fun openWallpaperPicker() {
        try {
            val intent = Intent.createChooser(
                Intent(Intent.ACTION_SET_WALLPAPER),
                "Select Wallpaper"
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    /**
     * Checks whether Fold8 & Slab Launcher is currently set as the default system Home app.
     */
    fun isDefaultLauncher(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = appContext.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(
            homeIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolveInfo?.activityInfo?.packageName == appContext.packageName
    }

    /**
     * Prompts the user to set Fold8 & Slab Launcher as the default Home app via [RoleManager]
     * or opens `Settings.ACTION_HOME_SETTINGS`.
     */
    fun requestDefaultLauncherRole(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = activity.getSystemService(RoleManager::class.java)
                if (roleManager != null &&
                    roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                    !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
                ) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    @Suppress("DEPRECATION")
                    activity.startActivityForResult(intent, REQUEST_CODE_ROLE_HOME)
                    return
                }
            }
        } catch (_: Exception) {
        }

        try {
            val settingsIntent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(settingsIntent)
        } catch (_: Exception) {
        }
    }

    /**
     * Opens the user's Clock / Alarms application when tapping the top-right oversized clock.
     */
    fun openClockApp() {
        try {
            val alarmIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(alarmIntent)
            return
        } catch (_: Exception) {
        }

        val knownClockPackages = listOf(
            "com.sec.android.app.clockpackage",
            "com.google.android.deskclock",
            "com.android.deskclock"
        )
        for (pkg in knownClockPackages) {
            if (launchPackage(pkg)) return
        }
    }

    private fun resolveCategoryName(appInfo: ApplicationInfo?, packageName: String): String {
        if (appInfo != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (appInfo.category) {
                ApplicationInfo.CATEGORY_GAME -> return "Games"
                ApplicationInfo.CATEGORY_AUDIO,
                ApplicationInfo.CATEGORY_VIDEO,
                ApplicationInfo.CATEGORY_IMAGE -> return "Media"
                ApplicationInfo.CATEGORY_SOCIAL -> return "Social"
                ApplicationInfo.CATEGORY_NEWS -> return "News"
                ApplicationInfo.CATEGORY_MAPS -> return "Travel"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> return "Productivity"
            }
        }
        val lower = packageName.lowercase()
        return when {
            lower.contains("dialer") || lower.contains("messaging") || lower.contains("gm") ||
                lower.contains("chat") || lower.contains("whatsapp") || lower.contains("telegram") -> "Communication"
            lower.contains("youtube") || lower.contains("camera") || lower.contains("photos") ||
                lower.contains("music") || lower.contains("spotify") || lower.contains("gallery") -> "Media"
            lower.contains("chrome") || lower.contains("browser") || lower.contains("notes") ||
                lower.contains("calendar") || lower.contains("docs") || lower.contains("keep") -> "Productivity"
            lower.contains("settings") || lower.contains("files") || lower.contains("inputmethod") ||
                lower.contains("calculator") || lower.contains("clock") -> "Utilities"
            else -> "Apps"
        }
    }

    private fun drawableToImageBitmap(drawable: Drawable, targetSizePx: Int = 144): ImageBitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap.asImageBitmap()
        }
        val width = drawable.intrinsicWidth.coerceAtLeast(targetSizePx).coerceAtMost(192)
        val height = drawable.intrinsicHeight.coerceAtLeast(targetSizePx).coerceAtMost(192)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }

    private fun createFallbackBitmap(): ImageBitmap {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        return bitmap.asImageBitmap()
    }

    companion object {
        const val REQUEST_CODE_ROLE_HOME = 1001
    }
}
