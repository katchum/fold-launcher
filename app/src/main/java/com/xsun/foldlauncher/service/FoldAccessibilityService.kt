package com.xsun.foldlauncher.service

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.xsun.foldlauncher.vc.VcBackgroundMonitor
import com.xsun.foldlauncher.vc.VcPackages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight, zero-bloat companion AccessibilityService for Galaxy Z Fold 8 & slab phones.
 *
 * Capabilities:
 * 1. Tracks the active foreground package (`foregroundPackage` & `isVcInForeground`) so the
 *    launcher's VC background monitor knows when `dev.agentconversation.vci` or
 *    `dev.agentconversation.preview` is in the foreground vs. background.
 * 2. Watches `Settings.Secure.DEFAULT_INPUT_METHOD` and configuration changes (such as Samsung DeX
 *    connecting/disconnecting) and automatically restores Gboard if One UI forces Samsung Keyboard.
 * 3. Performs global actions on behalf of the launcher:
 *    - Double-tap home screen to lock (`GLOBAL_ACTION_LOCK_SCREEN`)
 *    - Swipe down on home screen to pull down notifications (`GLOBAL_ACTION_NOTIFICATIONS`)
 *    - Toggle split-screen multitasking (`GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN`)
 */
class FoldAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var imeObserver: ContentObserver? = null

    var autoGuardDexKeyboard: Boolean = true

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        registerImeGuardObserver()
        if (autoGuardDexKeyboard && SystemTweaker.isSamsungKeyboardActive(this)) {
            SystemTweaker.enforcePreferredIme(this)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // When Samsung DeX connects or display posture changes, guard against Samsung Keyboard hijack
        if (autoGuardDexKeyboard && SystemTweaker.isSamsungKeyboardActive(this)) {
            SystemTweaker.enforcePreferredIme(this)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return

            if (autoGuardDexKeyboard && pkg == SystemTweaker.SAMSUNG_HONEYBOARD_PACKAGE) {
                SystemTweaker.enforcePreferredIme(this)
                return
            }

            // Ignore transient system overlays (status bar / volume / keyboard) when tracking foreground app
            if (!IGNORED_OVERLAY_PACKAGES.contains(pkg)) {
                _foregroundPackage.value = pkg
                val vcActive = VcPackages.isVcPackage(pkg) || VC_PACKAGES.contains(pkg)
                if (_isVcInForeground.value != vcActive) {
                    _isVcInForeground.value = vcActive
                    _lastVcStateChangeMs.value = System.currentTimeMillis()
                }
                VcBackgroundMonitor.getInstance(this).onForegroundPackageChanged(pkg)
            }
        }
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        unregisterImeGuardObserver()
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    fun lockScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            false
        }
    }

    fun openNotificationShade(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    fun openQuickSettings(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }

    fun toggleSplitScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
        } else {
            false
        }
    }

    private fun registerImeGuardObserver() {
        val uri: Uri = Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD) ?: return
        val observer = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                if (autoGuardDexKeyboard && SystemTweaker.isSamsungKeyboardActive(this@FoldAccessibilityService)) {
                    SystemTweaker.enforcePreferredIme(this@FoldAccessibilityService)
                }
            }
        }
        try {
            contentResolver.registerContentObserver(uri, false, observer)
            imeObserver = observer
        } catch (_: Exception) {
        }
    }

    private fun unregisterImeGuardObserver() {
        val observer = imeObserver ?: return
        try {
            contentResolver.unregisterContentObserver(observer)
        } catch (_: Exception) {
        }
        imeObserver = null
    }

    companion object {
        val VC_PACKAGES = setOf(
            "dev.agentconversation",
            "dev.agentconversation.preview",
            "dev.agentconversation.preview.preflight",
            "dev.agentconversation.vci",
            "com.example.agentconversation"
        )

        private val IGNORED_OVERLAY_PACKAGES = setOf(
            "com.android.systemui",
            "com.samsung.android.honeyboard",
            "com.google.android.inputmethod.latin",
            "com.touchtype.swiftkey",
            "android"
        )

        @Volatile
        var instance: FoldAccessibilityService? = null
            private set

        private val _foregroundPackage = MutableStateFlow("")
        val foregroundPackage: StateFlow<String> = _foregroundPackage.asStateFlow()

        private val _isVcInForeground = MutableStateFlow(false)
        val isVcInForeground: StateFlow<Boolean> = _isVcInForeground.asStateFlow()

        private val _lastVcStateChangeMs = MutableStateFlow(0L)
        val lastVcStateChangeMs: StateFlow<Long> = _lastVcStateChangeMs.asStateFlow()

        fun notifyLauncherResumed(packageName: String) {
            _foregroundPackage.value = packageName
            if (_isVcInForeground.value) {
                _isVcInForeground.value = false
                _lastVcStateChangeMs.value = System.currentTimeMillis()
            }
        }
    }
}
