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
import com.google.android.material.snackbar.Snackbar
import me.zegs.nomoreshorts.R
import me.zegs.nomoreshorts.models.BlockingMode
import me.zegs.nomoreshorts.models.LimitType
import me.zegs.nomoreshorts.PersistenceService
import me.zegs.nomoreshorts.models.PreferenceKeys
import me.zegs.nomoreshorts.models.ResetPeriodType
import me.zegs.nomoreshorts.ui.ChannelManagementActivity
import me.zegs.nomoreshorts.utils.ValidationUtils
import java.util.Locale

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var settingsManager: SettingsManager? = null
    private var countdownTimer: CountDownTimer? = null
    private var countdownDialog: AlertDialog? = null
    private var isCountdownActive = false // Track if countdown is running
    private var isCompletingCountdown = false // Track if we're in the process of completing countdown

    companion object {
        private const val TAG = "SettingsFragment"
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
            // Setup time picker preferences with enhanced error handling
            setupTimePicker(PreferenceKeys.SCHEDULE_START_TIME)
            setupTimePicker(PreferenceKeys.SCHEDULE_END_TIME)

            // Setup channel management with loading state
            setupChannelManagement()

            // Setup list preference summaries to show selected values
            setupListPreferenceSummaries()

            // Setup comprehensive input validation for numeric preferences
            setupEnhancedNumericValidation()
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
                        val timeString = String.format(Locale.US, "%02d:%02d", selectedHour, selectedMinute)
                        // Save the validated time
                        settingsManager?.let {
                            if (key == PreferenceKeys.SCHEDULE_START_TIME) {
                                it.scheduleStartTime = timeString
                            } else {
                                it.scheduleEndTime = timeString
                            }
                        }
                        preference.summary = timeString

                        val message = "Time updated successfully"
                        showSuccessMessage(message)

                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting time", e)
                        showErrorMessage("Failed to set time: ${e.message}")
                    } finally {

                    }
                }, hour, minute, true).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error showing time picker for $key", e)
                showErrorMessage("Failed to open time picker: ${e.message}")

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
    private fun setupChannelManagement() {
        findPreference<Preference>("manage_channels")?.setOnPreferenceClickListener {
            try {
                val intent = Intent(requireContext(), ChannelManagementActivity::class.java)
                startActivity(intent)

            } catch (e: Exception) {
                Log.e(TAG, "Error opening channel management", e)
                showErrorMessage("Failed to open channel management: ${e.message}")
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

    private fun setupEnhancedNumericValidation() {
        // Enhanced swipe limit validation using ValidationUtils
        findPreference<EditTextPreference>(PreferenceKeys.SWIPE_LIMIT_COUNT)?.let { pref ->
            setupEditTextPreferenceWithLoading(pref, InputType.TYPE_CLASS_NUMBER) { newValue ->
                ValidationUtils.validateSwipeLimit(newValue)
            }
        }

        // Enhanced time limit validation using ValidationUtils
        findPreference<EditTextPreference>(PreferenceKeys.TIME_LIMIT_MINUTES)?.let { pref ->
            setupEditTextPreferenceWithLoading(pref, InputType.TYPE_CLASS_NUMBER) { newValue ->
                ValidationUtils.validateTimeLimit(newValue)
            }
        }

        // Enhanced reset period minutes validation using ValidationUtils
        findPreference<EditTextPreference>(PreferenceKeys.RESET_PERIOD_MINUTES)?.let { pref ->
            setupEditTextPreferenceWithLoading(pref, InputType.TYPE_CLASS_NUMBER) { newValue ->
                ValidationUtils.validateResetPeriod(newValue)
            }
        }
    }

    private fun setupEditTextPreferenceWithLoading(
        preference: EditTextPreference,
        inputType: Int,
        validator: (String) -> ValidationUtils.ValidationResult
    ) {
        // Set input type and filters
        preference.setOnBindEditTextListener { editText ->
            editText.inputType = inputType
            editText.filters = arrayOf(InputFilter.LengthFilter(10)) // Prevent extremely long inputs
        }

        preference.setOnPreferenceChangeListener { _, newValue ->
            try {
                val sanitizedValue = ValidationUtils.sanitizeInput(newValue.toString())
                val result = validator(sanitizedValue)


                if (result.isValid) {
                    var message = "Setting updated successfully"
                    if (result.warningMessage.isNotEmpty()) {
                        message += "\nNote: ${result.warningMessage}"
                    }
                    showSuccessMessage(message)
                    true
                } else {
                    showErrorMessage(result.errorMessage)
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error validating preference", e)

                showErrorMessage("Invalid input format: ${e.message}")
                false
            }
        }

        // Update summary to show current value without emoji indicators
        preference.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
            val value = pref.text
            if (value.isNullOrBlank()) {
                "Not set"
            } else {
                value
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            when (key) {
                PreferenceKeys.APP_ENABLED -> {
                    val isEnabled = sharedPreferences?.getBoolean(key, false) ?: false

                    if (isEnabled && !isCountdownActive && !isCompletingCountdown) {
                        // App is being enabled - start the persistence service
                        Log.d(TAG, "App enabled, starting persistence service")
                        PersistenceService.start(requireContext())
                    } else if (!isEnabled && !isCountdownActive && !isCompletingCountdown) {
                        // Only start countdown if:
                        // 1. App is being disabled (!isEnabled)
                        // 2. No countdown is currently active (!isCountdownActive)
                        // 3. We're not in the process of completing a countdown (!isCompletingCountdown)

                        // Prevent the switch from changing - revert it back to enabled
                        isCountdownActive = true
                        settingsManager?.isAppEnabled = true
                        findPreference<SwitchPreferenceCompat>(PreferenceKeys.APP_ENABLED)?.isChecked = true

                        // Start countdown without changing any stored settings
                        startCountdown()
                    }
                    updatePreferenceDependencies()
                }

                PreferenceKeys.BLOCKING_MODE,
                PreferenceKeys.LIMIT_TYPE,
                PreferenceKeys.RESET_PERIOD_TYPE,
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
            val resetPeriodType = settingsManager?.resetPeriodType ?: ResetPeriodType.PER_DAY

            // Show/hide swipe limiting section based on blocking mode
            val swipeLimitingCategory = findPreference<Preference>("swipe_limiting_category")
            swipeLimitingCategory?.isVisible = isAppEnabled && isOnlySwipingMode

            // Show/hide specific limit fields based on limit type
            val swipeCountField = findPreference<EditTextPreference>(PreferenceKeys.SWIPE_LIMIT_COUNT)
            val timeLimitField = findPreference<EditTextPreference>(PreferenceKeys.TIME_LIMIT_MINUTES)
            val resetPeriodMinutesField = findPreference<EditTextPreference>(PreferenceKeys.RESET_PERIOD_MINUTES)

            swipeCountField?.isVisible = isAppEnabled && isOnlySwipingMode && limitType == LimitType.SWIPE_COUNT
            // Show time limit field only when limit type is TIME_LIMIT
            timeLimitField?.isVisible = isAppEnabled && isOnlySwipingMode && limitType == LimitType.TIME_LIMIT
            // Show reset period minutes only when reset period is time-based (AFTER_SESSION_END) instead of day-based
            resetPeriodMinutesField?.isVisible = isAppEnabled && isOnlySwipingMode && resetPeriodType == ResetPeriodType.AFTER_SESSION_END

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
                .setMessage(
                    resources.getQuantityString(
                        R.plurals.countdown_message,
                        remainingSeconds,
                        remainingSeconds
                    )
                )
                .setCancelable(false)
                .setNegativeButton(getString(R.string.countdown_cancel)) { _, _ ->
                    try {
                        cancelCountdown()
                        // Keep the app enabled - no changes needed since we never changed the setting
                        isCountdownActive = false
                    } catch (e: Exception) {
                        Log.e(TAG, "Error canceling countdown", e)
                    }
                }
                .create()

            countdownDialog?.show()

            countdownTimer = object : CountDownTimer((countdownSeconds * 1000).toLong(), 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    try {
                        remainingSeconds = (millisUntilFinished / 1000).toInt() + 1
                        countdownDialog?.setMessage(
                            resources.getQuantityString(
                                R.plurals.countdown_message,
                                remainingSeconds,
                                remainingSeconds
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating countdown timer", e)
                    }
                }

                override fun onFinish() {
                    try {
                        countdownDialog?.dismiss()
                        countdownDialog = null
                        isCountdownActive = false

                        // Only disable if we're still in the foreground (dialog was visible)
                        if (activity != null && !requireActivity().isFinishing && isAdded) {
                            // Set flag to prevent recursive countdown when we disable the app
                            isCompletingCountdown = true

                            // Now actually disable the app after countdown completes
                            settingsManager?.isAppEnabled = false
                            findPreference<SwitchPreferenceCompat>(PreferenceKeys.APP_ENABLED)?.isChecked = false
                            showSuccessMessage(getString(R.string.youtube_shorts_blocker_disabled))

                            // Reset the flag after a short delay to allow the preference change to process
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                isCompletingCountdown = false
                            }, 100)
                        } else {
                            // User left the app during countdown - keep it enabled
                            Log.d(TAG, "User left app during countdown - keeping app enabled")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error finishing countdown", e)
                        isCountdownActive = false
                        isCompletingCountdown = false
                    }
                }
            }
            countdownTimer?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting countdown", e)
            showErrorMessage("Failed to start countdown")
            isCountdownActive = false
        }
    }

    private fun cancelCountdown() {
        try {
            isCountdownActive = false
            countdownTimer?.cancel()
            countdownTimer = null
            countdownDialog?.dismiss()
            countdownDialog = null
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling countdown", e)
            isCountdownActive = false
        }
    }

    private fun showErrorMessage(message: String) {
        try {
            view?.let {
                Snackbar.make(it, message, Snackbar.LENGTH_LONG)
                    .setBackgroundTint(resources.getColor(android.R.color.holo_red_light, null))
                    .show()
            } ?: run {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing error message: $message", e)
        }
    }

    private fun showSuccessMessage(message: String) {
        try {
            view?.let {
                Snackbar.make(it, message, Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(resources.getColor(android.R.color.holo_green_light, null))
                    .show()
            } ?: run {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing success message: $message", e)
        }
    }
}
