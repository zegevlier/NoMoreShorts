package me.zegs.nomoreshorts

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import me.zegs.nomoreshorts.models.BlockingMode
import me.zegs.nomoreshorts.models.LimitType
import me.zegs.nomoreshorts.models.PreferenceKeys
import me.zegs.nomoreshorts.session.SessionManager
import me.zegs.nomoreshorts.settings.SettingsManager

class ShortsAccessibilityService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val TAG = "ShortsAccessibilityService"
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val BACK_ACTION_COOLDOWN_MS = 100L
        private const val CACHE_CLEANUP_INTERVAL_MS = 30000L // 30 seconds
    }

    // State management
    private var lastShortWatched: YouTubeShortsInfo? = null
    private var settingsManager: SettingsManager? = null
    private var sessionManager: SessionManager? = null
    private var lastShortsClosedTime: Long = 0
    private var isServiceInitialized: Boolean = false

    // Performance optimization fields
    private var lastAppEnabledCheck: Long = 0
    private var cachedAppEnabled: Boolean = false
    private val handler = Handler(Looper.getMainLooper())
    private var cacheCleanupRunnable: Runnable? = null

    override fun onServiceConnected() {
        try {
            super.onServiceConnected()
            Log.d(TAG, "Accessibility service connected")

            initializeService()
            scheduleCacheCleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Error during service connection", e)
            handleInitializationError(e)
        }
    }

    private fun initializeService() {
        try {
            settingsManager = SettingsManager(this)

            settingsManager?.let { sm ->
                sessionManager = SessionManager(sm)

                // Setup session callbacks with error handling
                sessionManager?.let { sessMgr ->
                    sessMgr.onSessionReset = {
                        try {
                            Log.d(TAG, "Session reset")
                            // Clear cache on session reset
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in session reset callback", e)
                        }
                    }

                    sessMgr.onLimitReached = {
                        try {
                            // Force close any current shorts and show detailed limit message
                            lastShortWatched?.let { shortsInfo ->
                                val detailedMessage = sessMgr.getLimitReachedMessage(this@ShortsAccessibilityService)
                                // We also need to reset the last watched shorts to avoid repeated closures
                                lastShortWatched = null
                                closeShorts(shortsInfo, detailedMessage)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in limit reached callback", e)
                            showToast(getString(R.string.shorts_swipe_limit_reached))
                        }
                    }
                }

                sm.addSharedPreferenceChangeListener(this)
                isServiceInitialized = true
                Log.d(TAG, "Service initialized successfully")
            } ?: throw IllegalStateException("Failed to initialize SettingsManager")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize service", e)
            handleInitializationError(e)
        }
    }

    private fun scheduleCacheCleanup() {
        cacheCleanupRunnable = Runnable {
            try {
                // Schedule next cleanup
                handler.postDelayed(cacheCleanupRunnable!!, CACHE_CLEANUP_INTERVAL_MS)
            } catch (e: Exception) {
                Log.e(TAG, "Error during cache cleanup", e)
            }
        }
        handler.postDelayed(cacheCleanupRunnable!!, CACHE_CLEANUP_INTERVAL_MS)
    }

    private fun handleInitializationError(e: Exception) {
        try {
            showToast("Accessibility service failed to initialize. Please restart the app.")
            isServiceInitialized = false
        } catch (toastError: Exception) {
            Log.e(TAG, "Failed to show initialization error toast", toastError)
        }
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
            Log.d(TAG, "Accessibility service destroying")

            // Cancel scheduled tasks
            cacheCleanupRunnable?.let { handler.removeCallbacks(it) }

            settingsManager?.let { sm ->
                try {
                    sm.removeSharedPreferenceChangeListener(this)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing preference change listener", e)
                }
            }

            sessionManager?.let { sessMgr ->
                try {
                    sessMgr.cleanup()
                } catch (e: Exception) {
                    Log.e(TAG, "Error cleaning up session manager", e)
                }
            }

            isServiceInitialized = false
            Log.d(TAG, "Service destroyed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during service destruction", e)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            if (!isServiceInitialized) {
                Log.w(TAG, "Service not initialized, ignoring preference change")
                return
            }

            // Invalidate app enabled cache when relevant preferences change
            when (key) {
                PreferenceKeys.APP_ENABLED,
                PreferenceKeys.SCHEDULE_ENABLED,
                PreferenceKeys.SCHEDULE_START_TIME,
                PreferenceKeys.SCHEDULE_END_TIME,
                PreferenceKeys.SCHEDULE_DAYS -> {
                    lastAppEnabledCheck = 0 // Force re-check on next access
                }
                PreferenceKeys.RESET_PERIOD_TYPE,
                PreferenceKeys.RESET_PERIOD_MINUTES,
                PreferenceKeys.LIMIT_TYPE,
                PreferenceKeys.SWIPE_LIMIT_COUNT,
                PreferenceKeys.TIME_LIMIT_MINUTES -> {
                    sessionManager?.let { sessMgr ->
                        try {
                            sessMgr.updateResetSchedule()
                            Log.d(TAG, "Updated reset schedule for preference: $key")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating reset schedule for preference: $key", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling preference change for key: $key", e)
        }
    }

    private fun isAppEnabled(): Boolean {
        return try {
            val currentTime = System.currentTimeMillis()

            // Use cached result if it's recent (within 1 second)
            if (currentTime - lastAppEnabledCheck < 1000) {
                return cachedAppEnabled
            }

            val settings = settingsManager
            if (settings == null) {
                Log.w(TAG, "SettingsManager is null, treating as disabled")
                cachedAppEnabled = false
                lastAppEnabledCheck = currentTime
                return false
            }

            cachedAppEnabled = settings.isAppEnabled && settings.isInSchedule()
            lastAppEnabledCheck = currentTime
            cachedAppEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if app is enabled", e)
            false // Default to disabled on error
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            // Only process events if the service is properly initialized and app is enabled
            if (!isServiceInitialized || !isAppEnabled()) {
                return
            }

            // Only process YouTube events
            if (event?.packageName != YOUTUBE_PACKAGE) {
                Log.v(TAG, "Ignoring event from package: ${event?.packageName}")
                return
            }

            event.let { accessibilityEvent ->
                processAccessibilityEvent(accessibilityEvent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        }
    }

    private fun processAccessibilityEvent(event: AccessibilityEvent) {
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    handleWindowContentChanged()
                }
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    handleWindowStateChanged()
                }
                else -> {
                    // Log other event types for debugging if needed
                    Log.v(TAG, "Unhandled event type: ${event.eventType}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing event type: ${event.eventType}", e)
        }
    }

    private fun handleWindowContentChanged() {
        try {
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.v(TAG, "Root node is null, skipping content change")
                return
            }

            val shortsInfo = extractShortsInfo(rootNode)
            if (shortsInfo != null) {
                processShortsInfo(shortsInfo)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling window content changed", e)
        }
    }

    private fun handleWindowStateChanged() {
        try {
            // This event is triggered when the active window changes
            // We can reset the last watched shorts content here
            lastShortWatched = null
            Log.v(TAG, "Window state changed, reset last watched short")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling window state changed", e)
        }
    }

    private fun processShortsInfo(shortsInfo: YouTubeShortsInfo) {
        try {
            if (lastShortWatched == shortsInfo) {
                return // Skip if the content is the same as the last watched
            }

            if (lastShortWatched != null) {
                // Handle new shorts content (swipe)
                lastShortWatched = shortsInfo
                handleShortsSwipe(shortsInfo)
            } else {
                // Handle entering shorts for the first time
                lastShortWatched = shortsInfo
                handleShortsEntered(shortsInfo)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing shorts info", e)
        }
    }

    private fun handleShortsEntered(shortsInfo: YouTubeShortsInfo) {
        try {
            Log.d(TAG, "Entered shorts: ${shortsInfo.title} by ${shortsInfo.account}")

            // Start a new session when entering shorts
            sessionManager?.let { sessMgr ->
                try {
                    sessMgr.startSession()
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting session", e)
                }
            }

            val settings = settingsManager ?: run {
                Log.e(TAG, "SettingsManager is null in handleShortsEntered")
                return
            }

            // Check various blocking conditions
            when {
                shortsInfo.backButton == null && settings.blockShortsFeed -> {
                    closeShorts(shortsInfo, getString(R.string.shorts_feed_blocked))
                }
                settings.blockingMode == BlockingMode.ALL_SHORTS -> {
                    closeShorts(shortsInfo, getString(R.string.all_shorts_blocked))
                }
                settings.blockingMode == BlockingMode.ONLY_SWIPING &&
                settings.limitType == LimitType.SWIPE_COUNT -> {
                    handleSwipeLimitOnEntry(shortsInfo, settings)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling shorts entered", e)
            // On error, show generic blocking message
            try {
                closeShorts(shortsInfo, getString(R.string.shorts_blocked))
            } catch (closeError: Exception) {
                Log.e(TAG, "Error closing shorts after handling error", closeError)
            }
        }
    }

    private fun handleSwipeLimitOnEntry(shortsInfo: YouTubeShortsInfo, settings: SettingsManager) {
        try {
            if (settings.swipeLimitCount == 0 && shortsInfo.backButton == null) {
                closeShorts(shortsInfo, getString(R.string.no_shorts_feed_zero_swipe))
                return
            }

            sessionManager?.let { sessMgr ->
                if (sessMgr.isLimitReached() && settings.swipeLimitCount != 0) {
                    closeShorts(shortsInfo, getString(R.string.shorts_swipe_limit_reached))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling swipe limit on entry", e)
        }
    }

    private fun handleShortsSwipe(shortsInfo: YouTubeShortsInfo) {
        try {
            Log.d(TAG, "Swiped to: ${shortsInfo.title} by ${shortsInfo.account}")

            val settings = settingsManager ?: run {
                Log.e(TAG, "SettingsManager is null in handleShortsSwipe")
                return
            }

            when (settings.blockingMode) {
                BlockingMode.ALL_SHORTS -> {
                    closeShorts(shortsInfo, getString(R.string.all_shorts_blocked))
                }
                BlockingMode.ONLY_SWIPING -> {
                    sessionManager?.let { sessMgr ->
                        try {
                            // Add a swipe and update time - this will check limits and potentially end the session
                            sessMgr.addSwipeAndUpdateTime()
                            // If the limit was hit, that will be dealt with in the session manager callback
                        } catch (e: Exception) {
                            Log.e(TAG, "Error adding swipe and updating time", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling shorts swipe", e)
        }
    }

    private fun closeShorts(shortsInfo: YouTubeShortsInfo, reason: String = getString(R.string.shorts_blocked)) {
        try {
            // Check if channel is in allowlist
            if (isChannelAllowed(shortsInfo.account)) {
                Log.d(TAG, "Channel ${shortsInfo.account} is in allowlist, not closing shorts")
                return
            }

            Log.d(TAG, "Closing shorts: $reason")

            // Show toast notification explaining why shorts were closed
            showToast(reason)

            val currentTime = System.currentTimeMillis()
            var shortsClosed = false

            // Try to close using back button first
            shortsInfo.backButton?.let { backButton ->
                if (performBackButtonAction(backButton)) {
                    shortsClosed = true
                    Log.d(TAG, "Closed shorts using back button")
                }
            }

            // Fallback to global back action if needed
            if (!shortsClosed) {
                performGlobalBackAction(currentTime)
            }

            lastShortsClosedTime = currentTime
        } catch (e: Exception) {
            Log.e(TAG, "Error closing shorts", e)
            // Try emergency fallback
            try {
                performGlobalAction(GLOBAL_ACTION_BACK)
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Emergency fallback failed", fallbackError)
            }
        }
    }

    private fun isChannelAllowed(account: String): Boolean {
        return try {
            val settings = settingsManager ?: return false

            if (!settings.allowlistEnabled) {
                return false
            }

            settings.allowedChannels.any { allowedChannel ->
                val normalizedAllowed = allowedChannel.removePrefix("@").lowercase().trim()
                val normalizedAccount = account.removePrefix("@").lowercase().trim()
                normalizedAllowed == normalizedAccount
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if channel is allowed: $account", e)
            false // Default to not allowed on error
        }
    }

    private fun performBackButtonAction(backButton: AccessibilityNodeInfo): Boolean {
        return try {
            if (backButton.isClickable) {
                backButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                true
            } else {
                Log.w(TAG, "Back button is not clickable")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing back button action", e)
            false
        }
    }

    private fun performGlobalBackAction(currentTime: Long) {
        try {
            // We only want to perform the global back action if we haven't done it recently
            if (currentTime - lastShortsClosedTime < BACK_ACTION_COOLDOWN_MS) {
                Log.v(TAG, "Back action cooldown active, skipping global back")
                return
            }

            if (performGlobalAction(GLOBAL_ACTION_BACK)) {
                Log.d(TAG, "Performed global back action")
            } else {
                Log.w(TAG, "Failed to perform global back action")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing global back action", e)
        }
    }

    private fun showToast(message: String) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing toast: $message", e)
        }
    }

    // YouTube Shorts extraction functions
    private fun extractShortsInfo(rootNode: AccessibilityNodeInfo): YouTubeShortsInfo? {
        return try {
            // Validate package name first
            if (rootNode.packageName != YOUTUBE_PACKAGE) {
                Log.v(TAG, "Not YouTube app, package: ${rootNode.packageName}")
                return null
            }

            extractShortsInfoFromStructure(rootNode)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting shorts info", e)
            null
        }
    }

    private fun extractShortsInfoFromStructure(rootNode: AccessibilityNodeInfo): YouTubeShortsInfo? {
        return try {
            // Validate root structure
            if (rootNode.className != "android.widget.FrameLayout") {
                Log.v(TAG, "Root is not FrameLayout: ${rootNode.className}")
                return null
            }

            val drawerLayout = safeGetChild(rootNode, 0) ?: return null
            if (drawerLayout.className != "androidx.drawerlayout.widget.DrawerLayout") {
                Log.v(TAG, "DrawerLayout not found: ${drawerLayout.className}")
                return null
            }

            val secondFrameLayout = safeGetChild(drawerLayout, 0) ?: return null
            if (secondFrameLayout.className != "android.widget.FrameLayout") {
                Log.v(TAG, "Second FrameLayout not found: ${secondFrameLayout.className}")
                return null
            }

            val thirdFrameLayout = safeGetChild(secondFrameLayout, 0) ?: return null
            if (thirdFrameLayout.className != "android.widget.FrameLayout") {
                Log.v(TAG, "Third FrameLayout not found: ${thirdFrameLayout.className}")
                return null
            }

            val scrollView = safeGetChild(thirdFrameLayout, 0) ?: return null
            if (scrollView.className != "android.widget.ScrollView") {
                Log.v(TAG, "ScrollView not found: ${scrollView.className}")
                return null
            }

            val (recyclerView, backViewGroup) = findScrollViewChildren(scrollView)
            val (title, account) = extractTitleAndAccount(recyclerView)
            val backButton = extractBackButton(backViewGroup)

            // Return YouTubeShortsInfo if we have both title and account, otherwise null
            if (title.isNotEmpty() && account.isNotEmpty()) {
                YouTubeShortsInfo(title, account, backButton)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting from structure", e)
            null
        }
    }

    private fun safeGetChild(node: AccessibilityNodeInfo, index: Int): AccessibilityNodeInfo? {
        return try {
            if (index < node.childCount) {
                node.getChild(index)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting child at index $index", e)
            null
        }
    }

    private fun findScrollViewChildren(scrollView: AccessibilityNodeInfo): Pair<AccessibilityNodeInfo?, AccessibilityNodeInfo?> {
        var recyclerView: AccessibilityNodeInfo? = null
        var backViewGroup: AccessibilityNodeInfo? = null

        try {
            for (i in 0 until scrollView.childCount) {
                val child = safeGetChild(scrollView, i) ?: continue
                when (child.className) {
                    "android.support.v7.widget.RecyclerView" -> recyclerView = child
                    "android.view.ViewGroup" -> backViewGroup = child
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding scroll view children", e)
        }

        return Pair(recyclerView, backViewGroup)
    }

    private fun extractTitleAndAccount(recyclerView: AccessibilityNodeInfo?): Pair<String, String> {
        if (recyclerView == null) {
            Log.v(TAG, "RecyclerView is null")
            return Pair("", "")
        }

        return try {
            val frameLayout = safeGetChild(recyclerView, 0)
            if (frameLayout?.className != "android.widget.FrameLayout") {
                return Pair("", "")
            }

            var title = ""
            var account = ""

            for (i in 0 until frameLayout.childCount) {
                val viewGroup = safeGetChild(frameLayout, i) ?: continue

                if (viewGroup.className == "android.view.ViewGroup" && viewGroup.childCount > 0) {
                    for (j in 0 until viewGroup.childCount) {
                        val shortsInfoGroup = safeGetChild(viewGroup, j) ?: continue

                        if (shortsInfoGroup.className == "android.view.ViewGroup" && shortsInfoGroup.childCount > 0) {
                            // Try to extract account information
                            if (account.isEmpty()) {
                                account = extractAccountFromShortsInfo(shortsInfoGroup)
                            }

                            // Try to extract title information
                            if (title.isEmpty()) {
                                title = extractTitleFromShortsInfo(shortsInfoGroup)
                            }
                        }
                    }
                }
            }

            Pair(title, account)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting title and account", e)
            Pair("", "")
        }
    }

    private fun extractAccountFromShortsInfo(shortsInfoGroup: AccessibilityNodeInfo): String {
        return try {
            val accountView2 = safeGetChild(shortsInfoGroup, 0) ?: return ""
            if (accountView2.className == "android.view.ViewGroup" && accountView2.childCount > 1) {
                val accountView3 = safeGetChild(accountView2, 1) ?: return ""
                if (accountView3.className == "android.view.ViewGroup") {
                    val accountNode = safeGetChild(accountView3, 0) ?: return ""
                    if (accountNode.className == "android.view.ViewGroup") {
                        return accountNode.text?.toString() ?: ""
                    }
                }
            }
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting account", e)
            ""
        }
    }

    private fun extractTitleFromShortsInfo(shortsInfoGroup: AccessibilityNodeInfo): String {
        return try {
            val titleGroup2 = safeGetChild(shortsInfoGroup, 0) ?: return ""
            if (titleGroup2.className == "android.view.ViewGroup") {
                val titleGroup3 = safeGetChild(titleGroup2, 0) ?: return ""
                if (titleGroup3.className == "android.view.ViewGroup") {
                    val titleNode = safeGetChild(titleGroup3, 0) ?: return ""
                    if (titleNode.className == "android.view.ViewGroup") {
                        return titleNode.text?.toString() ?: ""
                    }
                }
            }
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting title", e)
            ""
        }
    }

    private fun extractBackButton(backViewGroup: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        return try {
            if (backViewGroup != null) {
                // Make sure the first child is an ImageButton, and if so, we store it as the back button
                val backButtonNode = safeGetChild(backViewGroup, 0)
                if (backButtonNode?.className == "android.widget.ImageButton") {
                    return backButtonNode
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting back button", e)
            null
        }
    }

    override fun onInterrupt() {
        try {
            Log.d(TAG, "Accessibility service interrupted")
            // Handle service interruption here
            isServiceInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error handling service interruption", e)
        }
    }
}
