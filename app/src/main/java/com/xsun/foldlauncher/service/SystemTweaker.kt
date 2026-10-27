package com.xsun.foldlauncher.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat

object SystemTweaker {

    const val SAMSUNG_HONEYBOARD_PACKAGE = "com.samsung.android.honeyboard"
    const val GBOARD_IME_ID = "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"
    const val SWIFTKEY_IME_ID = "com.touchtype.swiftkey/com.touchtype.KeyboardService"

    const val VC_PROD_PACKAGE = "dev.agentconversation.vci"
    const val VC_PREVIEW_PACKAGE = "dev.agentconversation.preview"
    const val VC_TALK_ACTION = "dev.agentconversation.widget.TALK"
    const val DEFAULT_VC_HOST_URL = "http://100.96.188.90:8080"

    private const val KEY_LOCK_SCREEN_ALLOW_ROTATION = "lock_screen_allow_rotation"
    private const val KEY_DEX_ON_SCREEN_KEYBOARD_LOCATION = "dex_on_screen_keyboard_location"

    fun hasWriteSecureSettings(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (FoldAccessibilityService.instance != null) return true
        val expectedComponent = "${context.packageName}/${FoldAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expectedComponent, ignoreCase = true) }
    }

    fun getCurrentImeId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: ""
    }

    fun isSamsungKeyboardActive(context: Context): Boolean {
        return getCurrentImeId(context).startsWith(SAMSUNG_HONEYBOARD_PACKAGE)
    }

    /**
     * Resolves the best non-Samsung keyboard installed on the device (preferring Gboard, then SwiftKey).
     */
    fun findPreferredNonSamsungIme(context: Context): String? {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return GBOARD_IME_ID
        val enabledList = imm.enabledInputMethodList
        val gboard = enabledList.firstOrNull {
            it.id.equals(GBOARD_IME_ID, ignoreCase = true) ||
                it.packageName.contains("google.android.inputmethod", ignoreCase = true)
        }
        if (gboard != null) return gboard.id

        val swiftKey = enabledList.firstOrNull {
            it.id.equals(SWIFTKEY_IME_ID, ignoreCase = true) ||
                it.packageName.contains("swiftkey", ignoreCase = true)
        }
        if (swiftKey != null) return swiftKey.id

        return enabledList.firstOrNull {
            !it.packageName.equals(SAMSUNG_HONEYBOARD_PACKAGE, ignoreCase = true)
        }?.id
    }

    /**
     * Prevents Samsung DeX from locking the user into Samsung Keyboard (Honeyboard).
     * Requires `android.permission.WRITE_SECURE_SETTINGS` granted once via ADB.
     */
    fun enforcePreferredIme(
        context: Context,
        preferredImeId: String? = null
    ): Boolean {
        if (!hasWriteSecureSettings(context)) return false
        val targetIme = preferredImeId ?: findPreferredNonSamsungIme(context) ?: GBOARD_IME_ID
        return try {
            val cr = context.contentResolver
            Settings.Secure.putString(cr, Settings.Secure.DEFAULT_INPUT_METHOD, targetIme)
            // Also route DeX virtual keyboard to phone screen touchpad mode (1) so One UI doesn't override IME
            Settings.Global.putInt(cr, KEY_DEX_ON_SCREEN_KEYBOARD_LOCATION, 1)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun isLockScreenRotationEnabled(context: Context): Boolean {
        return try {
            val cr = context.contentResolver
            val sysVal = Settings.System.getInt(cr, KEY_LOCK_SCREEN_ALLOW_ROTATION, -1)
            if (sysVal != -1) return sysVal == 1
            Settings.Secure.getInt(cr, KEY_LOCK_SCREEN_ALLOW_ROTATION, 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Enables or disables Lock Screen + Home Screen landscape rotation on Samsung One UI / Z Fold 8.
     */
    fun setLockScreenRotation(context: Context, enabled: Boolean): Boolean {
        val value = if (enabled) 1 else 0
        var updated = false
        val cr = context.contentResolver
        try {
            if (Settings.System.canWrite(context) || hasWriteSecureSettings(context)) {
                Settings.System.putInt(cr, KEY_LOCK_SCREEN_ALLOW_ROTATION, value)
                Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, value)
                updated = true
            }
        } catch (_: Exception) {
        }
        try {
            if (hasWriteSecureSettings(context)) {
                Settings.Secure.putInt(cr, KEY_LOCK_SCREEN_ALLOW_ROTATION, value)
                Settings.Global.putInt(cr, KEY_LOCK_SCREEN_ALLOW_ROTATION, value)
                updated = true
            }
        } catch (_: Exception) {
        }
        return updated
    }

    /**
     * Expands the system notification shade when swiping down anywhere on the home screen.
     */
    @SuppressLint("WrongConstant", "PrivateApi")
    fun expandNotificationsPanel(context: Context): Boolean {
        val a11y = FoldAccessibilityService.instance
        if (a11y != null && a11y.openNotificationShade()) {
            return true
        }
        return try {
            val statusBarService = context.getSystemService("statusbar") ?: return false
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val expandMethod = statusBarManager.getMethod("expandNotificationsPanel")
            expandMethod.invoke(statusBarService)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Locks the screen immediately without pressing the physical power button (requires AccessibilityService).
     */
    fun lockScreenNow(): Boolean {
        return FoldAccessibilityService.instance?.lockScreen() ?: false
    }

    /**
     * Triggers Android split-screen mode via AccessibilityService.
     */
    fun toggleSplitScreen(): Boolean {
        return FoldAccessibilityService.instance?.toggleSplitScreen() ?: false
    }

    /**
     * Launches two apps side-by-side in multi-window / split-screen mode on the Z Fold 8 inner display.
     */
    fun launchSplitScreenPair(
        context: Context,
        primaryPackage: String,
        secondaryPackage: String
    ): Boolean {
        val pm = context.packageManager
        val primaryIntent = pm.getLaunchIntentForPackage(primaryPackage)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return false

        val secondaryIntent = pm.getLaunchIntentForPackage(secondaryPackage)?.apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            )
        } ?: return false

        return try {
            context.startActivity(primaryIntent)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    context.startActivity(secondaryIntent)
                } catch (_: Exception) {
                }
            }, 180L)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Finds the installed VC package (`dev.agentconversation.vci` or `dev.agentconversation.preview`).
     */
    fun findInstalledVcPackage(context: Context): String? {
        val pm = context.packageManager
        for (pkg in listOf(VC_PROD_PACKAGE, VC_PREVIEW_PACKAGE)) {
            if (pm.getLaunchIntentForPackage(pkg) != null) return pkg
        }
        return null
    }

    /**
     * Launches the installed VC app (`dev.agentconversation.vci` or `dev.agentconversation.preview`),
     * optionally passing session/project extras, or falls back to the VC web UI URL.
     */
    fun launchVc(
        context: Context,
        sessionId: String? = null,
        projectId: String? = null,
        startVoiceTalk: Boolean = false,
        adjacentSplitScreen: Boolean = false
    ): Boolean {
        val vcPkg = findInstalledVcPackage(context)
        if (vcPkg != null) {
            val intent = context.packageManager.getLaunchIntentForPackage(vcPkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (adjacentSplitScreen) {
                    addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
                if (startVoiceTalk) {
                    action = VC_TALK_ACTION
                }
                if (!sessionId.isNullOrBlank()) {
                    putExtra("sessionId", sessionId)
                    putExtra("focusSession", sessionId)
                }
                if (!projectId.isNullOrBlank()) {
                    putExtra("projectId", projectId)
                }
            }
            if (intent != null) {
                return try {
                    context.startActivity(intent)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
        return try {
            val url = if (!sessionId.isNullOrBlank()) {
                "$DEFAULT_VC_HOST_URL/?session=$sessionId"
            } else {
                DEFAULT_VC_HOST_URL
            }
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getAdbGrantCommand(context: Context): String {
        return "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun isDexDesktopModeActive(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_TYPE_MASK
        if (uiMode == android.content.res.Configuration.UI_MODE_TYPE_DESK) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.resources.configuration.smallestScreenWidthDp >= 900
    }
}
