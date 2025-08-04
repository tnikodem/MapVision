package com.nikodem.mapvision

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var locationFile: File? = null

    companion object {
        const val ACTION_START_LOCATION_SERVICE = "ACTION_START_LOCATION_SERVICE"
        const val ACTION_STOP_LOCATION_SERVICE = "ACTION_STOP_LOCATION_SERVICE"
        private const val NOTIFICATION_CHANNEL_ID = "location_service_channel"
        private const val NOTIFICATION_ID = 123
        private const val TAG = "LocationService"
        private const val LOCATION_FILE_NAME = "recorded_locations.csv"

        // Action to notify UI that a new location is available (optional, for live updates)
//        const val ACTION_NEW_LOCATION_BROADCAST = "com.nikodem.mapvision.NEW_LOCATION"
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationFile()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->


                    Log.d(TAG, "New Location: ${location.latitude}, ${location.longitude}. accuracy: ${location.accuracy}m")
                    storeLocation(System.currentTimeMillis(), location.latitude, location.longitude, location.accuracy)

//                    // Optional: Broadcast new location for live updates in UI
//                    val intent = Intent(ACTION_NEW_LOCATION_BROADCAST).apply {
//                        putExtra(EXTRA_LATITUDE, location.latitude)
//                        putExtra(EXTRA_LONGITUDE, location.longitude)
//                    }
//                    sendBroadcast(intent)
                }
            }
        }
    }

    private fun setupLocationFile() {
        // Store in internal storage, specific to this app
        val directory = File(filesDir, "LocationData")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        locationFile = File(directory, LOCATION_FILE_NAME)
        Log.d(TAG, "Location file path: ${locationFile?.absolutePath}")

    }

    private fun storeLocation(timestamp: Long, latitude: Double, longitude: Double, accuracy: Float) {
        locationFile?.let { file ->
            try {
                // Append the new location data
                FileOutputStream(file, true).bufferedWriter().use { writer ->
                    writer.appendLine("$latitude,$longitude,$accuracy,$timestamp")
                }
                Log.d(TAG, "Stored location: $latitude,$longitude,$accuracy,$timestamp")
            } catch (e: IOException) {
                Log.e(TAG, "Error writing location to file", e)
            }
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_LOCATION_SERVICE -> {
                Log.d(TAG, "Starting location service.")
                // Re-initialize file in case it was cleared or if service is restarted
                // Or decide if you want to append to existing file from previous sessions
                // For this example, we'll append if it exists, or create new if not.
                setupLocationFile() // Ensures file is ready and has header if new
                startForegroundServiceNotification()
                startLocationUpdates()
            }

            ACTION_STOP_LOCATION_SERVICE -> {
                Log.d(TAG, "Stopping location service.")
                stopLocationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE) // Use STOP_FOREGROUND_REMOVE to remove notification
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundServiceNotification() { // Renamed for clarity
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Location Service Active").setContentText("Recording your route...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your app's icon
            .setOngoing(true).build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Location Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Channel for the location tracking service"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    @SuppressWarnings("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000) // 10 seconds
                .setMinUpdateIntervalMillis(5000) // 5 seconds
                .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback, Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates started.")
        } catch (unlikely: SecurityException) {
            Log.e(TAG, "Lost location permission. Could not request updates. $unlikely")
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "Location updates stopped.")
            } else {
                Log.d(TAG, "Failed to remove location updates.")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        Log.d(TAG, "LocationService destroyed.")
    }
}