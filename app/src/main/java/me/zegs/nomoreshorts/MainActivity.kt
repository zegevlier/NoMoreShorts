package me.zegs.nomoreshorts

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import me.zegs.nomoreshorts.settings.SettingsFragment
import me.zegs.nomoreshorts.settings.SettingsManager
import me.zegs.nomoreshorts.ui.PermissionRequestActivity
import me.zegs.nomoreshorts.utils.AccessibilityUtils

class MainActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsManager = SettingsManager(this)

        // Check accessibility permission and route accordingly
        checkPermissionAndRoute()
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions when returning from other activities
        checkPermissionAndRoute()
    }

    private fun checkPermissionAndRoute() {
        if (AccessibilityUtils.isAccessibilityServiceEnabled(this)) {
            // Permission granted - show main settings
            showMainSettings()
        } else {
            // Need permission - show request screen
            showPermissionRequest()
        }
    }

    private fun showMainSettings() {
        // Only set content view if we haven't already
        if (findViewById<androidx.fragment.app.FragmentContainerView>(R.id.settings_container) == null) {
            setContentView(R.layout.activity_main)
            supportActionBar?.title = getString(R.string.settings_title)

            // Load the settings fragment
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    private fun showPermissionRequest() {
        val intent = Intent(this, PermissionRequestActivity::class.java)
        startActivity(intent)
        finish()
    }
}