package dev.csaba.armap.treewalk.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle

fun openBrowserWindow(url: String?, context: Context?) {
    val uris = Uri.parse(url)
    val intent = Intent(Intent.ACTION_VIEW, uris)
    val bundle = Bundle()
    bundle.putBoolean("new_window", true)
    intent.putExtras(bundle)
    context?.startActivity(intent)
}
