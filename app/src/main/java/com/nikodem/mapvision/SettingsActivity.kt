package com.nikodem.mapvision

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import android.util.Log
import android.app.Activity
import android.content.Intent
import android.util.Xml
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import org.maplibre.android.geometry.LatLng
import java.io.InputStream
import androidx.activity.result.contract.ActivityResultContracts

class SettingsActivity : AppCompatActivity() {

    private lateinit var coordinates: MutableList<LatLng>
    private lateinit var saveFileLauncher: ActivityResultLauncher<Intent>
    private lateinit var loadFileLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_settings) // Verweis auf die Layout-Datei

        // ActivityResultLauncher initialisieren
        saveFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        saveTrackToXml(outputStream)
                        Toast.makeText(this, "Track gespeichert", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        loadFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val loadedCoordinates = loadTrackFromXml(inputStream)
                        coordinates.clear()
                        coordinates.addAll(loadedCoordinates)
                        Toast.makeText(this, "Track geladen", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Empfange die Koordinaten aus der MainActivity
        coordinates = intent.getSerializableExtra("coordinates") as? MutableList<LatLng> ?: mutableListOf()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings) // Verweis auf die Layout-Datei
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_fragment_container, SettingsFragment())
            .commit()

        // Button zum Speichern konfigurieren
        val saveButton = findViewById<Button>(R.id.PB_Save)
        saveButton.setOnClickListener {
            Log.d("SettingsActivity", "Speichern-Button geklickt")
            openFileDialogForSaving()
        }

        // Button zum Laden konfigurieren
        val loadButton = findViewById<Button>(R.id.PB_Load)
        loadButton.setOnClickListener {
            openFileDialogForLoading()
        }

        // Exit-Button konfigurieren
        val exitButton = findViewById<Button>(R.id.Exit_Settings)
        exitButton.setOnClickListener {
            setResult(RESULT_OK) // Ergebnis setzen
            finish() // Schließt die Aktivität
        }
    }
    private fun openFileDialogForSaving() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/xml"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, "track.xml")
        }
        saveFileLauncher.launch(intent)
    }

    private fun openFileDialogForLoading() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/xml"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        loadFileLauncher.launch(intent)
    }



    private fun saveTrackToXml(outputStream: java.io.OutputStream) {
        val serializer = android.util.Xml.newSerializer()
        serializer.setOutput(outputStream, "UTF-8")
        serializer.startDocument("UTF-8", true)
        serializer.startTag(null, "Track")

        for (coordinate in coordinates) {
            serializer.startTag(null, "Point")
            serializer.attribute(null, "latitude", coordinate.latitude.toString())
            serializer.attribute(null, "longitude", coordinate.longitude.toString())
            serializer.endTag(null, "Point")
        }

        serializer.endTag(null, "Track")
        serializer.endDocument()
        outputStream.close()
    }

    private fun loadTrackFromXml(inputStream: InputStream): List<LatLng> {
        val coordinates = mutableListOf<LatLng>()
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var latitude = 0.0
        var longitude = 0.0
/*
        while (eventType != android.util.XmlPullParser.END_DOCUMENT) {
            if (eventType == android.util.XmlPullParser.START_TAG && parser.name == "Point") {
                latitude = parser.getAttributeValue(null, "latitude").toDouble()
                longitude = parser.getAttributeValue(null, "longitude").toDouble()
                coordinates.add(LatLng(latitude, longitude))
            }
            eventType = parser.next()
        }
  */
        inputStream.close()

        return coordinates
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
        }
    }
}

