package me.zegs.nomoreshorts.session

import android.content.Context
import android.os.Handler
import android.os.Looper
import me.zegs.nomoreshorts.R
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
        if (sessionStartTime == 0L) {
            sessionStartTime = System.currentTimeMillis()
            sessionSwipeCount = 0
            scheduleReset()
        }
    }

    fun addSwipeAndUpdateTime() {
        sessionSwipeCount++

        if (settingsManager.resetPeriodType == ResetPeriodType.AFTER_SESSION_END) {
            // Re-schedule reset after each swipe
            // This means that if you stop swiping, the session will reset after the delay
            scheduleReset()
        }
        // Check if limit is reached (this ends the session)
        if (isLimitReached()) {
            onLimitReached?.invoke()
        }
    }

    fun scheduleReset(customDelayMillis: Long? = null) {
        // Cancel any existing reset task
        cancelReset()

        val delayMillis = customDelayMillis ?: settingsManager.getSessionTimeoutMillis()

        if (delayMillis <= 0) {
            return
        }


        currentResetTask = Runnable {
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
        }
    }

    fun updateResetSchedule() {
        // Called when settings change - reschedule with new timing
        if (currentResetTask != null) {
            scheduleReset()
        }
    }

    private fun resetSession() {
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

    fun getLimitReachedMessage(context: Context): String {
        return when (settingsManager.limitType) {
            me.zegs.nomoreshorts.models.LimitType.SWIPE_COUNT -> {
                if (settingsManager.swipeLimitCount == 0) {
                    context.getString(R.string.swiping_disabled)
                } else {
                    context.getString(R.string.swipe_limit_reached, sessionSwipeCount, settingsManager.swipeLimitCount)
                }
            }

            me.zegs.nomoreshorts.models.LimitType.TIME_LIMIT -> {
                val timeSpentMinutes = (getCurrentTimeSpent() / 60000).toInt()
                context.getString(R.string.time_limit_reached, timeSpentMinutes, settingsManager.timeLimitMinutes)
            }
        }
    }

    fun cleanup() {
        cancelReset()
        onSessionReset = null
        onLimitReached = null
    }
}
