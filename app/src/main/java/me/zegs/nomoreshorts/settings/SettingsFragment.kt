package me.zegs.nomoreshorts.settings

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
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
import java.util.*

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var settingsManager: SettingsManager? = null
    private var countdownTimer: CountDownTimer? = null
    private var countdownDialog: AlertDialog? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        settingsManager = SettingsManager(requireContext())

        setupPreferences()
        updatePreferenceDependencies()
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
        // Setup time picker preferences
        setupTimePicker(PreferenceKeys.SCHEDULE_START_TIME, "Start Time")
        setupTimePicker(PreferenceKeys.SCHEDULE_END_TIME, "End Time")

        // Setup channel management
        findPreference<Preference>("manage_channels")?.setOnPreferenceClickListener {
            val intent = Intent(requireContext(), ChannelManagementActivity::class.java)
            startActivity(intent)
            true
        }

        // Setup list preference summaries to show selected values
        setupListPreferenceSummaries()

        // Setup input validation for numeric preferences
        setupNumericValidation()
    }

    private fun setupTimePicker(key: String, title: String) {
        findPreference<Preference>(key)?.setOnPreferenceClickListener { preference ->
            val currentTime = settingsManager?.let {
                if (key == PreferenceKeys.SCHEDULE_START_TIME) it.scheduleStartTime else it.scheduleEndTime
            } ?: "09:00"

            val timeParts = currentTime.split(":")
            val hour = timeParts[0].toIntOrNull() ?: 9
            val minute = timeParts[1].toIntOrNull() ?: 0

            TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->
                val timeString = String.format("%02d:%02d", selectedHour, selectedMinute)
                settingsManager?.let {
                    if (key == PreferenceKeys.SCHEDULE_START_TIME) {
                        it.scheduleStartTime = timeString
                    } else {
                        it.scheduleEndTime = timeString
                    }
                }
                preference.summary = timeString
            }, hour, minute, true).show()

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
        // Swipe limit validation
        findPreference<EditTextPreference>(PreferenceKeys.SWIPE_LIMIT_COUNT)?.setOnPreferenceChangeListener { _, newValue ->
            val count = newValue.toString().toIntOrNull()
            if (count == null || count < 0 || count > 1000) {
                Toast.makeText(requireContext(), "Swipe limit must be between 0 and 1000", Toast.LENGTH_SHORT).show()
                false
            } else {
                true
            }
        }

        // Time limit validation
        findPreference<EditTextPreference>(PreferenceKeys.TIME_LIMIT_MINUTES)?.setOnPreferenceChangeListener { _, newValue ->
            val minutes = newValue.toString().toIntOrNull()
            if (minutes == null || minutes < 1) {
                Toast.makeText(requireContext(), "Time limit must be at least 1 minute", Toast.LENGTH_SHORT).show()
                false
            } else {
                true
            }
        }

        // Reset period minutes validation
        findPreference<EditTextPreference>(PreferenceKeys.RESET_PERIOD_MINUTES)?.setOnPreferenceChangeListener { _, newValue ->
            val minutes = newValue.toString().toIntOrNull()
            if (minutes == null || minutes < 1) {
                Toast.makeText(requireContext(), "Reset period must be at least 1 minute", Toast.LENGTH_SHORT).show()
                false
            } else {
                true
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            PreferenceKeys.APP_ENABLED -> {
                val isEnabled = sharedPreferences?.getBoolean(key, false) ?: false
                if (!isEnabled) {
                    startCountdown()
                }
                updatePreferenceDependencies()
            }
            PreferenceKeys.BLOCKING_MODE -> {
                updatePreferenceDependencies()
            }
            PreferenceKeys.LIMIT_TYPE -> {
                updatePreferenceDependencies()
            }
            PreferenceKeys.SCHEDULE_ENABLED -> {
                updatePreferenceDependencies()
            }
            PreferenceKeys.ALLOWLIST_ENABLED -> {
                updatePreferenceDependencies()
            }
        }
    }

    private fun updatePreferenceDependencies() {
        val isAppEnabled = settingsManager?.isAppEnabled ?: false
        val isOnlySwipingMode = settingsManager?.blockingMode == BlockingMode.ONLY_SWIPING
        val limitType = settingsManager?.limitType ?: LimitType.SWIPE_COUNT
        val isScheduleEnabled = settingsManager?.scheduleEnabled ?: false
        val isAllowlistEnabled = settingsManager?.allowlistEnabled ?: false

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
    }

    private fun updateTimeSummaries() {
        settingsManager?.let { manager ->
            findPreference<Preference>(PreferenceKeys.SCHEDULE_START_TIME)?.summary = manager.scheduleStartTime
            findPreference<Preference>(PreferenceKeys.SCHEDULE_END_TIME)?.summary = manager.scheduleEndTime
        }
    }

    private fun startCountdown() {
        cancelCountdown() // Cancel any existing countdown

        val countdownSeconds = 10
        var remainingSeconds = countdownSeconds

        // Create the dialog first
        countdownDialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.countdown_title))
            .setMessage(getString(R.string.countdown_message, remainingSeconds))
            .setCancelable(false)
            .setNegativeButton(getString(R.string.countdown_cancel)) { _, _ ->
                cancelCountdown()
                // Re-enable the app
                settingsManager?.isAppEnabled = true
                findPreference<SwitchPreferenceCompat>(PreferenceKeys.APP_ENABLED)?.isChecked = true
            }
            .create()

        countdownDialog?.show()

        countdownTimer = object : CountDownTimer((countdownSeconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = (millisUntilFinished / 1000).toInt() + 1
                countdownDialog?.setMessage(getString(R.string.countdown_message, remainingSeconds))
            }

            override fun onFinish() {
                countdownDialog?.dismiss()
                countdownDialog = null
                // App will remain disabled as set by the preference change
                Toast.makeText(requireContext(), "YouTube Shorts Blocker disabled", Toast.LENGTH_SHORT).show()
            }
        }
        countdownTimer?.start()
    }

    private fun cancelCountdown() {
        countdownTimer?.cancel()
        countdownTimer = null
        countdownDialog?.dismiss()
        countdownDialog = null
    }
}
