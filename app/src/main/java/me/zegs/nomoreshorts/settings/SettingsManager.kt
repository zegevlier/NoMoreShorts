package me.zegs.nomoreshorts.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import me.zegs.nomoreshorts.models.BlockingMode
import me.zegs.nomoreshorts.models.LimitType
import me.zegs.nomoreshorts.models.PreferenceKeys
import me.zegs.nomoreshorts.models.ResetPeriodType
import androidx.core.content.edit
import java.util.Calendar

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val gson = Gson()

    companion object {
        private const val TAG = "SettingsManager"

        // Validation constants
        private const val MIN_SWIPE_LIMIT = 0
        private const val MAX_SWIPE_LIMIT = 10000
        private const val MIN_TIME_LIMIT = 1
        private const val MAX_TIME_LIMIT = 1440 // 24 hours
        private const val MIN_RESET_PERIOD = 1
        private const val MAX_RESET_PERIOD = 10080 // 1 week in minutes

        // Default values
        private const val DEFAULT_SWIPE_LIMIT = 0
        private const val DEFAULT_TIME_LIMIT = 30
        private const val DEFAULT_RESET_PERIOD = 60
        private const val DEFAULT_START_TIME = "09:00"
        private const val DEFAULT_END_TIME = "22:00"
    }

    // Master Control with validation
    var isAppEnabled: Boolean
        get() = safeGetBoolean(PreferenceKeys.APP_ENABLED, false)
        set(value) = safeSetBoolean(PreferenceKeys.APP_ENABLED, value)

    // Blocking Configuration with validation
    var blockShortsFeed: Boolean
        get() = safeGetBoolean(PreferenceKeys.BLOCK_FEED, true)
        set(value) = safeSetBoolean(PreferenceKeys.BLOCK_FEED, value)

    var blockingMode: BlockingMode
        get() = safeGetEnum(PreferenceKeys.BLOCKING_MODE, BlockingMode.ALL_SHORTS) { BlockingMode.valueOf(it) }
        set(value) = safeSetEnum(PreferenceKeys.BLOCKING_MODE, value)

    // Limit Configuration with validation
    var limitType: LimitType
        get() = safeGetEnum(PreferenceKeys.LIMIT_TYPE, LimitType.SWIPE_COUNT) { LimitType.valueOf(it) }
        set(value) = safeSetEnum(PreferenceKeys.LIMIT_TYPE, value)

    // Swipe Limiting with comprehensive validation
    var swipeLimitCount: Int
        get() {
            val stringValue = safeGetString(PreferenceKeys.SWIPE_LIMIT_COUNT, DEFAULT_SWIPE_LIMIT.toString())
            return validateAndCoerceInt(stringValue, MIN_SWIPE_LIMIT, MAX_SWIPE_LIMIT, DEFAULT_SWIPE_LIMIT)
        }
        set(value) {
            val validatedValue = validateAndCoerceInt(value.toString(), MIN_SWIPE_LIMIT, MAX_SWIPE_LIMIT, DEFAULT_SWIPE_LIMIT)
            safeSetString(PreferenceKeys.SWIPE_LIMIT_COUNT, validatedValue.toString())
        }

    var timeLimitMinutes: Int
        get() {
            val stringValue = safeGetString(PreferenceKeys.TIME_LIMIT_MINUTES, DEFAULT_TIME_LIMIT.toString())
            return validateAndCoerceInt(stringValue, MIN_TIME_LIMIT, MAX_TIME_LIMIT, DEFAULT_TIME_LIMIT)
        }
        set(value) {
            val validatedValue = validateAndCoerceInt(value.toString(), MIN_TIME_LIMIT, MAX_TIME_LIMIT, DEFAULT_TIME_LIMIT)
            safeSetString(PreferenceKeys.TIME_LIMIT_MINUTES, validatedValue.toString())
        }

    var resetPeriodType: ResetPeriodType
        get() = safeGetEnum(PreferenceKeys.RESET_PERIOD_TYPE, ResetPeriodType.PER_DAY) { ResetPeriodType.valueOf(it) }
        set(value) = safeSetEnum(PreferenceKeys.RESET_PERIOD_TYPE, value)

    var resetPeriodMinutes: Int
        get() {
            val stringValue = safeGetString(PreferenceKeys.RESET_PERIOD_MINUTES, DEFAULT_RESET_PERIOD.toString())
            return validateAndCoerceInt(stringValue, MIN_RESET_PERIOD, MAX_RESET_PERIOD, DEFAULT_RESET_PERIOD)
        }
        set(value) {
            val validatedValue = validateAndCoerceInt(value.toString(), MIN_RESET_PERIOD, MAX_RESET_PERIOD, DEFAULT_RESET_PERIOD)
            safeSetString(PreferenceKeys.RESET_PERIOD_MINUTES, validatedValue.toString())
        }

    // Scheduling with validation
    var scheduleEnabled: Boolean
        get() = safeGetBoolean(PreferenceKeys.SCHEDULE_ENABLED, false)
        set(value) = safeSetBoolean(PreferenceKeys.SCHEDULE_ENABLED, value)

    var scheduleStartTime: String
        get() = validateTimeString(safeGetString(PreferenceKeys.SCHEDULE_START_TIME, DEFAULT_START_TIME))
        set(value) = safeSetString(PreferenceKeys.SCHEDULE_START_TIME, validateTimeString(value))

    var scheduleEndTime: String
        get() = validateTimeString(safeGetString(PreferenceKeys.SCHEDULE_END_TIME, DEFAULT_END_TIME))
        set(value) = safeSetString(PreferenceKeys.SCHEDULE_END_TIME, validateTimeString(value))

    var scheduleDays: Set<String>
        get() {
            return try {
                // First, try to get it as a Set<String> (MultiSelectListPreference format)
                val directSet = prefs.getStringSet(PreferenceKeys.SCHEDULE_DAYS, null)
                if (directSet != null && directSet.isNotEmpty()) {
                    validateDaysSet(directSet)
                } else {
                    // Fallback to JSON format
                    val daysJson = safeGetString(PreferenceKeys.SCHEDULE_DAYS, null)
                    if (!daysJson.isNullOrEmpty()) {
                        val type = object : TypeToken<Set<String>>() {}.type
                        val parsedSet: Set<String> = gson.fromJson(daysJson, type) ?: getDefaultDays()
                        validateDaysSet(parsedSet)
                    } else {
                        getDefaultDays()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting schedule days, using defaults", e)
                getDefaultDays()
            }
        }
        set(value) {
            try {
                val validatedDays = validateDaysSet(value)
                // Store as StringSet for MultiSelectListPreference compatibility
                prefs.edit { putStringSet(PreferenceKeys.SCHEDULE_DAYS, validatedDays) }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting schedule days", e)
            }
        }

    // Channel Allowlist with validation
    var allowlistEnabled: Boolean
        get() = safeGetBoolean(PreferenceKeys.ALLOWLIST_ENABLED, false)
        set(value) = safeSetBoolean(PreferenceKeys.ALLOWLIST_ENABLED, value)

    var allowedChannels: List<String>
        get() {
            return try {
                val channelsJson = safeGetString(PreferenceKeys.ALLOWED_CHANNELS, null)
                if (!channelsJson.isNullOrEmpty()) {
                    val type = object : TypeToken<List<String>>() {}.type
                    val channels: List<String> = gson.fromJson(channelsJson, type) ?: emptyList()
                    validateChannelList(channels)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting allowed channels, returning empty list", e)
                emptyList()
            }
        }
        set(value) {
            try {
                val validatedChannels = validateChannelList(value)
                val channelsJson = gson.toJson(validatedChannels)
                safeSetString(PreferenceKeys.ALLOWED_CHANNELS, channelsJson)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting allowed channels", e)
            }
        }

    // Safe preference access methods with error handling
    private fun safeGetBoolean(key: String, defaultValue: Boolean): Boolean {
        return try {
            prefs.getBoolean(key, defaultValue)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting boolean preference $key, using default: $defaultValue", e)
            defaultValue
        }
    }

    private fun safeSetBoolean(key: String, value: Boolean) {
        try {
            prefs.edit { putBoolean(key, value) }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting boolean preference $key to $value", e)
        }
    }

    private fun safeGetString(key: String, defaultValue: String?): String? {
        return try {
            prefs.getString(key, defaultValue)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting string preference $key, using default: $defaultValue", e)
            defaultValue
        }
    }

    private fun safeSetString(key: String, value: String?) {
        try {
            prefs.edit { putString(key, value) }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting string preference $key to $value", e)
        }
    }

    private inline fun <reified T : Enum<T>> safeGetEnum(key: String, defaultValue: T, valueOf: (String) -> T): T {
        return try {
            val stringValue = safeGetString(key, defaultValue.name) ?: defaultValue.name
            valueOf(stringValue)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid enum value for $key, using default: $defaultValue", e)
            defaultValue
        } catch (e: Exception) {
            Log.e(TAG, "Error getting enum preference $key, using default: $defaultValue", e)
            defaultValue
        }
    }

    private fun <T : Enum<T>> safeSetEnum(key: String, value: T) {
        try {
            safeSetString(key, value.name)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting enum preference $key to ${value.name}", e)
        }
    }

    // Validation helper methods
    private fun validateAndCoerceInt(value: String?, min: Int, max: Int, default: Int): Int {
        return try {
            if (value.isNullOrBlank()) {
                Log.w(TAG, "Empty integer value, using default: $default")
                return default
            }
            val intValue = value.toIntOrNull()
            when {
                intValue == null -> {
                    Log.w(TAG, "Invalid integer value: $value, using default: $default")
                    default
                }
                intValue < min -> {
                    Log.w(TAG, "Value $intValue below minimum $min, coercing to $min")
                    min
                }
                intValue > max -> {
                    Log.w(TAG, "Value $intValue above maximum $max, coercing to $max")
                    max
                }
                else -> intValue
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating integer value: $value, using default: $default", e)
            default
        }
    }

    private fun validateTimeString(timeString: String?): String {
        if (timeString.isNullOrBlank()) {
            Log.w(TAG, "Empty time string, using default")
            return DEFAULT_START_TIME
        }

        return try {
            val parts = timeString.split(":")
            if (parts.size == 2) {
                val hour = parts[0].toIntOrNull()?.coerceIn(0, 23)
                val minute = parts[1].toIntOrNull()?.coerceIn(0, 59)

                if (hour != null && minute != null) {
                    String.format("%02d:%02d", hour, minute)
                } else {
                    Log.w(TAG, "Invalid time format: $timeString, using default")
                    DEFAULT_START_TIME
                }
            } else {
                Log.w(TAG, "Invalid time format: $timeString, using default")
                DEFAULT_START_TIME
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating time string: $timeString, using default", e)
            DEFAULT_START_TIME
        }
    }

    private fun validateDaysSet(days: Set<String>): Set<String> {
        val validDays = setOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        return try {
            val filteredDays = days.filter { day ->
                val isValid = day.lowercase() in validDays
                if (!isValid) {
                    Log.w(TAG, "Invalid day of week: $day, filtering out")
                }
                isValid
            }.map { it.lowercase() }.toSet()

            if (filteredDays.isEmpty()) {
                Log.w(TAG, "No valid days provided, using all days")
                getDefaultDays()
            } else {
                filteredDays
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating days set, using defaults", e)
            getDefaultDays()
        }
    }

    private fun validateChannelList(channels: List<String>): List<String> {
        return try {
            channels.filter { channel ->
                val isValid = channel.isNotBlank() && channel.length <= 100 // Reasonable limit
                if (!isValid) {
                    Log.w(TAG, "Invalid channel name: '$channel', filtering out")
                }
                isValid
            }.distinct() // Remove duplicates
        } catch (e: Exception) {
            Log.e(TAG, "Error validating channel list, returning empty list", e)
            emptyList()
        }
    }

    private fun getDefaultDays(): Set<String> {
        return setOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    }

    fun isEnabledOnDay(day: Int): Boolean {
        return try {
            val days = scheduleDays.map { it.lowercase() }
            when (day) {
                Calendar.MONDAY -> "monday" in days
                Calendar.TUESDAY -> "tuesday" in days
                Calendar.WEDNESDAY -> "wednesday" in days
                Calendar.THURSDAY -> "thursday" in days
                Calendar.FRIDAY -> "friday" in days
                Calendar.SATURDAY -> "saturday" in days
                Calendar.SUNDAY -> "sunday" in days
                else -> {
                    Log.w(TAG, "Invalid day of week: $day")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if enabled on day $day", e)
            false
        }
    }

    fun addSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        try {
            prefs.registerOnSharedPreferenceChangeListener(listener)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering preference change listener", e)
        }
    }

    fun removeSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        try {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering preference change listener", e)
        }
    }

    fun getStartTimeAsMillisToday(): Long {
        return getTimeInMillis(scheduleStartTime)
    }

    fun getEndTimeAsMillisToday(): Long {
        return getTimeInMillis(scheduleEndTime)
    }

    private fun getTimeInMillis(time: String): Long {
        return try {
            val parts = time.split(":")
            if (parts.size != 2) return 0L

            val hours = parts[0].toIntOrNull() ?: return 0L
            val minutes = parts[1].toIntOrNull() ?: return 0L

            if (hours !in 0..23 || minutes !in 0..59) return 0L

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, hours)
            calendar.set(Calendar.MINUTE, minutes)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        } catch (e: Exception) {
            Log.e(TAG, "Error converting time to millis: $time", e)
            0L
        }
    }

    fun getSessionTimeoutMillis(): Long {
        return try {
            when (resetPeriodType) {
                ResetPeriodType.AFTER_SESSION_END -> {
                    val minutes = resetPeriodMinutes.toLong()
                    if (minutes > 0) minutes * 60 * 1000L else 60 * 60 * 1000L // Default to 1 hour
                }
                ResetPeriodType.PER_DAY -> {
                    // For per day, calculate milliseconds until midnight
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    val timeout = calendar.timeInMillis - System.currentTimeMillis()
                    if (timeout > 0) timeout else 24 * 60 * 60 * 1000L // Default to 24 hours
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating session timeout", e)
            60 * 60 * 1000L // Default to 1 hour
        }
    }

    fun isInSchedule(): Boolean {
        return try {
            if (!scheduleEnabled) return true

            val calendar = Calendar.getInstance()
            val currentDay = calendar.get(Calendar.DAY_OF_WEEK)

            // Check if today is enabled
            if (!isEnabledOnDay(currentDay)) return false

            // Check if current time is within schedule
            val currentTimeMillis = calendar.timeInMillis
            val todayStart = getStartTimeAsMillisToday()
            val todayEnd = getEndTimeAsMillisToday()

            if (todayStart == 0L || todayEnd == 0L) {
                Log.w(TAG, "Invalid schedule times, allowing access")
                return true
            }

            if (todayEnd > todayStart) {
                // Same day schedule (e.g., 9:00 AM to 10:00 PM)
                currentTimeMillis >= todayStart && currentTimeMillis <= todayEnd
            } else {
                // Overnight schedule (e.g., 10:00 PM to 6:00 AM next day)
                currentTimeMillis >= todayStart || currentTimeMillis <= todayEnd
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if in schedule, allowing access", e)
            true // Default to allowing access if there's an error
        }
    }

    /**
     * Validates all current preference values and fixes any invalid ones
     * This method can be called on app startup to ensure data integrity
     */
    fun validateAndFixAllPreferences() {
        try {
            Log.d(TAG, "Validating and fixing all preferences")

            // Trigger getters which will validate and fix invalid values
            swipeLimitCount
            timeLimitMinutes
            resetPeriodMinutes
            scheduleStartTime
            scheduleEndTime
            scheduleDays
            allowedChannels

            Log.d(TAG, "Preference validation completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during preference validation", e)
        }
    }
}
