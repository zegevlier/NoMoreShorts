package me.zegs.nomoreshorts

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import me.zegs.nomoreshorts.settings.SettingsFragment
import me.zegs.nomoreshorts.settings.SettingsManager
import me.zegs.nomoreshorts.ui.PermissionRequestActivity

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
        if (isAccessibilityServiceEnabled()) {
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

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val services = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

            println("Enabled accessibility services: '$services'")
            println("Looking for package: $packageName")

            // Handle case where services might be null or empty
            if (services.isNullOrEmpty()) {
                println("No accessibility services enabled")
                return false
            }

            // Check if our service is in the list - be more flexible with the matching
            val isEnabled = services.contains(packageName) &&
                    (services.contains("ShortsAccessibilityService") ||
                            services.contains("MyAccessibilityService"))

            println("Service enabled: $isEnabled")
            return isEnabled

        } catch (e: Exception) {
            println("Error checking accessibility service: ${e.message}")
            false
        }
    }
}