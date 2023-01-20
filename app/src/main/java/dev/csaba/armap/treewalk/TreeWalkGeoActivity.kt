/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.csaba.armap.treewalk

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.*
import com.oguzdev.circularfloatingactionmenu.library.FloatingActionButton
import com.oguzdev.circularfloatingactionmenu.library.FloatingActionMenu
import com.oguzdev.circularfloatingactionmenu.library.SubActionButton
import dev.csaba.armap.common.helpers.FullScreenHelper
import dev.csaba.armap.common.samplerender.SampleRender
import dev.csaba.armap.treewalk.helpers.*
import io.reactivex.BackpressureStrategy
import io.reactivex.android.schedulers.AndroidSchedulers.mainThread
import io.reactivex.disposables.Disposables
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit


class TreeWalkGeoActivity : AppCompatActivity() {
  companion object {
    private const val TAG = "TreeWalkGeoActivity"
    private const val LOCATIONS_FILE_NAME = "locations.xml"
    private const val LOCATIONS_EN_FILE_NAME = "locations_es.xml"
    private const val LOCATIONS_ES_FILE_NAME = "locations_en.xml"
    private const val WEBSITE_URL = "https://treewalks.github.io/"
    private const val DEFAULT_LANGUAGE = "en"
  }

  lateinit var arCoreSessionHelper: ARCoreSessionLifecycleHelper
  lateinit var view: TreeWalkGeoView
  private lateinit var renderer: TreeWalkGeoRenderer
  private val fileDownloader by lazy {
    FileDownloader(
      OkHttpClient.Builder().build()
    )
  }
  private var disposable = Disposables.disposed()
  private var currentLanguage = DEFAULT_LANGUAGE
  private var hasSemanticApi = false

