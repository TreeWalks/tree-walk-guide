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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.coroutineScope
import com.oguzdev.circularfloatingactionmenu.library.FloatingActionButton
import com.oguzdev.circularfloatingactionmenu.library.FloatingActionMenu
import com.oguzdev.circularfloatingactionmenu.library.SubActionButton
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.*
import dev.csaba.armap.common.helpers.FullScreenHelper
import dev.csaba.armap.common.samplerender.SampleRender
import dev.csaba.armap.treewalk.helpers.ARCoreSessionLifecycleHelper
import dev.csaba.armap.treewalk.helpers.FileDownloader
import dev.csaba.armap.treewalk.helpers.GeoPermissionsHelper
import dev.csaba.armap.treewalk.helpers.TreeWalkGeoView
import io.reactivex.BackpressureStrategy
import io.reactivex.android.schedulers.AndroidSchedulers.*
import io.reactivex.disposables.Disposables
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class TreeWalkGeoActivity : AppCompatActivity() {
  companion object {
    private const val TAG = "TreeWalkGeoActivity"
    private const val LOCATIONS_FILE_NAME = "locations_v2_2.xml"
    private const val LOCATIONS_URL = "https://treewalks.github.io/locations.xml"
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

  private fun createCircularFABMenu() {
    // Set up the white button on the lower right corner
    // more or less with default parameter

    // Set up the white button on the lower right corner
    // more or less with default parameter
    val fabIconNew = ImageView(this)
    fabIconNew.setImageDrawable(ContextCompat.getDrawable(this.baseContext, R.drawable.ic_action_new_light))
    val rightLowerButton: FloatingActionButton = FloatingActionButton.Builder(this)
      .setContentView(fabIconNew)
      .build()

    val rLSubBuilder: SubActionButton.Builder = SubActionButton.Builder(this)
    val rlIcon1 = ImageView(this)
    val rlIcon2 = ImageView(this)
    val rlIcon3 = ImageView(this)
    val rlIcon4 = ImageView(this)

    rlIcon1.setImageDrawable(ContextCompat.getDrawable(this.baseContext, R.drawable.ic_action_chat_light))
    rlIcon2.setImageDrawable(ContextCompat.getDrawable(this.baseContext, R.drawable.ic_action_camera_light))
    rlIcon3.setImageDrawable(ContextCompat.getDrawable(this.baseContext, R.drawable.ic_action_video_light))
    rlIcon4.setImageDrawable(ContextCompat.getDrawable(this.baseContext, R.drawable.ic_action_place_light))

    // Build the menu with default options: light theme, 90 degrees, 72dp radius.
    // Set 4 default SubActionButtons
    val rightLowerMenu: FloatingActionMenu = FloatingActionMenu.Builder(this)
      .addSubActionView(rLSubBuilder.setContentView(rlIcon1).build())
      .addSubActionView(rLSubBuilder.setContentView(rlIcon2).build())
      .addSubActionView(rLSubBuilder.setContentView(rlIcon3).build())
      .addSubActionView(rLSubBuilder.setContentView(rlIcon4).build())
      .attachTo(rightLowerButton)
      .build()

    // Listen menu open and close events to animate the button content view
    rightLowerMenu.setStateChangeListener(object : FloatingActionMenu.MenuStateChangeListener {
      override fun onMenuOpened(menu: FloatingActionMenu?) {
        // Rotate the icon of rightLowerButton 45 degrees clockwise
        fabIconNew.rotation = 0f
        val pvhR = PropertyValuesHolder.ofFloat(View.ROTATION, 45f)
        val animation = ObjectAnimator.ofPropertyValuesHolder(fabIconNew, pvhR)
        animation.start()
      }

      override fun onMenuClosed(menu: FloatingActionMenu?) {
        // Rotate the icon of rightLowerButton 45 degrees counter-clockwise
        fabIconNew.rotation = 45f
        val pvhR = PropertyValuesHolder.ofFloat(View.ROTATION, 0f)
        val animation = ObjectAnimator.ofPropertyValuesHolder(fabIconNew, pvhR)
        animation.start()
      }
    })
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    RxJavaPlugins.setErrorHandler {
      Log.e("Error", it.localizedMessage ?: "")
    }

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

    // Create circular FAB menu
    createCircularFABMenu()

    lifecycle.coroutineScope.launch {
      downloadLocationsAsync()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    disposable.dispose()
  }

  private suspend fun downloadLocationsAsync(): Deferred<Int> = coroutineScope {
    async {
      var cachedFile = File(cacheDir, LOCATIONS_FILE_NAME)

      disposable = fileDownloader.download(LOCATIONS_URL, cachedFile)
        .throttleFirst(100, TimeUnit.MILLISECONDS)
        .toFlowable(BackpressureStrategy.LATEST)
        .subscribeOn(Schedulers.io())
        .observeOn(mainThread())
        .subscribe({
          Log.i(TAG, "$it% Downloaded")
        }, {
          Log.e(TAG, it.localizedMessage, it)
          cachedFile = File(cacheDir, LOCATIONS_FILE_NAME)
          renderer.processLocations(cachedFile)
        }, {
          Log.i(TAG, "Download Complete")
          renderer.processLocations(cachedFile)
        })

      return@async 0
    }
  }

  // Configure the session, setting the desired options according to your usecase.
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
      // Use toast instead of snackbar here since the activity will exit.
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
