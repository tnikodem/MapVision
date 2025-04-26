package com.nikodem.mapvision

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.camera.CameraPosition
import java.util.Properties

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var mapLibreMap: MapLibreMap

    private fun getMapStyleUrl(): String {
        val properties = Properties()
        // In src.main.assets/secrets.properties
        // Add the file secrets.properties with the content MAP_STYLE_URL="https://example.com/style.json"
        val inputStream = assets.open("secrets.properties")
        properties.load(inputStream)
        return properties.getProperty("MAP_STYLE_URL")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init MapLibre
        MapLibre.getInstance(this)

        setContentView(R.layout.activity_main)

        // Init the MapView
        mapView = findViewById(R.id.mapView)
        mapView.getMapAsync { map ->
            map.setStyle(getMapStyleUrl()){
                // Aktivieren der MapControls
                map.uiSettings.isCompassEnabled = true
            }
            mapLibreMap = map
            map.cameraPosition = CameraPosition.Builder().target(LatLng(50.7,6.0)).zoom(15.0).build()
        }

    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
}