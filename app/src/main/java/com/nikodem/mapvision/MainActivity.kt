package com.nikodem.mapvision

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.util.Properties
import android.util.Log

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var mapLibreMap: MapLibreMap
    private val coordinates = mutableListOf<LatLng>()
    private val handler = android.os.Handler()
    private val updateInterval = 5000L // 5 seconds

    private val isEmulator = (Build.FINGERPRINT.startsWith("generic")
    || Build.FINGERPRINT.startsWith("unknown")
    || Build.FINGERPRINT.contains("emu64xa:16")
    || Build.MODEL.contains("google_sdk")
    || Build.MODEL.contains("Android SDK built for x86")
    || Build.MANUFACTURER.contains("Genymotion")
    || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
    || "google_sdk" == Build.PRODUCT)

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

    private fun createFeatureCollection(): FeatureCollection {
        val featureCollection = FeatureCollection.fromFeatures(
            coordinates.map { coordinate ->
                val point = Point.fromLngLat(coordinate.longitude, coordinate.latitude)
                Feature.fromGeometry(point)
            }.toTypedArray()
        )
        return featureCollection
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val mapType = sharedPreferences.getString("map_type", "normal")?:"normal"

        // Init MapLibre
        MapLibre.getInstance(this)

        setContentView(R.layout.activity_main)

        // Init the MapView
        mapView = findViewById(R.id.mapView)
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.cameraPosition = CameraPosition.Builder().target(LatLng(50.836, 6.07717)).zoom(12.0).build()

            val mapStyleUrl = getMapStyleUrl(mapType)
            map.setStyle(mapStyleUrl) {
                map.uiSettings.isCompassEnabled = true
                showAachenMarker()
                enableLocation()
                startLocationUpdates()
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
            // Settings Button Callback
            val settingsButton = findViewById<Button>(R.id.button_settings)
            settingsButton.setOnClickListener {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivityForResult(intent, 100)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            // Einstellungen neu laden
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val mapType = sharedPreferences.getString("map_type", "normal") ?: "normal"
            val mapStyleUrl = getMapStyleUrl(mapType)

            // Map-Style aktualisieren
            mapLibreMap.setStyle(mapStyleUrl) {
                Toast.makeText(this, "Map-Style aktualisiert", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAachenMarker() {
        val aachenLatLng = LatLng(50.836, 6.07717)
        mapLibreMap.addMarker(
            MarkerOptions()
                .position(aachenLatLng)
                .title("Aachen")
        )
        mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(aachenLatLng, 12.0))
    }

    private fun startLocationUpdates() {
        // init map layer
        val featureCollection = createFeatureCollection()
        val source = GeoJsonSource(
            "coordinates",
            featureCollection
        )
        mapLibreMap.style!!.addSource(source)
        val layer = CircleLayer("coordinates", source.id).withProperties(
            org.maplibre.android.style.layers.PropertyFactory.circleRadius(3f),
            org.maplibre.android.style.layers.PropertyFactory.circleColor("red")
        )
        mapLibreMap.style!!.addLayer(layer)

        // define update function
        handler.postDelayed(object : Runnable {
            override fun run() {
                val locationComponent = mapLibreMap.locationComponent
                val lastLocation = locationComponent.lastKnownLocation
                if (lastLocation != null && isEmulator == false) {
                    val latLng = LatLng(lastLocation.latitude, lastLocation.longitude)
                    mapLibreMap.cameraPosition = CameraPosition.Builder()
                        .target(latLng)
                        .build()
                    coordinates.add(latLng)
                    updateMapWithCoordinates()
                } else {
                    Log.d("MainActivity", "Neue Koordinate: NULL")    //FIXME
                    val latLng = LatLng(
                        50.836 + (Math.random() - 0.5) * 0.005,
                        6.07717 + (Math.random() - 0.5) * 0.005)
                    mapLibreMap.cameraPosition = CameraPosition.Builder()
                        .target(latLng)
                        .build()
                    coordinates.add(latLng)
                    updateMapWithCoordinates()
                }
                handler.postDelayed(this, updateInterval)
            }
        }, updateInterval)
    }

    private fun updateMapWithCoordinates() {
        // Update geojson layer to show all coordinates
        val source = mapLibreMap.style!!.getSourceAs<GeoJsonSource>("coordinates")
        source?.setGeoJson(createFeatureCollection())

        //show toast with last coordinates
        val lastCoordinate = coordinates.last()
        Toast.makeText(this, "Last Coordinate: $lastCoordinate", Toast.LENGTH_SHORT).show()
    }

    private fun enableLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                123
            )
        } else {
            val locationComponent = mapLibreMap.locationComponent
            val options = org.maplibre.android.location.LocationComponentActivationOptions
                .builder(this, mapLibreMap.style!!)
                .useDefaultLocationEngine(true)
                .build()
            locationComponent.activateLocationComponent(options)
            locationComponent.isLocationComponentEnabled = true
            locationComponent.cameraMode = org.maplibre.android.location.modes.CameraMode.TRACKING
            locationComponent.renderMode = org.maplibre.android.location.modes.RenderMode.COMPASS
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 123 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableLocation()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
}