package me.zegs.nomoreshorts

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import me.zegs.nomoreshorts.models.BlockingMode
import me.zegs.nomoreshorts.models.LimitType
import me.zegs.nomoreshorts.models.PreferenceKeys
import me.zegs.nomoreshorts.session.SessionManager
import me.zegs.nomoreshorts.settings.SettingsManager

class ShortsAccessibilityService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    // A string storing the last found shorts content
    private var lastShortWatched: YouTubeShortsInfo? = null
    private lateinit var settingsManager: SettingsManager
    private lateinit var sessionManager: SessionManager
    private var lastBackButtonTime: Long = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsManager = SettingsManager(this)
        sessionManager = SessionManager(settingsManager)

        // Setup session callbacks
        sessionManager.onSessionReset = {
            println("Session has been reset - limits cleared")
        }

        sessionManager.onLimitReached = {
            println("Session limit reached - blocking shorts")
            // Force close any current shorts
            lastShortWatched?.let { closeShorts(it) }
        }

        settingsManager.addSharedPreferenceChangeListener(this)
        println("Accessibility Service Connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::settingsManager.isInitialized) {
            settingsManager.removeSharedPreferenceChangeListener(this)
        }
        if (::sessionManager.isInitialized) {
            sessionManager.cleanup()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        // React to settings changes and update session scheduling if needed
        when (key) {
            PreferenceKeys.RESET_PERIOD_TYPE,
            PreferenceKeys.RESET_PERIOD_MINUTES,
            PreferenceKeys.LIMIT_TYPE,
            PreferenceKeys.SWIPE_LIMIT_COUNT,
            PreferenceKeys.TIME_LIMIT_MINUTES -> {
                println("Session-related setting changed: $key")
                if (::sessionManager.isInitialized) {
                    sessionManager.updateResetSchedule()
                }
            }
        }
    }

    private fun isAppEnabled(): Boolean {
        return settingsManager.isAppEnabled && settingsManager.isInSchedule()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Only process events if the app is enabled and in schedule
        if (!::settingsManager.isInitialized || !isAppEnabled()) {
            return
        }

        event?.let {
            if (it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                val rootNode = rootInActiveWindow
                rootNode?.let { node ->
                    val shortsInfo = extractShortsInfo(node)
                    if (shortsInfo != null) {
                        if (lastShortWatched == shortsInfo) {
                            println("Shorts content is the same as last watched, skipping")
                            return // Skip if the content is the same as the last watched
                        } else if (lastShortWatched != null) {
                            // Handle new shorts content
                            handleShortsSwipe(shortsInfo)
                        } else {
                            println("First Shorts content detected: ${shortsInfo.title} by ${shortsInfo.account}")
                            // Handle entering shorts for the first time
                            handleShortsEntered(shortsInfo)
                        }
                        lastShortWatched = shortsInfo // Update the last watched shorts content
                    }
                }
            } else if (it.eventType == AccessibilityEvent.WINDOWS_CHANGE_ACTIVE) {
                println("Active window changed, resetting lastShortWatched")
                lastShortWatched = null
            } else {
                println("Unhandled event type: ${it.eventType} - ${it.className} - ${it.packageName}")
            }
        }
    }

    private fun handleShortsEntered(shortsInfo: YouTubeShortsInfo) {
        // This function is called when we enter shorts from any source
        println("Entered Shorts: ${shortsInfo.title} by ${shortsInfo.account} - ${sessionManager.isLimitReached()}")

        if (sessionManager.isLimitReached() && (!(settingsManager.limitType == LimitType.SWIPE_COUNT && settingsManager.swipeLimitCount == 0)) || shortsInfo.backButton == null) {
            // We've reached the limit, so we need to kick the user out of shorts
            println("Session limit reached, closing shorts immediately")
            closeShorts(shortsInfo)
            return
        }
        // Start a new session when entering shorts, if there isn't one already
        sessionManager.startSession()

        if (shortsInfo.backButton == null && settingsManager.blockShortsFeed) {
            println("We just entered the shorts tab and blocking is enabled, closing the shorts")
            closeShorts(shortsInfo)
        } else if (settingsManager.blockingMode == BlockingMode.ALL_SHORTS) {
            // If we are blocking all shorts, we close the shorts
            println("Blocking all shorts, closing the shorts")
            closeShorts(shortsInfo)
        }
    }

    private fun handleShortsSwipe(shortsInfo: YouTubeShortsInfo) {
        // This is called when swiping to new content within shorts
        println("New Shorts content detected: ${shortsInfo.title} by ${shortsInfo.account}")

        when (settingsManager.blockingMode) {
            BlockingMode.ALL_SHORTS -> {
                closeShorts(shortsInfo)
            }

            BlockingMode.ONLY_SWIPING -> {
                // Add a swipe and update time - this will check limits and potentially end the session
                sessionManager.addSwipeAndUpdateTime()

                // Only continue if the session wasn't ended by the limit check above
                if (sessionManager.isLimitReached()) {
                    // Handle blocking based on mode
                    closeShorts(shortsInfo)
                }
            }
        }
    }

    private fun closeShorts(shortsInfo: YouTubeShortsInfo) {
        // We never close shorts if the channel is in the allowlist
        // However, these do count towards limits, so we only perform that check here
        if (settingsManager.allowlistEnabled &&
            settingsManager.allowedChannels.any { allowedChannel ->
                val normalizedAllowed = allowedChannel.removePrefix("@").lowercase()
                val normalizedAccount = shortsInfo.account.removePrefix("@").lowercase()
                normalizedAllowed == normalizedAccount
            }
        ) {
            println("Channel ${shortsInfo.account} is in allowlist, allowing shorts")
            return
        }
        var shortsClosed = false
        if (shortsInfo.backButton != null) {
            val backButton = shortsInfo.backButton
            if (backButton.isClickable) {
                println("Pressing back button to exit shorts")
                backButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                shortsClosed = true
            }
            lastBackButtonTime = System.currentTimeMillis()
        }
        if (!shortsClosed) {
            // We couldn't close shorts with the normal back button, so we'll trigger a global back action
            println("Couldn't close shorts with the back button, triggering global back action")
            // We only want to perform the global back action if we haven't done it in the last 2 seconds
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackButtonTime < 1000) {
                println("Global back action already performed recently, skipping")
                return
            }
            lastBackButtonTime = currentTime
            println("Performing global back action")
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun extractShortsInfo(rootNode: AccessibilityNodeInfo): YouTubeShortsInfo? {
        // This function detects whether the page is the YouTube Shorts page by checking if the structure
        // of the page matches the expected structure. This will break if YouTube changes the structure,
        // but it was the most reliable method with very few false positives.
        if (rootNode.packageName != "com.google.android.youtube") {
            return null
        }

        return try {
            extractShortsInfoFromStructure(rootNode)
        } catch (e: Exception) {
            println("Error extracting shorts info: ${e.message}")
            debugPrintTreeStructure("Error in tree structure", rootNode)
            e.printStackTrace()
            null
        }
    }

    private fun extractShortsInfoFromStructure(rootNode: AccessibilityNodeInfo): YouTubeShortsInfo? {
        if (rootNode.className != "android.widget.FrameLayout") {
            return null
        }

        val drawerLayout = rootNode.getChild(0)
        if (drawerLayout?.className != "androidx.drawerlayout.widget.DrawerLayout") {
            return null
        }

        val secondFrameLayout = drawerLayout.getChild(0)
        if (secondFrameLayout?.className != "android.widget.FrameLayout") {
            return null
        }

        val thirdFrameLayout = secondFrameLayout.getChild(0)
        if (thirdFrameLayout?.className != "android.widget.FrameLayout") {
            return null
        }

        val scrollView = thirdFrameLayout.getChild(0)
        if (scrollView?.className != "android.widget.ScrollView") {
            return null
        }

        val (recyclerView, backViewGroup) = findScrollViewChildren(scrollView)

        val (title, account) = extractTitleAndAccount(recyclerView)
        val backButton = extractBackButton(backViewGroup)

        // Return YouTubeShortsInfo if we have both title and account, otherwise null
        return if (title.isNotEmpty() && account.isNotEmpty()) {
            YouTubeShortsInfo(title, account, backButton)
        } else {
            null
        }
    }

    private fun findScrollViewChildren(scrollView: AccessibilityNodeInfo): Pair<AccessibilityNodeInfo?, AccessibilityNodeInfo?> {
        var recyclerView: AccessibilityNodeInfo? = null
        var backViewGroup: AccessibilityNodeInfo? = null

        for (i in 0 until scrollView.childCount) {
            val child = scrollView.getChild(i)
            when (child?.className) {
                "android.support.v7.widget.RecyclerView" -> recyclerView = child
                "android.view.ViewGroup" -> backViewGroup = child
            }
        }

        return Pair(recyclerView, backViewGroup)
    }

    private fun extractTitleAndAccount(recyclerView: AccessibilityNodeInfo?): Pair<String, String> {
        if (recyclerView == null) {
            return Pair("", "")
        }

        val frameLayout = recyclerView.getChild(0)
        if (frameLayout?.className != "android.widget.FrameLayout") {
            return Pair("", "")
        }

        var title = ""
        var account = ""

        for (i in 0 until frameLayout.childCount) {
            val viewGroup = frameLayout.getChild(i)
            if (viewGroup == null || viewGroup.childCount == 0) {
                continue // If there are no children, there is no title or account
            }

            if (viewGroup.className == "android.view.ViewGroup") {
                // It's one of the children, but which one seems to differ. We'll just try them all
                for (j in 0 until viewGroup.childCount) {
                    val shortsInfoGroup = viewGroup.getChild(j)
                    if (shortsInfoGroup != null && shortsInfoGroup.childCount == 0) {
                        continue // If there are no children, there is no title or account
                    }

                    if (shortsInfoGroup?.className == "android.view.ViewGroup") {
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

        return Pair(title, account)
    }

    private fun extractAccountFromShortsInfo(shortsInfoGroup: AccessibilityNodeInfo): String {
        val accountView2 = shortsInfoGroup.getChild(0)
        if (accountView2?.className == "android.view.ViewGroup" && accountView2.childCount > 1) {
            val accountView3 = accountView2.getChild(1)
            if (accountView3?.className == "android.view.ViewGroup") {
                val accountNode = accountView3.getChild(0)
                if (accountNode?.className == "android.view.ViewGroup") {
                    return accountNode.text?.toString() ?: ""
                }
            }
        }
        return ""
    }

    private fun extractTitleFromShortsInfo(shortsInfoGroup: AccessibilityNodeInfo): String {
        val titleGroup2 = shortsInfoGroup.getChild(0)
        if (titleGroup2?.className == "android.view.ViewGroup") {
            val titleGroup3 = titleGroup2.getChild(0)
            if (titleGroup3?.className == "android.view.ViewGroup") {
                val titleNode = titleGroup3.getChild(0)
                if (titleNode?.className == "android.view.ViewGroup") {
                    return titleNode.text?.toString() ?: ""
                }
            }
        }
        return ""
    }

    private fun extractBackButton(backViewGroup: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (backViewGroup != null) {
            // Make sure the first child is an ImageButton, and if so, we store it as the back button
            val backButtonNode = backViewGroup.getChild(0)
            if (backButtonNode?.className == "android.widget.ImageButton") {
                return backButtonNode
            }
        }
        return null
    }

    private fun debugPrintTreeStructure(message: String, node: AccessibilityNodeInfo) {
        val content = StringBuilder()
        content.append("$message:\n")
        getEntireTreeStructure(node, content)
        println("Tree Structure:\n$content")
    }

    private fun getEntireTreeStructure(node: AccessibilityNodeInfo, content: StringBuilder, depth: Int = 0) {
        content.append(" ".repeat(depth) + "${node.className} /$/ ${node.text} /$/ ${node.tooltipText}\n")
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { childNode ->
                getEntireTreeStructure(childNode, content, depth + 1)
            }
        }
    }

    // Helper function to recursively extract text from nodes
    private fun extractTextFromNode(node: AccessibilityNodeInfo, content: StringBuilder) {
        node.text?.let { content.append(it).append("\n") }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { extractTextFromNode(it, content) }
        }
    }

    override fun onInterrupt() {
        // Handle service interruption here
    }
}
