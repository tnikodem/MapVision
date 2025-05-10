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
import org.maplibre.android.geometry.LatLng
import java.io.File
import java.io.FileOutputStream
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory

class SettingsActivity : AppCompatActivity() {

    private lateinit var coordinates: MutableList<LatLng>

    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_settings) // Verweis auf die Layout-Datei

        // Empfange die Koordinaten aus der MainActivity
        coordinates = intent.getSerializableExtra("coordinates") as? MutableList<LatLng> ?: mutableListOf()

        // Button zum Speichern konfigurieren
        val saveButton = findViewById<Button>(R.id.PB_Save)
        saveButton.setOnClickListener {
            openFileDialogForSaving()
        }

        // Button zum Laden konfigurieren
        val loadButton = findViewById<Button>(R.id.PB_Load)
        loadButton.setOnClickListener {
            openFileDialogForLoading()
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings) // Verweis auf die Layout-Datei
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_fragment_container, SettingsFragment())
            .commit()
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
        startActivityForResult(intent, 101)
    }

    private fun openFileDialogForLoading() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/xml"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, 102)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("SettingsActivity", "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
        if (resultCode == Activity.RESULT_OK && data != null) {
            val uri = data.data
            when (requestCode) {
                101 -> {
                    // Speichern
                    uri?.let {
                        contentResolver.openOutputStream(it)?.use { outputStream ->
                            saveTrackToXml(outputStream)
                            Toast.makeText(this, "Track gespeichert", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                102 -> {
                    // Laden
                    uri?.let {
                        contentResolver.openInputStream(it)?.use { inputStream ->
                            val loadedCoordinates = loadTrackFromXml(inputStream)
                            coordinates.clear()
                            coordinates.addAll(loadedCoordinates)
                            Toast.makeText(this, "Track geladen", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
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

