package com.nikodem.mapvision

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.nikodem.mapvision.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}