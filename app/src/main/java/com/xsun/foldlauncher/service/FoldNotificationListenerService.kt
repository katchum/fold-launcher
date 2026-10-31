package com.xsun.foldlauncher.service

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

data class MipNotificationAction(
    val title: String,
    val actionIntent: PendingIntent?
)

data class MipNotificationItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val subText: String?,
    val postTime: Long,
    val isClearable: Boolean,
    val isOngoing: Boolean,
    val count: Int,
    val contentIntent: PendingIntent?,
    val actions: List<MipNotificationAction>
)

class FoldNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instanceRef = WeakReference(this)
        _isListenerConnectedFlow.value = true
        refreshNotificationsInternal()
    }

    override fun onListenerDisconnected() {
        if (instanceRef?.get() === this) {
            instanceRef = null
        }
        _isListenerConnectedFlow.value = false
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshNotificationsInternal()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshNotificationsInternal()
    }

    private fun refreshNotificationsInternal() {
        val rawArray = try {
            activeNotifications ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }
        val pm = packageManager
        val appLabelCache = HashMap<String, String>()
        val items = ArrayList<MipNotificationItem>(rawArray.size)

        for (sbn in rawArray) {
            val notification = sbn.notification ?: continue
            val extras = notification.extras ?: Bundle.EMPTY

            val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)
            val rawText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)
            val rawSubText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)

            val title = rawTitle?.toString()?.trim().orEmpty()
            val text = rawText?.toString()?.trim().orEmpty()
            val subText = rawSubText?.toString()?.trim()?.takeIf { it.isNotEmpty() }

            // Skip empty group summary placeholders or blank notifications
            if (title.isEmpty() && text.isEmpty()) continue
            val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            if (isGroupSummary && text.isEmpty()) continue

            val pkg = sbn.packageName.orEmpty()
            val appName = appLabelCache.getOrPut(pkg) {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString().trim().ifEmpty { pkg }
                } catch (_: Exception) {
                    pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                }
            }

            val parsedActions = notification.actions?.mapNotNull { act ->
                val actTitle = act.title?.toString()?.trim().orEmpty()
                if (actTitle.isEmpty()) null else MipNotificationAction(actTitle, act.actionIntent)
            } ?: emptyList()

            items.add(
                MipNotificationItem(
                    key = sbn.key ?: "${pkg}_${sbn.id}_${sbn.postTime}",
                    packageName = pkg,
                    appName = appName,
                    title = title.ifEmpty { appName },
                    text = text,
                    subText = subText,
                    postTime = sbn.postTime,
                    isClearable = sbn.isClearable,
                    isOngoing = sbn.isOngoing,
                    count = notification.number.coerceAtLeast(1),
                    contentIntent = notification.contentIntent,
                    actions = parsedActions
                )
            )
        }

        val sorted = items
            .sortedWith(compareBy<MipNotificationItem> { it.isOngoing }.thenByDescending { it.postTime })
            .take(30)

        _activeNotificationsFlow.value = sorted
    }

    companion object {
        private const val KEY_ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"

        @Volatile
        private var instanceRef: WeakReference<FoldNotificationListenerService>? = null

        private val _activeNotificationsFlow = MutableStateFlow<List<MipNotificationItem>>(emptyList())
        val activeNotificationsFlow: StateFlow<List<MipNotificationItem>> = _activeNotificationsFlow.asStateFlow()

        private val _isListenerConnectedFlow = MutableStateFlow(false)
        val isListenerConnectedFlow: StateFlow<Boolean> = _isListenerConnectedFlow.asStateFlow()

        fun isNotificationListenerEnabled(context: Context): Boolean {
            if (_isListenerConnectedFlow.value && instanceRef?.get() != null) return true
            val cr = context.contentResolver
            val flat = try {
                Settings.Secure.getString(cr, KEY_ENABLED_NOTIFICATION_LISTENERS).orEmpty()
            } catch (_: Exception) {
                ""
            }
            if (flat.contains(context.packageName)) return true

            if (SystemTweaker.hasWriteSecureSettings(context)) {
                return try {
                    val component = "${context.packageName}/${FoldNotificationListenerService::class.java.canonicalName}"
                    val updated = if (flat.isBlank()) component else "$flat:$component"
                    Settings.Secure.putString(cr, KEY_ENABLED_NOTIFICATION_LISTENERS, updated)
                    true
                } catch (_: Exception) {
                    false
                }
            }
            return false
        }

        fun openNotificationListenerSettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }

        fun refreshNotifications() {
            instanceRef?.get()?.refreshNotificationsInternal()
        }

        private fun buildBackgroundActivityStartBundle(): Bundle? {
            return try {
                val opts = ActivityOptions.makeBasic()
                if (Build.VERSION.SDK_INT >= 34) {
                    try {
                        val method = ActivityOptions::class.java.getMethod(
                            "setPendingIntentBackgroundActivityStartMode",
                            Int::class.javaPrimitiveType
                        )
                        // MODE_BACKGROUND_ACTIVITY_START_ALLOWED = 1
                        method.invoke(opts, 1)
                    } catch (_: Exception) {
                    }
                }
                opts.toBundle()
            } catch (_: Exception) {
                null
            }
        }

        fun fireNotificationIntent(context: Context, item: MipNotificationItem): Boolean {
            val pending = item.contentIntent
            if (pending != null) {
                val sent = try {
                    val optionsBundle = buildBackgroundActivityStartBundle()
                    if (optionsBundle != null) {
                        pending.send(context, 0, null, null, null, null, optionsBundle)
                    } else {
                        pending.send()
                    }
                    if (item.isClearable) {
                        dismissNotification(item.key)
                    }
                    true
                } catch (_: Exception) {
                    try {
                        pending.send()
                        if (item.isClearable) {
                            dismissNotification(item.key)
                        }
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
                if (sent) return true
            }

            return try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(item.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                } ?: return false
                context.startActivity(launchIntent)
                if (item.isClearable) {
                    dismissNotification(item.key)
                }
                true
            } catch (_: Exception) {
                false
            }
        }

        fun fireActionIntent(context: Context, action: MipNotificationAction): Boolean {
            val pending = action.actionIntent ?: return false
            return try {
                val optionsBundle = buildBackgroundActivityStartBundle()
                if (optionsBundle != null) {
                    pending.send(context, 0, null, null, null, null, optionsBundle)
                } else {
                    pending.send()
                }
                refreshNotifications()
                true
            } catch (_: Exception) {
                try {
                    pending.send()
                    refreshNotifications()
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }

        fun dismissNotification(key: String) {
            try {
                instanceRef?.get()?.cancelNotification(key)
            } catch (_: Exception) {
            }
            _activeNotificationsFlow.value = _activeNotificationsFlow.value.filterNot { it.key == key }
        }

        fun dismissAllNotifications() {
            try {
                instanceRef?.get()?.cancelAllNotifications()
            } catch (_: Exception) {
            }
            _activeNotificationsFlow.value = _activeNotificationsFlow.value.filter { it.isOngoing }
        }
    }
}
