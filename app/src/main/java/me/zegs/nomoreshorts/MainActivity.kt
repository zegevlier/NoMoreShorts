package me.zegs.nomoreshorts

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import me.zegs.nomoreshorts.settings.SettingsFragment
import me.zegs.nomoreshorts.settings.SettingsManager
import me.zegs.nomoreshorts.ui.PermissionRequestActivity
import me.zegs.nomoreshorts.utils.AccessibilityUtils

class MainActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private var isCheckingPermissions = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            settingsManager = SettingsManager(this)

            // Start persistence service to keep accessibility service running
            PersistenceService.start(this)

            // Check accessibility permission and route accordingly
            checkPermissionAndRoute()
        } catch (e: Exception) {
            showError("Failed to initialize app: ${e.message}")
            finish()
        }
    }

    override fun onResume() {
        super.onResume()

        // Re-check permissions when returning from other activities
        // Add a small delay to avoid rapid successive checks
        if (!isCheckingPermissions) {
            Handler(Looper.getMainLooper()).postDelayed({
                checkPermissionAndRoute()
            }, 300)
        }
    }

    private fun checkPermissionAndRoute() {
        if (isCheckingPermissions) return

        isCheckingPermissions = true

        try {
            // Use a background thread for permission checking to avoid blocking UI
            Thread {
                try {
                    val hasPermission = AccessibilityUtils.isAccessibilityServiceEnabled(this)

                    runOnUiThread {
                        try {
                            if (hasPermission) {
                                // Permission granted - show main settings
                                showMainSettings()
                            } else {
                                // Need permission - show request screen
                                showPermissionRequest()
                            }
                        } catch (e: Exception) {
                            showError("Failed to load interface: ${e.message}")
                        } finally {
                            isCheckingPermissions = false
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        showError("Failed to check permissions: ${e.message}")
                        isCheckingPermissions = false
                    }
                }
            }.start()

        } catch (e: Exception) {
            showError("Failed to start permission check: ${e.message}")
            isCheckingPermissions = false
        }
    }

    private fun showMainSettings() {
        try {
            // Only set content view if we haven't already
            if (findViewById<androidx.fragment.app.FragmentContainerView>(R.id.settings_container) == null) {
                setContentView(R.layout.activity_main)
                supportActionBar?.title = getString(R.string.settings_title)

                // Load the settings fragment with error handling
                try {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.settings_container, SettingsFragment())
                        .commit()
                } catch (e: Exception) {
                    showError("Failed to load settings: ${e.message}")
                }
            }
        } catch (e: Exception) {
            showError("Failed to show main settings: ${e.message}")
        }
    }

    private fun showPermissionRequest() {
        try {
            val intent = Intent(this, PermissionRequestActivity::class.java)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            showError("Failed to open permission request: ${e.message}")
        }
    }

    private fun showError(message: String) {
        try {
            val rootView = findViewById<View>(android.R.id.content)
            if (rootView != null) {
                Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)
                    .setBackgroundTint(getColor(android.R.color.holo_red_light))
                    .setAction("Retry") {
                        checkPermissionAndRoute()
                    }
                    .show()
            } else {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {
            // Fallback to Toast if Snackbar fails
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
}