package com.nikodem.mapvision

import android.Manifest
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
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

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var mapLibreMap: MapLibreMap

    private val coordinates = mutableListOf<LatLng>()
    private val handler = android.os.Handler()
    private val updateInterval = 5000L // 5 seconds


    private fun getMapStyleUrl(): String {
        val properties = Properties()
        // Create file app/src/main/assets/secrets.properties
        // Add MAP_STYLE_URL="https://example.com/style.json"
        val inputStream = assets.open("secrets.properties")
        properties.load(inputStream)
        //return properties.getProperty("MAP_STYLE_URL")
        return properties.getProperty("MAP_STYLE_SAT")
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

        // Init MapLibre
        MapLibre.getInstance(this)

        setContentView(R.layout.activity_main)

        // Init the MapView
        mapView = findViewById(R.id.mapView)
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.cameraPosition = CameraPosition.Builder().target(LatLng(50.826, 6.07717)).zoom(12.0).build()

            map.setStyle(getMapStyleUrl()){
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
        }
    }

    private fun showAachenMarker() {
        val aachenLatLng = LatLng(50.826, 6.07717)
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
                if (lastLocation != null) {
                    val latLng = LatLng(lastLocation.latitude, lastLocation.longitude)
                    coordinates.add(latLng)
                    updateMapWithCoordinates()
                } else {
                    //FIXME
                    val latLng = LatLng(
                        50.826 + (Math.random() - 0.5) * 0.005,
                        6.07717 + (Math.random() - 0.5) * 0.005
                    )
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