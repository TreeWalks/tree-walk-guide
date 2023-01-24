package dev.csaba.armap.treewalk

import android.os.Bundle
import android.provider.Settings
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dev.csaba.armap.treewalk.helpers.openBrowserWindow

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val developerMode = Settings.Secure.getInt(
            activity?.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
        )
        if (developerMode <= 0) {
            val localDebugPref: Preference? = preferenceManager.findPreference("local_test")
            localDebugPref?.isVisible = false
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == "connect_button") {
            openBrowserWindow(TreeWalkGeoActivity.WEBSITE_URL, context)
            return true
        }

        return false
    }
}
