package me.zegs.nomoreshorts.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import me.zegs.nomoreshorts.R
import me.zegs.nomoreshorts.utils.AccessibilityUtils

class PermissionRequestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_request)

        setupViews()
        setupBackPressedHandler()
    }

    private fun setupViews() {
        val titleText = findViewById<TextView>(R.id.titleText)
        val descriptionText = findViewById<TextView>(R.id.descriptionText)
        val grantPermissionButton = findViewById<Button>(R.id.grantPermissionButton)

        titleText.text = getString(R.string.permission_title)
        descriptionText.text = getString(R.string.permission_description)
        grantPermissionButton.text = getString(R.string.grant_permission)

        grantPermissionButton.setOnClickListener {
            openAccessibilitySettings()
        }

        supportActionBar?.title = getString(R.string.permission_title)
    }

    private fun setupBackPressedHandler() {
        // Handle back button press with modern API
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // User pressed back - just stay on this screen since we need the permission
                // They can use the system back button to exit the app if they want
                // We do nothing to prevent going back
            }
        })
    }

    private fun openAccessibilitySettings() {
        // First check if we already have permission
        if (AccessibilityUtils.isAccessibilityServiceEnabled(this)) {
            // Already have permission, go to main settings
            val intent = Intent(this, me.zegs.nomoreshorts.MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
            return
        }

        // Don't have permission, open accessibility settings
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } catch (_: Exception) {
            // Fallback to general accessibility settings
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if permission was granted when user returns
        if (AccessibilityUtils.isAccessibilityServiceEnabled(this)) {
            // Permission granted, go to main activity which will show settings
            val intent = Intent(this, me.zegs.nomoreshorts.MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }
}
