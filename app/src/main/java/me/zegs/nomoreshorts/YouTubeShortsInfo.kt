package me.zegs.nomoreshorts

import android.view.accessibility.AccessibilityNodeInfo

data class YouTubeShortsInfo(
    val title: String,
    val account: String,
    val backButton: AccessibilityNodeInfo?
)
