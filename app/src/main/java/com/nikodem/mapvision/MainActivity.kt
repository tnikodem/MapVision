package com.nikodem.mapvision

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMap.OnCameraMoveStartedListener
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.Property
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.io.File
import java.io.IOException
import java.util.Properties


// Constants for CSV file
private const val LOCATION_FILE_NAME = "recorded_locations.csv"

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var mapLibreMap: MapLibreMap

    private var F_Tracking = true
    private var F_Position = true

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 5000L

    private fun getMapStyleUrl(mapType: String): String {
        val properties = Properties()
        return try {
            assets.open("secrets.properties").use { inputStream ->
                properties.load(inputStream)
                when (mapType) {
                    "outdoor" -> properties.getProperty("MAP_STYLE_OUT")
                    "satellite" -> properties.getProperty("MAP_STYLE_SAT")
                    else -> properties.getProperty("MAP_STYLE_MAP")
                } ?: "https://default-url.com/style.json"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "https://default-url.com/style.json" // Fallback-URL
        }
    }

    private fun loadCoordinatesFromCSV(): MutableList<Triple<LatLng, Float, Long>> {
        val coordinatesData = mutableListOf<Triple<LatLng, Float, Long>>()

        try {
            val directory = File(filesDir, "LocationData")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val locationFile = File(directory, LOCATION_FILE_NAME)

            if (!locationFile.exists()) {
                Log.e("CSVReader", "CSV file not found: $LOCATION_FILE_NAME")
                return coordinatesData // Return empty list if file doesn't exist
            }
            // Read the CSV file from locationFile
            locationFile.bufferedReader().use { reader ->
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    val tokens = line?.split(',')
                    if (tokens != null && tokens.size >= 2) {
                        try {
                            val lat = tokens[0].trim().toDouble()
                            val lon = tokens[1].trim().toDouble()
                            val accuracy = tokens[2].trim().toFloat()
                            val time = tokens[3].trim().toLong()
                            coordinatesData.add(Triple(LatLng(lat, lon), accuracy, time))
                        } catch (e: NumberFormatException) {
                            Log.e("CSVReader", "Error parsing line: $line", e)
                            // Handle or log the error for the specific line
                        }
                    }
                }
            }

        } catch (e: IOException) {
            Log.e("CSVReader", "Error reading CSV file: $LOCATION_FILE_NAME", e)
            // Handle or log the file reading error
            // You might want to show a Toast to the user or return an empty list
        }
        return coordinatesData
    }

    private fun createFeatureCollection(): FeatureCollection {

        val coordinatesData = loadCoordinatesFromCSV()

        val featureCollection = FeatureCollection.fromFeatures(
            coordinatesData.map { (latLng, accuracy, _) ->
                val point = Point.fromLngLat(latLng.longitude, latLng.latitude)
                val feature = Feature.fromGeometry(point)
                feature.addNumberProperty("accuracy", accuracy)
                feature
            }.toTypedArray()
        )
        return featureCollection
    }

    private fun enableLocation() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 123
            )
        } else {
            val locationComponent = mapLibreMap.locationComponent
            val options = org.maplibre.android.location.LocationComponentActivationOptions.builder(
                this, mapLibreMap.style!!
            ).useDefaultLocationEngine(true).build()
            locationComponent.activateLocationComponent(options)
            locationComponent.isLocationComponentEnabled = true
            locationComponent.cameraMode = org.maplibre.android.location.modes.CameraMode.TRACKING
            locationComponent.renderMode = org.maplibre.android.location.modes.RenderMode.COMPASS
        }
    }

    private fun startMapUpdates() {
        // init map layer
        val featureCollection = createFeatureCollection()
        val source = GeoJsonSource(
            "coordinates", featureCollection
        )
        Log.d("Manfred", "StartLocationUpdates")
        mapLibreMap.style!!.addSource(source)
        val layer = CircleLayer("coordinates", source.id).withProperties(
            org.maplibre.android.style.layers.PropertyFactory.circleRadius(
                Expression.division(
                    Expression.get("accuracy"),
                    Expression.literal(30)
                )
            ), // Accuracy is in meters
            org.maplibre.android.style.layers.PropertyFactory.circleRadius(3f),
            org.maplibre.android.style.layers.PropertyFactory.circleColor(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.get("accuracy"),
                    Expression.stop(5, Expression.color(android.graphics.Color.RED)), // Full red below 5m
                    Expression.stop(25, Expression.color(android.graphics.Color.WHITE)) // Full white above 25m
                )
            )
        )
        mapLibreMap.style!!.addLayer(layer)

        // define update function
        handler.postDelayed(object : Runnable {
            override fun run() {
                updateMapWithCoordinates()
                handler.postDelayed(this, updateInterval)
            }
        }, updateInterval)

    }

    private fun updateMapWithCoordinates() {
        val featureCollection = createFeatureCollection()
        val source = mapLibreMap.style!!.getSourceAs<GeoJsonSource>("coordinates")
        if (source == null) {
            Log.d("Manfred", "GeoJsonSource 'coordinates' nicht gefunden!")
            return
        }
        source.setGeoJson(featureCollection)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineLocationGranted =
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)

            if (fineLocationGranted) {
                // Foreground location access granted.
                Log.d("Permissions", "Foreground location permission granted.")

                if (ContextCompat.checkSelfPermission(
                        this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    // Background location permission already granted.
                    Log.d("Permissions", "Background location permission already granted.")
                    startLocationServiceConditionally()
                } else {
                    // Background location permission not granted, request it.
                    // It's good practice to explain to the user why you need this.
                    showBackgroundLocationPermissionRationale()
                }

            } else {
                // Location permission was denied.
                // Explain to the user why the feature is unavailable and how to grant permission.
                Log.d("Permissions", "Foreground location permission denied.")
                Toast.makeText(
                    this,
                    "Location permission is required to track your position.",
                    Toast.LENGTH_LONG
                ).show()
                // Optionally, guide the user to app settings.
                // showPermissionDeniedDialog()
            }
        }

    private fun requestLocationPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)

        // For Android 12 (API 31) and above, you need to request FOREGROUND_SERVICE permission separately
        // if your service uses foregroundServiceType="location".
        // However, the `LocationService` example primarily focuses on location.
        // If you are using `foregroundServiceType` in manifest, ensure you handle its permission.

        requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    private fun showBackgroundLocationPermissionRationale() {
        AlertDialog.Builder(this).setTitle("Background Location Access")
            .setMessage("This app needs background location access to continuously record your route, even when the app is not in the foreground. Please grant 'Allow all the time' in the next screen.")
            .setPositiveButton("Grant Permission") { _, _ ->
                // Request background location permission.
                // This will typically take the user to the app's location permission settings.
                requestBackgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }.setNegativeButton("No, thanks") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(
                    this,
                    "Background location access denied. Route recording will be limited.",
                    Toast.LENGTH_LONG
                ).show()
            }.create().show()
    }

    // Launcher for requesting background location permission.
    // This will usually direct the user to the app's permission settings page for location.
    private val requestBackgroundLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.d("Permissions", "Background location permission granted.")
                startLocationServiceConditionally()
            } else {
                Log.d("Permissions", "Background location permission denied.")
                Toast.makeText(
                    this,
                    "Background location access denied. Route recording will be limited.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    // You would call this function when the user clicks a button to start tracking, for example.
    private fun startTrackingClicked() {
        // First, check if foreground location permissions are granted.
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            // Foreground permissions are granted. Now check for background if needed.
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // All necessary permissions granted
                startLocationServiceConditionally()
            } else {
                // Foreground granted, but background not. Show rationale for background.
                showBackgroundLocationPermissionRationale()
            }

        } else {
            // Foreground permissions not granted. Request them.
            requestLocationPermissions()
        }
    }

    /**
     * Starts the location service if all conditions (like permissions) are met.
     * You might add other checks here (e.g., is GPS enabled?).
     */
    private fun startLocationServiceConditionally() {
        // You might want to add a check here to ensure location services are enabled on the device.
        // For simplicity, directly starting the service here.
        Log.d("MainActivity", "All required location permissions granted. Starting service.")
        startLocationService() // This is the function you defined to start your LocationService
    }

    // To start the service (ensure permissions are granted first)
    private fun startLocationService() {
        val intent = Intent(this, LocationService::class.java).apply {
            action = LocationService.ACTION_START_LOCATION_SERVICE
        }
        startForegroundService(intent)
    }

    // To stop the service
    private fun stopLocationService() {
        val intent = Intent(this, LocationService::class.java).apply {
            action = LocationService.ACTION_STOP_LOCATION_SERVICE
        }
        startService(intent)
    }

    /**
     * Shows a dialog explaining that the permission was denied and offers to take the user
     * to the app settings.
     */
    @Suppress("unused") // This is a utility function you might want to use
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this).setTitle("Permission Denied")
            .setMessage("Location permission is required for this feature to work. Please enable it in app settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                // Take the user to the app settings page
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }.setNegativeButton("Cancel", null).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val mapType = sharedPreferences.getString("map_type", "normal") ?: "normal"

        // Init MapLibre
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        // Init the MapView
        mapView = findViewById(R.id.mapView)
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.cameraPosition =
                CameraPosition.Builder().target(LatLng(50.836, 6.07717)).zoom(12.0).build()

            val mapStyleUrl = getMapStyleUrl(mapType)
            map.setStyle(mapStyleUrl) {
                map.uiSettings.isCompassEnabled = true
                enableLocation()
                startMapUpdates()
            }

            // Zoom-In Button Callback
            val zoomInButton = findViewById<Button>(R.id.button_zoom_in)
            zoomInButton.setOnClickListener {
                map.animateCamera(CameraUpdateFactory.zoomIn())
            }
            // Zoom-Out Button Callback
            val zoomOutButton = findViewById<Button>(R.id.button_zoom_out)
            zoomOutButton.setOnClickListener {
                map.animateCamera(CameraUpdateFactory.zoomOut())
            }

            mapLibreMap.addOnCameraMoveStartedListener { reason ->
                if (reason == OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    //  val cameraPosition = mapLibreMap.cameraPosition
                    F_Position = false
                }
            }
        }

        // Settings Button Callback
        val settingsButton = findViewById<Button>(R.id.button_settings)
        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivityForResult(intent, 100)
        }

        // Settings Button Position
        val positionButton = findViewById<ImageButton>(R.id.location)
        positionButton.setOnClickListener {
            Log.d("Position", "Position-Button clicked")
            F_Position = true
        }
        // Start Button Callback
        val startButton = findViewById<Button>(R.id.button_start_recording)
        startButton.setOnClickListener {
            Log.d("Manfred", "Start-Button clicked")
            startTrackingClicked()
        }
        // Stop Button Callback
        val stopButton = findViewById<Button>(R.id.button_stop_recording)
        stopButton.setOnClickListener {
            Log.d("Manfred", "Stop-Button clicked")
            stopLocationService()
        }
        //CLear Button Callback
        val clearButton = findViewById<Button>(R.id.button_clear_recording)
        clearButton.setOnClickListener {
            Log.d("Manfred", "Clear-Button clicked")
            //Remove the location data from the CSV file
            val directory = File(filesDir, "LocationData")
            if (directory.exists()) {
                val locationFile = File(directory, LOCATION_FILE_NAME)
                if (locationFile.exists()) {
                    try {
                        locationFile.delete()
                        Log.d("Manfred", "Location data cleared from file: $LOCATION_FILE_NAME")
                        Toast.makeText(this, "Location data cleared", Toast.LENGTH_SHORT).show()
                        updateMapWithCoordinates()
                    } catch (e: IOException) {
                        Log.e("Manfred", "Error clearing location data", e)
                        Toast.makeText(this, "Error clearing location data", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    Log.d("Manfred", "No location data file found to clear.")
                }
            } else {
                Log.d("Manfred", "LocationData directory does not exist.")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        // Wert aktualisieren, falls er in den Einstellungen geändert wurde
        val sharedPreferences: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(this)
        F_Tracking = sharedPreferences.getBoolean("enable_tracking", true)
        Log.d("Manfred", "mapView.onResume, F_Tracking: $F_Tracking")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            // Einstellungen neu laden
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val mapType = sharedPreferences.getString("map_type", "normal") ?: "normal"
            val mapStyleUrl = getMapStyleUrl(mapType)
            Log.d("Manfred", "OnActivityResult")
            // Map-Style aktualisieren

            mapLibreMap.setStyle(mapStyleUrl) {
                Toast.makeText(this, "Map-Style aktualisiert", Toast.LENGTH_SHORT).show()
                // GeoJsonSource und Layer erneut hinzufügen
                val featureCollection = createFeatureCollection()
                val source = GeoJsonSource("coordinates", featureCollection)
                it.addSource(source)

                val layer = CircleLayer("coordinates", source.id).withProperties(
                    org.maplibre.android.style.layers.PropertyFactory.circleRadius(3f),
                    org.maplibre.android.style.layers.PropertyFactory.circleColor(
                        Expression.interpolate(
                            Expression.linear(),
                            Expression.get("accuracy"),
                            Expression.stop(5, Expression.color(android.graphics.Color.RED)), // Full red below 5m
                            Expression.stop(25, Expression.color(android.graphics.Color.BLUE)) // Full white above 25m
                        )
                    )
                )
                it.addLayer(layer)

                Log.d("Manfred", "GeoJsonSource und Layer wurden nach Style-Wechsel hinzugefügt.")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 123 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableLocation()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart(); mapView.onStart()
    }

    override fun onPause() {
        super.onPause(); mapView.onPause()
    }

    override fun onStop() {
        super.onStop(); mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy(); mapView.onDestroy()
    }
}