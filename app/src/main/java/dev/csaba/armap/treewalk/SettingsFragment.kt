package dev.csaba.armap.treewalk

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dev.csaba.armap.treewalk.helpers.openBrowserWindow

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == "connect_button") {
            openBrowserWindow(TreeWalkGeoActivity.WEBSITE_URL, context)
            return true
        }

        return false
    }
}
