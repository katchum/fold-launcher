package com.xsun.foldlauncher.model

import android.content.ComponentName
import androidx.compose.ui.graphics.ImageBitmap

data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String = "",
    val icon: ImageBitmap? = null,
    val launchCount: Int = 0,
    val isPinnedCover: Boolean = false,
    val isPinnedInner: Boolean = false,
    val isDockPinned: Boolean = false,
    val category: String = "Apps",
    val shortcuts: List<AppShortcutItem> = emptyList(),
    val isSystemApp: Boolean = category.equals("System", ignoreCase = true) ||
        category.equals("Utilities", ignoreCase = true) ||
        packageName.startsWith("com.android.") ||
        packageName.startsWith("com.sec.android.") ||
        packageName.startsWith("com.samsung.android.")
) {
    val componentName: ComponentName
        get() = ComponentName(packageName, activityName)

    val normalizedLabel: String = label.lowercase().trim()

    val acronym: String = label
        .split(Regex("[\\s\\-_/.]+"))
        .filter { it.isNotEmpty() }
        .map { it[0].lowercaseChar() }
        .joinToString("")

    val firstLetter: Char = label.trim().firstOrNull()?.uppercaseChar()?.let {
        if (it in 'A'..'Z') it else '#'
    } ?: '#'
}

data class AppPair(
    val id: String,
    val title: String,
    val primaryPackage: String,
    val secondaryPackage: String
)

data class FolderItem(
    val id: String,
    val name: String,
    val packageNames: List<String> = emptyList(),
    val isCoverFolder: Boolean = true,
    val isInnerFolder: Boolean = true
)

data class AppShortcutItem(
    val id: String,
    val packageName: String,
    val shortLabel: String,
    val longLabel: String = shortLabel
)
