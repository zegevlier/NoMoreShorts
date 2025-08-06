package me.zegs.nomoreshorts.settings

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputFilter
import android.text.InputType
import android.util.Log
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import me.zegs.nomoreshorts.R
import me.zegs.nomoreshorts.models.BlockingMode
import me.zegs.nomoreshorts.models.LimitType
import me.zegs.nomoreshorts.models.PreferenceKeys
import me.zegs.nomoreshorts.ui.ChannelManagementActivity
import java.util.Locale

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var settingsManager: SettingsManager? = null
    private var countdownTimer: CountDownTimer? = null
    private var countdownDialog: AlertDialog? = null

    companion object {
        private const val TAG = "SettingsFragment"
        private const val MAX_SWIPE_LIMIT = 10000
        private const val MIN_SWIPE_LIMIT = 0
        private const val MAX_TIME_LIMIT = 1440 // 24 hours in minutes
        private const val MIN_TIME_LIMIT = 1
        private const val MAX_RESET_PERIOD = 10080 // 1 week in minutes
        private const val MIN_RESET_PERIOD = 1
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        try {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            settingsManager = SettingsManager(requireContext())
            setupPreferences()
            updatePreferenceDependencies()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating preferences", e)
            showErrorMessage("Failed to load settings. Please restart the app.")
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        cancelCountdown()
    }

    private fun setupPreferences() {
        try {
            // Setup time picker preferences with error handling
            setupTimePicker(PreferenceKeys.SCHEDULE_START_TIME)
            setupTimePicker(PreferenceKeys.SCHEDULE_END_TIME)

            // Setup channel management with error handling
            setupChannelManagement()

            // Setup list preference summaries to show selected values
            setupListPreferenceSummaries()

            // Setup comprehensive input validation for numeric preferences
            setupNumericValidation()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up preferences", e)
            showErrorMessage("Failed to initialize settings. Some features may not work correctly.")
        }
    }

    private fun setupTimePicker(key: String) {
        findPreference<Preference>(key)?.setOnPreferenceClickListener { preference ->
            try {
                val currentTime = settingsManager?.let {
                    if (key == PreferenceKeys.SCHEDULE_START_TIME) it.scheduleStartTime else it.scheduleEndTime
                } ?: "09:00"

                val (hour, minute) = parseTimeString(currentTime)

                TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->
                    try {
                        if (isValidTime(selectedHour, selectedMinute)) {
                            val timeString = String.format(Locale.US, "%02d:%02d", selectedHour, selectedMinute)
                            settingsManager?.let {
                                if (key == PreferenceKeys.SCHEDULE_START_TIME) {
                                    it.scheduleStartTime = timeString
                                } else {
                                    it.scheduleEndTime = timeString
                                }
                            }
                            preference.summary = timeString
                        } else {
                            showErrorMessage("Invalid time selected")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting time", e)
                        showErrorMessage("Failed to set time")
                    }
                }, hour, minute, true).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error showing time picker for $key", e)
                showErrorMessage("Failed to open time picker")
            }
            true
        }
    }

    private fun parseTimeString(timeString: String): Pair<Int, Int> {
        return try {
            val timeParts = timeString.split(":")
            if (timeParts.size == 2) {
                val hour = timeParts[0].toIntOrNull()?.coerceIn(0, 23) ?: 9
                val minute = timeParts[1].toIntOrNull()?.coerceIn(0, 59) ?: 0
                Pair(hour, minute)
            } else {
                Pair(9, 0) // Default fallback
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing time string: $timeString", e)
            Pair(9, 0) // Default fallback
        }
    }

    private fun isValidTime(hour: Int, minute: Int): Boolean {
        return hour in 0..23 && minute in 0..59
    }

    private fun setupChannelManagement() {
        findPreference<Preference>("manage_channels")?.setOnPreferenceClickListener {
            try {
                val intent = Intent(requireContext(), ChannelManagementActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening channel management", e)
                showErrorMessage("Failed to open channel management")
            }
            true
        }
    }

    private fun setupListPreferenceSummaries() {
        // Setup blocking mode list preference
        findPreference<ListPreference>(PreferenceKeys.BLOCKING_MODE)?.let { pref ->
            pref.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }

        // Setup limit type list preference
        findPreference<ListPreference>(PreferenceKeys.LIMIT_TYPE)?.let { pref ->
            pref.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }

        // Setup reset period type list preference
        findPreference<ListPreference>(PreferenceKeys.RESET_PERIOD_TYPE)?.let { pref ->
            pref.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }
    }

    private fun setupNumericValidation() {
        // Enhanced swipe limit validation with comprehensive error handling
        findPreference<EditTextPreference>(PreferenceKeys.SWIPE_LIMIT_COUNT)?.let { pref ->
            setupEditTextPreference(pref, InputType.TYPE_CLASS_NUMBER) { newValue ->
                validateSwipeLimit(newValue)
            }
        }

        // Enhanced time limit validation
        findPreference<EditTextPreference>(PreferenceKeys.TIME_LIMIT_MINUTES)?.let { pref ->
            setupEditTextPreference(pref, InputType.TYPE_CLASS_NUMBER) { newValue ->
                validateTimeLimit(newValue)
            }
        }

        // Enhanced reset period minutes validation
        findPreference<EditTextPreference>(PreferenceKeys.RESET_PERIOD_MINUTES)?.let { pref ->
            setupEditTextPreference(pref, InputType.TYPE_CLASS_NUMBER) { newValue ->
                validateResetPeriod(newValue)
            }
        }
    }

    private fun setupEditTextPreference(
        preference: EditTextPreference,
        inputType: Int,
        validator: (String) -> ValidationResult
    ) {
        // Set input type and filters
        preference.setOnBindEditTextListener { editText ->
            editText.inputType = inputType
            editText.filters = arrayOf(InputFilter.LengthFilter(10)) // Prevent extremely long inputs
        }

        preference.setOnPreferenceChangeListener { _, newValue ->
            try {
                val result = validator(newValue.toString())
                if (result.isValid) {
                    true
                } else {
                    showErrorMessage(result.errorMessage)
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error validating preference", e)
                showErrorMessage("Invalid input format")
                false
            }
        }
    }

    private fun validateSwipeLimit(value: String): ValidationResult {
        if (value.isBlank()) {
            return ValidationResult(false, "Swipe limit cannot be empty")
        }

        val count = value.toIntOrNull()
        return when {
            count == null -> ValidationResult(false, "Please enter a valid number")
            count < MIN_SWIPE_LIMIT -> ValidationResult(false, "Swipe limit cannot be negative")
            count > MAX_SWIPE_LIMIT -> ValidationResult(false, "Swipe limit cannot exceed $MAX_SWIPE_LIMIT")
            else -> ValidationResult(true, "")
        }
    }

    private fun validateTimeLimit(value: String): ValidationResult {
        if (value.isBlank()) {
            return ValidationResult(false, "Time limit cannot be empty")
        }

        val minutes = value.toIntOrNull()
        return when {
            minutes == null -> ValidationResult(false, "Please enter a valid number")
            minutes < MIN_TIME_LIMIT -> ValidationResult(false, "Time limit must be at least $MIN_TIME_LIMIT minute")
            minutes > MAX_TIME_LIMIT -> ValidationResult(false, "Time limit cannot exceed $MAX_TIME_LIMIT minutes (24 hours)")
            else -> ValidationResult(true, "")
        }
    }

    private fun validateResetPeriod(value: String): ValidationResult {
        if (value.isBlank()) {
            return ValidationResult(false, "Reset period cannot be empty")
        }

        val minutes = value.toIntOrNull()
        return when {
            minutes == null -> ValidationResult(false, "Please enter a valid number")
            minutes < MIN_RESET_PERIOD -> ValidationResult(false, "Reset period must be at least $MIN_RESET_PERIOD minute")
            minutes > MAX_RESET_PERIOD -> ValidationResult(false, "Reset period cannot exceed $MAX_RESET_PERIOD minutes (1 week)")
            else -> ValidationResult(true, "")
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            when (key) {
                PreferenceKeys.APP_ENABLED -> {
                    val isEnabled = sharedPreferences?.getBoolean(key, false) ?: false
                    if (!isEnabled) {
                        startCountdown()
                    }
                    updatePreferenceDependencies()
                }
                PreferenceKeys.BLOCKING_MODE,
                PreferenceKeys.LIMIT_TYPE,
                PreferenceKeys.SCHEDULE_ENABLED,
                PreferenceKeys.ALLOWLIST_ENABLED -> {
                    updatePreferenceDependencies()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling preference change for key: $key", e)
        }
    }

    private fun updatePreferenceDependencies() {
        try {
            val isAppEnabled = settingsManager?.isAppEnabled ?: false
            val isOnlySwipingMode = settingsManager?.blockingMode == BlockingMode.ONLY_SWIPING
            val limitType = settingsManager?.limitType ?: LimitType.SWIPE_COUNT

            // Show/hide swipe limiting section based on blocking mode
            val swipeLimitingCategory = findPreference<Preference>("swipe_limiting_category")
            swipeLimitingCategory?.isVisible = isAppEnabled && isOnlySwipingMode

            // Show/hide specific limit fields based on limit type
            val swipeCountField = findPreference<EditTextPreference>(PreferenceKeys.SWIPE_LIMIT_COUNT)
            val timeLimitField = findPreference<EditTextPreference>(PreferenceKeys.TIME_LIMIT_MINUTES)

            swipeCountField?.isVisible = isAppEnabled && isOnlySwipingMode && limitType == LimitType.SWIPE_COUNT
            timeLimitField?.isVisible = isAppEnabled && isOnlySwipingMode && limitType == LimitType.TIME_LIMIT

            // Update summaries for time preferences
            updateTimeSummaries()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating preference dependencies", e)
        }
    }

    private fun updateTimeSummaries() {
        try {
            settingsManager?.let { manager ->
                findPreference<Preference>(PreferenceKeys.SCHEDULE_START_TIME)?.summary = manager.scheduleStartTime
                findPreference<Preference>(PreferenceKeys.SCHEDULE_END_TIME)?.summary = manager.scheduleEndTime
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating time summaries", e)
        }
    }

    private fun startCountdown() {
        try {
            cancelCountdown() // Cancel any existing countdown

            val countdownSeconds = 10
            var remainingSeconds = countdownSeconds

            // Create the dialog first
            countdownDialog = AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.countdown_title))
                .setMessage(resources.getQuantityString(R.plurals.countdown_message, remainingSeconds, remainingSeconds))
                .setCancelable(false)
                .setNegativeButton(getString(R.string.countdown_cancel)) { _, _ ->
                    try {
                        cancelCountdown()
                        // Re-enable the app
                        settingsManager?.isAppEnabled = true
                        findPreference<SwitchPreferenceCompat>(PreferenceKeys.APP_ENABLED)?.isChecked = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error re-enabling app from countdown", e)
                    }
                }
                .create()

            countdownDialog?.show()

            countdownTimer = object : CountDownTimer((countdownSeconds * 1000).toLong(), 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    try {
                        remainingSeconds = (millisUntilFinished / 1000).toInt() + 1
                        countdownDialog?.setMessage(resources.getQuantityString(R.plurals.countdown_message, remainingSeconds, remainingSeconds))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating countdown timer", e)
                    }
                }

                override fun onFinish() {
                    try {
                        countdownDialog?.dismiss()
                        countdownDialog = null
                        // App will remain disabled as set by the preference change
                        Toast.makeText(requireContext(), getString(R.string.youtube_shorts_blocker_disabled), Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error finishing countdown", e)
                    }
                }
            }
            countdownTimer?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting countdown", e)
            showErrorMessage("Failed to start countdown")
        }
    }

    private fun cancelCountdown() {
        try {
            countdownTimer?.cancel()
            countdownTimer = null
            countdownDialog?.dismiss()
            countdownDialog = null
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling countdown", e)
        }
    }

    private fun showErrorMessage(message: String) {
        try {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing error message: $message", e)
        }
    }

    private data class ValidationResult(val isValid: Boolean, val errorMessage: String)
}
