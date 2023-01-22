package dev.csaba.armap.treewalk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    private fun openBrowserWindow(url: String?, context: Context?) {
        val uris = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uris)
        val bundle = Bundle()
        bundle.putBoolean("new_window", true)
        intent.putExtras(bundle)
        context?.startActivity(intent)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == "connect_button") {
            openBrowserWindow(TreeWalkGeoActivity.WEBSITE_URL, context)
            return true
        }

        return false
    }
}
