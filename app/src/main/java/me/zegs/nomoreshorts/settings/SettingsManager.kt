package me.zegs.nomoreshorts.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import me.zegs.nomoreshorts.models.BlockingMode
import me.zegs.nomoreshorts.models.LimitType
import me.zegs.nomoreshorts.models.PreferenceKeys
import me.zegs.nomoreshorts.models.ResetPeriodType
import androidx.core.content.edit

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val gson = Gson()

    // Master Control
    var isAppEnabled: Boolean
        get() = prefs.getBoolean(PreferenceKeys.APP_ENABLED, false)
        set(value) = prefs.edit { putBoolean(PreferenceKeys.APP_ENABLED, value) }

    // Blocking Configuration
    var blockShortsFeed: Boolean
        get() = prefs.getBoolean(PreferenceKeys.BLOCK_FEED, true)
        set(value) = prefs.edit { putBoolean(PreferenceKeys.BLOCK_FEED, value)}

    var blockingMode: BlockingMode
        get() {
            val mode = prefs.getString(PreferenceKeys.BLOCKING_MODE, BlockingMode.ALL_SHORTS.name)
            return try {
                BlockingMode.valueOf(mode ?: BlockingMode.ALL_SHORTS.name)
            } catch (_: IllegalArgumentException) {
                BlockingMode.ALL_SHORTS
            }
        }
        set(value) = prefs.edit { putString(PreferenceKeys.BLOCKING_MODE, value.name)}

    // Limit Configuration
    var limitType: LimitType
        get() {
            val type = prefs.getString(PreferenceKeys.LIMIT_TYPE, LimitType.SWIPE_COUNT.name)
            return try {
                LimitType.valueOf(type ?: LimitType.SWIPE_COUNT.name)
            } catch (_: IllegalArgumentException) {
                LimitType.SWIPE_COUNT
            }
        }
        set(value) = prefs.edit { putString(PreferenceKeys.LIMIT_TYPE, value.name)}

    // Swipe Limiting
    var swipeLimitCount: Int
        get() {
            val stringValue = prefs.getString(PreferenceKeys.SWIPE_LIMIT_COUNT, "0") ?: "0"
            return stringValue.toIntOrNull()?.coerceIn(0, 1000) ?: 0
        }
        set(value) {
            prefs.edit { putString(PreferenceKeys.SWIPE_LIMIT_COUNT, value.coerceIn(0, 1000).toString()) }
        }

    var timeLimitMinutes: Int
        get() {
            val stringValue = prefs.getString(PreferenceKeys.TIME_LIMIT_MINUTES, "30") ?: "30"
            return stringValue.toIntOrNull()?.coerceAtLeast(1) ?: 30
        }
        set(value) {
            prefs.edit { putString(PreferenceKeys.TIME_LIMIT_MINUTES, value.coerceAtLeast(1).toString()) }
        }

    var resetPeriodType: ResetPeriodType
        get() {
            val type = prefs.getString(PreferenceKeys.RESET_PERIOD_TYPE, ResetPeriodType.PER_DAY.name)
            return try {
                ResetPeriodType.valueOf(type ?: ResetPeriodType.PER_DAY.name)
            } catch (_: IllegalArgumentException) {
                ResetPeriodType.PER_DAY
            }
        }
        set(value) = prefs.edit {putString(PreferenceKeys.RESET_PERIOD_TYPE, value.name) }

    var resetPeriodMinutes: Int
        get() {
            val stringValue = prefs.getString(PreferenceKeys.RESET_PERIOD_MINUTES, "60") ?: "60"
            return stringValue.toIntOrNull()?.coerceAtLeast(1) ?: 60
        }
        set(value) {
            prefs.edit {putString(PreferenceKeys.RESET_PERIOD_MINUTES, value.coerceAtLeast(1).toString()) }
        }

    // Scheduling
    var scheduleEnabled: Boolean
        get() = prefs.getBoolean(PreferenceKeys.SCHEDULE_ENABLED, false)
        set(value) = prefs.edit {putBoolean(PreferenceKeys.SCHEDULE_ENABLED, value) }

    var scheduleStartTime: String
        get() = prefs.getString(PreferenceKeys.SCHEDULE_START_TIME, "09:00") ?: "09:00"
        set(value) = prefs.edit {putString(PreferenceKeys.SCHEDULE_START_TIME, value) }

    var scheduleEndTime: String
        get() = prefs.getString(PreferenceKeys.SCHEDULE_END_TIME, "22:00") ?: "22:00"
        set(value) = prefs.edit {putString(PreferenceKeys.SCHEDULE_END_TIME, value) }

    var scheduleDays: Set<String>
        get() {
            // First, try to get it as a Set<String> (MultiSelectListPreference format)
            try {
                val directSet = prefs.getStringSet(PreferenceKeys.SCHEDULE_DAYS, null)
                if (directSet != null) {
                    return directSet
                }
            } catch (_: Exception) {
                // If that fails, try to get it as a JSON string
            }

            // Fallback to JSON format
            val daysJson = prefs.getString(PreferenceKeys.SCHEDULE_DAYS, null)
            return if (daysJson != null) {
                try {
                    val type = object : TypeToken<Set<String>>() {}.type
                    gson.fromJson(daysJson, type)
                } catch (_: Exception) {
                    getDefaultDays()
                }
            } else {
                getDefaultDays()
            }
        }
        set(value) {
            // Store as StringSet for MultiSelectListPreference compatibility
            prefs.edit {putStringSet(PreferenceKeys.SCHEDULE_DAYS, value.toSet()) }
        }

    // Channel Allowlist
    var allowlistEnabled: Boolean
        get() = prefs.getBoolean(PreferenceKeys.ALLOWLIST_ENABLED, false)
        set(value) = prefs.edit {putBoolean(PreferenceKeys.ALLOWLIST_ENABLED, value) }

    var allowedChannels: List<String>
        get() {
            val channelsJson = prefs.getString(PreferenceKeys.ALLOWED_CHANNELS, null)
            return if (channelsJson != null) {
                try {
                    val type = object : TypeToken<List<String>>() {}.type
                    gson.fromJson(channelsJson, type) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
        set(value) {
            val channelsJson = gson.toJson(value)
            prefs.edit {putString(PreferenceKeys.ALLOWED_CHANNELS, channelsJson) }
        }

    private fun getDefaultDays(): Set<String> {
        return setOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    }

    fun isEnabledOnDay(day: Int): Boolean {
        val days = scheduleDays.map { it.lowercase() }
        return when (day) {
            1 -> "monday" in days
            2 -> "tuesday" in days
            3 -> "wednesday" in days
            4 -> "thursday" in days
            5 -> "friday" in days
            6 -> "saturday" in days
            7 -> "sunday" in days
            else -> false
        }
    }

    fun addSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun removeSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun getStartTimeAsMillisToday(): Long {
        return getTimeInMillis(scheduleStartTime)
    }

    fun getEndTimeAsMillisToday(): Long {
        return getTimeInMillis(scheduleEndTime)
    }

    private fun getTimeInMillis(time: String): Long {
        val parts = time.split(":")
        if (parts.size != 2) return 0L
        val hours = parts[0].toIntOrNull() ?: return 0L
        val minutes = parts[1].toIntOrNull() ?: return 0L
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, hours)
        calendar.set(java.util.Calendar.MINUTE, minutes)
        calendar.set(java.util.Calendar.SECOND, 0)
        return calendar.timeInMillis
    }

    fun getSessionTimeoutMillis(): Long {
        return when (resetPeriodType) {
            ResetPeriodType.AFTER_SESSION_END -> resetPeriodMinutes * 60 * 1000L
            ResetPeriodType.PER_DAY -> {
                // For per day, calculate milliseconds until midnight
                val calendar = java.util.Calendar.getInstance()
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                calendar.timeInMillis - System.currentTimeMillis()
            }
        }
    }

    fun isInSchedule(): Boolean {
        if (!scheduleEnabled) return true

        val calendar = java.util.Calendar.getInstance()
        val currentDay = calendar.get(java.util.Calendar.DAY_OF_WEEK)

        // Check if today is enabled
        if (!isEnabledOnDay(currentDay)) return false

        // Check if current time is within schedule
        val currentTimeMillis = calendar.timeInMillis
        val todayStart = getStartTimeAsMillisToday()
        val todayEnd = getEndTimeAsMillisToday()

        return if (todayEnd > todayStart) {
            // Same day schedule (e.g., 9:00 AM to 10:00 PM)
            currentTimeMillis >= todayStart && currentTimeMillis <= todayEnd
        } else {
            // Overnight schedule (e.g., 10:00 PM to 6:00 AM next day)
            currentTimeMillis >= todayStart || currentTimeMillis <= todayEnd
        }
    }
}
