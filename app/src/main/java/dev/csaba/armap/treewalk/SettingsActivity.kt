package dev.csaba.armap.treewalk

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_VISIBLE
                or View.STATUS_BAR_VISIBLE)
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun notifyFinish() {
        val resultIntent = Intent()
        setResult(TreeWalkGeoActivity.RC_APPLY_SETTINGS, resultIntent)
        finish()
    }

    override fun onDestroy() {
        notifyFinish()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        notifyFinish()
        return true
    }
}
