package me.zegs.nomoreshorts.session

import android.os.Handler
import android.os.Looper
import me.zegs.nomoreshorts.models.ResetPeriodType
import me.zegs.nomoreshorts.settings.SettingsManager

class SessionManager(private val settingsManager: SettingsManager) {

    private val handler = Handler(Looper.getMainLooper())
    private var currentResetTask: Runnable? = null
    private var sessionStartTime: Long = 0
    private var sessionSwipeCount: Int = 0

    // Callbacks
    var onSessionReset: (() -> Unit)? = null
    var onLimitReached: (() -> Unit)? = null

    fun startSession() {
        println("Starting new session if there is no active session")
        if (sessionStartTime == 0L) {
            sessionStartTime = System.currentTimeMillis()
            sessionSwipeCount = 0
            scheduleReset()
            println("Session started at ${sessionStartTime}")
        } else {
            println("Session already active, not starting a new one")
        }
    }

    fun addSwipeAndUpdateTime() {
        sessionSwipeCount++
        val currentSessionTime = getCurrentTimeSpent()
        println("Swipe count: $sessionSwipeCount, Time spent: ${currentSessionTime / 1000} seconds")

        if (settingsManager.resetPeriodType == ResetPeriodType.AFTER_SESSION_END) {
            // Re-schedule reset after each swipe
            // This means that if you stop swiping, the session will reset after the delay
            scheduleReset()
        }
        // Check if limit is reached (this ends the session)
        if (isLimitReached()) {
            println("Session limit reached!")
        }
    }

    fun scheduleReset(customDelayMillis: Long? = null) {
        // Cancel any existing reset task
        cancelReset()

        val delayMillis = customDelayMillis ?: settingsManager.getSessionTimeoutMillis()

        if (delayMillis <= 0) {
            println("Invalid delay for reset scheduling: $delayMillis")
            return
        }

        println("Scheduling session reset in ${delayMillis / 1000} seconds")

        currentResetTask = Runnable {
            println("Session reset triggered")
            resetSession()
            onSessionReset?.invoke()

            // Reschedule if needed (for per-day resets)
            if (settingsManager.resetPeriodType == ResetPeriodType.PER_DAY) {
                scheduleReset()
            }
        }

        handler.postDelayed(currentResetTask!!, delayMillis)
    }

    fun cancelReset() {
        currentResetTask?.let { task ->
            handler.removeCallbacks(task)
            currentResetTask = null
            println("Cancelled scheduled session reset")
        }
    }

    fun updateResetSchedule() {
        // Called when settings change - reschedule with new timing
        if (currentResetTask != null) {
            println("Updating reset schedule due to settings change")
            scheduleReset()
        }
    }

    private fun resetSession() {
        println("Resetting session counters")
        sessionSwipeCount = 0
        sessionStartTime = 0L
    }

    fun getCurrentTimeSpent(): Long {
        return if (sessionStartTime > 0) {
            System.currentTimeMillis() - sessionStartTime
        } else {
            0L
        }
    }

    fun isLimitReached(): Boolean {
        return when (settingsManager.limitType) {
            me.zegs.nomoreshorts.models.LimitType.SWIPE_COUNT -> {
                sessionSwipeCount >= settingsManager.swipeLimitCount
            }
            me.zegs.nomoreshorts.models.LimitType.TIME_LIMIT -> {
                getCurrentTimeSpent() >= settingsManager.timeLimitMinutes * 60 * 1000L
            }
        }
    }

    fun cleanup() {
        cancelReset()
        onSessionReset = null
        onLimitReached = null
    }
}
