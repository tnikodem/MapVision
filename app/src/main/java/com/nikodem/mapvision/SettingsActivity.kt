package com.nikodem.mapvision

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import android.util.Log

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
        }
    }
}