  private fun createCircularFABMenu() {
    // Set up the white button on the lower right corner
    // more or less with default parameter
    val fabMenuIcon = ImageView(this)
    fabMenuIcon.setImageDrawable(ContextCompat.getDrawable(this.baseContext, R.drawable.baseline_add_24))
    val rightLowerButton: FloatingActionButton = FloatingActionButton.Builder(this)
      .setContentView(fabMenuIcon)
      .build()

    val fabSubBuilder: SubActionButton.Builder = SubActionButton.Builder(this)
    val doneIcon = ImageView(this)
    val numbersIcon = ImageView(this)
    val voiceOnOffIcon = ImageView(this)
    val wifiScanIcon = ImageView(this)
    val translateIcon = ImageView(this)
    val informationIcon = ImageView(this)
    val devModeIcon = ImageView(this)

    doneIcon.setImageDrawable(ContextCompat.getDrawable(this.baseContext, R.drawable.baseline_done_24))
    numbersIcon.setImageDrawable(ContextCompat.getDrawable(this.baseContext, R.drawable.baseline_numbers_24))
    voiceOnOffIcon.setImageDrawable(ContextCompat.getDrawable(this.baseContext, R.drawable.baseline_volume_up_24))
    wifiScanIcon.setImageDrawable(ContextCompat.getDrawable(this.baseContext, R.drawable.baseline_perm_scan_wifi_24))
    translateIcon.setImageDrawable(ContextCompat.getDrawable(this.baseContext, R.drawable.baseline_translate_24))
    informationIcon.setImageDrawable(ContextCompat.getDrawable(this.baseContext, R.drawable.baseline_info_outline_24))
    devModeIcon.setImageDrawable(ContextCompat.getDrawable(this.baseContext, R.drawable.baseline_developer_mode_24))

    // Build the menu with default options: light theme, 90 degrees, 72dp radius.
    // Set default SubActionButtons
    val rightLowerMenu: FloatingActionMenu = FloatingActionMenu.Builder(this)
      .setRadius(600)
      .addSubActionView(fabSubBuilder.setContentView(doneIcon).build(), 196, 196)
      .addSubActionView(fabSubBuilder.setContentView(numbersIcon).build(), 196, 196)
      .addSubActionView(fabSubBuilder.setContentView(voiceOnOffIcon).build(), 196, 196)
      .addSubActionView(fabSubBuilder.setContentView(wifiScanIcon).build(), 196, 196)
      .addSubActionView(fabSubBuilder.setContentView(translateIcon).build(), 196, 196)
      .addSubActionView(fabSubBuilder.setContentView(informationIcon).build(), 196, 196)
      .addSubActionView(fabSubBuilder.setContentView(devModeIcon).build(), 196, 196)
      .attachTo(rightLowerButton)
      .build()

    translateIcon.setOnClickListener {
      val builder = AlertDialog.Builder(this)
      builder.setTitle(resources.getString(R.string.select_language))
      val languages = arrayOf(resources.getString(R.string.language_english), resources.getString(R.string.language_spanish))
      val checkedItem = if (currentLanguage == "en") 0 else 1

      var selectedLocale = ""
      builder.setSingleChoiceItems(
        languages, checkedItem
      ) { _, which ->
        selectedLocale = if (which == 0) "en" else "es"
      }

      builder.setPositiveButton(resources.getString(R.string.apply_action)) { _, _ ->
        if (selectedLocale != currentLanguage) {
          val localeList = LocaleListCompat.forLanguageTags(selectedLocale)
          AppCompatDelegate.setApplicationLocales(localeList)
        }
      }

      builder.setNegativeButton(resources.getString(R.string.cancel_action), null)
      builder.show()
    }

    devModeIcon.setOnClickListener {
      view.snackbarHelper.showMessage(this, "Dev Mode!")
    }

    // Listen menu open and close events to animate the button content view
    rightLowerMenu.setStateChangeListener(object : FloatingActionMenu.MenuStateChangeListener {
      override fun onMenuOpened(menu: FloatingActionMenu?) {
        // Rotate the icon of rightLowerButton 45 degrees clockwise
        fabMenuIcon.rotation = 0f
        val pvhR = PropertyValuesHolder.ofFloat(View.ROTATION, 45f)
        val animation = ObjectAnimator.ofPropertyValuesHolder(fabMenuIcon, pvhR)
        animation.start()
      }

      override fun onMenuClosed(menu: FloatingActionMenu?) {
        // Rotate the icon of rightLowerButton 45 degrees counter-clockwise
        fabMenuIcon.rotation = 45f
        val pvhR = PropertyValuesHolder.ofFloat(View.ROTATION, 0f)
        val animation = ObjectAnimator.ofPropertyValuesHolder(fabMenuIcon, pvhR)
        animation.start()
      }
    })
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    RxJavaPlugins.setErrorHandler {
      Log.e("Error", it.localizedMessage ?: "")
    }

    val arCoreVersion: Long = packageManager.getVersionCodeCompat("com.google.ar.core")
    hasSemanticApi = arCoreVersion >= 223620091

    // Setup ARCore session lifecycle helper and configuration.
    arCoreSessionHelper = ARCoreSessionLifecycleHelper(this)
    // If Session creation or Session.resume() fails, display a message and log detailed
    // information.
    arCoreSessionHelper.exceptionCallback =
      { exception ->
        val message =
          when (exception) {
            is UnavailableUserDeclinedInstallationException ->
              resources.getString(R.string.install_google_play)
            is UnavailableApkTooOldException -> resources.getString(R.string.update_ar_core)
            is UnavailableSdkTooOldException -> resources.getString(R.string.update_this_app)
            is UnavailableDeviceNotCompatibleException -> resources.getString(R.string.no_ar_support)
            is CameraNotAvailableException -> resources.getString(R.string.camera_not_available)
            else -> resources.getString(R.string.ar_core_exception, exception.toString())
          }
        Log.e(TAG, "ARCore threw an exception", exception)
        view.snackbarHelper.showError(this, message)
      }

    // Configure session features.
    arCoreSessionHelper.beforeSessionResume = ::configureSession
    lifecycle.addObserver(arCoreSessionHelper)

    // Set up the Tree Walk AR renderer.
    renderer = TreeWalkGeoRenderer(this)
    lifecycle.addObserver(renderer)

    // Set up Tree Walk AR UI.
    view = TreeWalkGeoView(this)
    lifecycle.addObserver(view)
    setContentView(view.root)

    // Sets up an example renderer using our TreeWalkGeoRenderer.
    SampleRender(view.surfaceView, renderer, assets)

    // https://github.com/android/user-interface-samples/tree/main/PerAppLanguages
    // https://android-developers.googleblog.com/2022/11/per-app-language-preferences-part-1.html
    // Fetching the current application locale using the AndroidX support Library
    currentLanguage = if (!AppCompatDelegate.getApplicationLocales().isEmpty) {
      // Fetches the current Application Locale from the list
      AppCompatDelegate.getApplicationLocales()[0]?.language ?: DEFAULT_LANGUAGE
    } else {
      // Fetches the default System Locale
      Locale.getDefault().language
    }

    // Create circular FAB menu
    createCircularFABMenu()

//    lifecycle.coroutineScope.launch {
//      downloadLocationsAsync(LOCATIONS_FILE_NAME)
//      downloadLocationsAsync(LOCATIONS_EN_FILE_NAME)
//      downloadLocationsAsync(LOCATIONS_ES_FILE_NAME)
//    }
  }

  override fun onDestroy() {
    super.onDestroy()
    disposable.dispose()
  }

  private suspend fun downloadLocationsAsync(fileName: String): Deferred<Int> = coroutineScope {
    async {
      var cachedFile = File(cacheDir, fileName)

      disposable = fileDownloader.download(WEBSITE_URL + fileName, cachedFile)
        .throttleFirst(100, TimeUnit.MILLISECONDS)
        .toFlowable(BackpressureStrategy.LATEST)
        .subscribeOn(Schedulers.io())
        .observeOn(mainThread())
        .subscribe({
          Log.i(TAG, "$it% Downloaded")
        }, {
          Log.e(TAG, it.localizedMessage, it)
          cachedFile = File(cacheDir, fileName)
          renderer.processLocations(cachedFile)
        }, {
          Log.i(TAG, "Download Complete")
          renderer.processLocations(cachedFile)
        })

      return@async 0
    }
  }

  // Configure the session, setting the desired options according to your use case.
  private fun configureSession(session: Session) {
    session.configure(
      session.config.apply {
        // Enable Geospatial Mode.
        geospatialMode = Config.GeospatialMode.ENABLED
        // This finding mode is probably the default
        // https://developers.google.com/ar/develop/java/geospatial/terrain-anchors
        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
      }
    )
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    results: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, results)
    if (!GeoPermissionsHelper.hasGeoPermissions(this)) {
      // Use toast instead of snack bar here since the activity will exit.
      Toast.makeText(this, resources.getString(R.string.permissions_needed), Toast.LENGTH_LONG)
        .show()
      if (!GeoPermissionsHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        GeoPermissionsHelper.launchPermissionSettings(this)
      }
      finish()
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
  }
}
