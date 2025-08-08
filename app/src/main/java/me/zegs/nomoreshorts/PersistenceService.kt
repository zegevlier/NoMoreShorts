package me.zegs.nomoreshorts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import me.zegs.nomoreshorts.models.PreferenceKeys
import me.zegs.nomoreshorts.settings.SettingsManager

class PersistenceService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val TAG = "PersistenceService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "accessibility_persistence"
        private const val CHECK_INTERVAL_MS = 30000L // 30 seconds

        fun start(context: Context) {
            val intent = Intent(context, PersistenceService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PersistenceService::class.java)
            context.stopService(intent)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var checkRunnable: Runnable? = null
    private lateinit var settingsManager: SettingsManager
    private lateinit var preferences: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PersistenceService created")

        // Initialize settings manager and preferences
        settingsManager = SettingsManager(this)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        // Register for preference changes
        preferences.registerOnSharedPreferenceChangeListener(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startAccessibilityServiceMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "PersistenceService started")
        return START_STICKY // Restart if killed by system
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "PersistenceService destroyed")

        // Unregister preference listener
        try {
            preferences.unregisterOnSharedPreferenceChangeListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering preference listener", e)
        }

        checkRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == PreferenceKeys.APP_ENABLED) {
            val isEnabled = sharedPreferences?.getBoolean(PreferenceKeys.APP_ENABLED, false) ?: false
            Log.d(TAG, "App enabled setting changed: $isEnabled")

            if (!isEnabled) {
                Log.d(TAG, "App disabled, stopping persistence service")
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.persistence_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.persistence_notification_channel_description)
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // Create intent to open MainActivity when notification is tapped
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.persistence_notification_title))
            .setContentText(getString(R.string.persistence_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .build()
    }

    private fun startAccessibilityServiceMonitoring() {
        checkRunnable = object : Runnable {
            override fun run() {
                try {
                    // Check if app is still enabled before each monitoring cycle
                    if (!settingsManager.isAppEnabled) {
                        Log.d(TAG, "App disabled during monitoring, stopping service")
                        stopSelf()
                        return
                    }

                    if (!isAccessibilityServiceEnabled()) {
                        Log.w(TAG, "Accessibility service is not enabled")
                        // Note: We cannot programmatically enable the accessibility service
                        // User must manually enable it in settings
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking accessibility service status", e)
                }

                // Schedule next check only if service is still running
                if (!settingsManager.isAppEnabled) {
                    Log.d(TAG, "App disabled, not scheduling next check")
                    return
                }

                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }

        // Start monitoring
        handler.post(checkRunnable!!)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "${packageName}/${ShortsAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        return enabledServices?.contains(serviceName) == true
    }
}
