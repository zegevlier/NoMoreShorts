package me.zegs.nomoreshorts.utils

import android.content.Context
import android.provider.Settings

object AccessibilityUtils {

    /**
     * Checks if the NoMoreShorts accessibility service is currently enabled
     * @param context Android context to access system settings
     * @return true if the accessibility service is enabled, false otherwise
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return try {
            val services = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

            // Handle case where services might be null or empty
            if (services.isNullOrEmpty()) {
                return false
            }

            // Check if our service is in the list - be more flexible with the matching
            val packageName = context.packageName
            val isEnabled = services.contains(packageName) &&
                    (services.contains("ShortsAccessibilityService") ||
                            services.contains("MyAccessibilityService"))

            return isEnabled

        } catch (_: Exception) {
            false
        }
    }
}
