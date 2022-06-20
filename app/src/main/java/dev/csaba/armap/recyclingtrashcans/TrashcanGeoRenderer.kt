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
package dev.csaba.armap.recyclingtrashcans

import android.opengl.Matrix
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.Anchor
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import dev.csaba.armap.common.helpers.DisplayRotationHelper
import dev.csaba.armap.common.helpers.TrackingStateHelper
import dev.csaba.armap.common.samplerender.Framebuffer
import dev.csaba.armap.common.samplerender.Mesh
import dev.csaba.armap.common.samplerender.SampleRender
import dev.csaba.armap.common.samplerender.Shader
import dev.csaba.armap.common.samplerender.arcore.BackgroundRenderer
import java.io.IOException
import kotlin.math.*

data class GpsLocation(val lat: Double, val lon: Double, val elevation: Double)

class TrashcanGeoRenderer(val activity: TrashcanGeoActivity) :
  SampleRender.Renderer, DefaultLifecycleObserver {
  //<editor-fold desc="ARCore initialization" defaultstate="collapsed">
  companion object {
    const val TAG = "TrashcanGeoRenderer"

    private const val Z_NEAR = 0.1f
    private const val Z_FAR = 1000f

    private const val MEAN_EARTH_RADIUS = 6371.008
    private const val D2R = Math.PI / 180.0
  }

  lateinit var backgroundRenderer: BackgroundRenderer
  lateinit var virtualSceneFramebuffer: Framebuffer
  var hasSetTextureNames = false

  // Virtual object (ARCore pawn)
  lateinit var virtualObjectMesh: Mesh
  lateinit var virtualObjectShader: Shader

  // Temporary matrix allocated here to reduce number of allocations for each frame.
  val viewMatrix = FloatArray(16)
  val projectionMatrix = FloatArray(16)
  var gpsLocations: MutableList<GpsLocation> = emptyList<GpsLocation>().toMutableList()
  var modelMatrixes: MutableList<FloatArray> = emptyList<FloatArray>().toMutableList()
  var modelViewMatrixes: MutableList<FloatArray> = emptyList<FloatArray>().toMutableList()
  val modelViewProjectionMatrix: MutableList<FloatArray> = emptyList<FloatArray>().toMutableList() // projection x view x model

  val session
    get() = activity.arCoreSessionHelper.session

  val displayRotationHelper = DisplayRotationHelper(activity)
  val trackingStateHelper = TrackingStateHelper(activity)

  override fun onResume(owner: LifecycleOwner) {
    displayRotationHelper.onResume()
    hasSetTextureNames = false
  }

  override fun onPause(owner: LifecycleOwner) {
    displayRotationHelper.onPause()
  }

  override fun onSurfaceCreated(render: SampleRender) {
    // Prepare the rendering objects.
    // This involves reading shaders and 3D model files, so may throw an IOException.
    try {
      backgroundRenderer = BackgroundRenderer(render)
      virtualSceneFramebuffer = Framebuffer(render, /*width=*/ 1, /*height=*/ 1)

      // Virtual object to render (Geospatial Marker)
      virtualObjectMesh = Mesh.createFromAsset(render, "models/map_pin.obj")
      virtualObjectShader =
        Shader.createFromAssets(
          render,
          "shaders/ar_unlit_object.vert",
          "shaders/ar_unlit_object.frag",
          /*defines=*/ null)

      backgroundRenderer.setUseDepthVisualization(render, false)
      backgroundRenderer.setUseOcclusion(render, false)

      val locations: Array<String> = activity.resources.getStringArray(R.array.locations)
      // val mapView = activity.view.mapView
      for (location in locations) {
        val locationParts = location.split(",")
        gpsLocations.add(
          GpsLocation(
            locationParts[0].toDouble(),
            locationParts[1].toDouble(),
            locationParts[2].toDouble()
          )
        )
        modelMatrixes.add(FloatArray(16))
        modelViewMatrixes.add(FloatArray(16))
        modelViewProjectionMatrix.add(FloatArray(16))
      }
    } catch (e: IOException) {
      Log.e(TAG, "Failed to read a required asset file", e)
      showError("Failed to read a required asset file: $e")
    }
  }

  override fun onSurfaceChanged(render: SampleRender, width: Int, height: Int) {
    displayRotationHelper.onSurfaceChanged(width, height)
    virtualSceneFramebuffer.resize(width, height)
  }
  //</editor-fold>

  override fun onDrawFrame(render: SampleRender) {
    val session = session ?: return

    //<editor-fold desc="ARCore frame boilerplate" defaultstate="collapsed">
    // Texture names should only be set once on a GL thread unless they change. This is done during
    // onDrawFrame rather than onSurfaceCreated since the session is not guaranteed to have been
    // initialized during the execution of onSurfaceCreated.
    if (!hasSetTextureNames) {
      session.setCameraTextureNames(intArrayOf(backgroundRenderer.cameraColorTexture.textureId))
      hasSetTextureNames = true
    }

    // -- Update per-frame state

    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    displayRotationHelper.updateSessionIfNeeded(session)

    // Obtain the current frame from ARSession. When the configuration is set to
    // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
    // camera framerate.
    val frame =
      try {
        session.update()
      } catch (e: CameraNotAvailableException) {
        Log.e(TAG, "Camera not available during onDrawFrame", e)
        showError("Camera not available. Try restarting the app.")
        return
      }

    val camera = frame.camera

    // BackgroundRenderer.updateDisplayGeometry must be called every frame to update the coordinates
    // used to draw the background camera image.
    backgroundRenderer.updateDisplayGeometry(frame)

    // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
    trackingStateHelper.updateKeepScreenOnFlag(camera.trackingState)

    // -- Draw background
    if (frame.timestamp != 0L) {
      // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
      // drawing possible leftover data from previous sessions if the texture is reused.
      backgroundRenderer.drawBackground(render)
    }

    // If not tracking, don't draw 3D objects.
    if (camera.trackingState == TrackingState.PAUSED) {
      return
    }

    // Get projection matrix.
    camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR)

    // Get camera matrix and draw.
    camera.getViewMatrix(viewMatrix, 0)

    render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f)
    //</editor-fold>

    // Step 1.1.: Obtain Geospatial information and display it on the map.
    val earth = session.earth
    if (earth?.trackingState == TrackingState.TRACKING) {
      val cameraGeospatialPose = earth.cameraGeospatialPose
      activity.view.mapView?.updateMapPosition(
        latitude = cameraGeospatialPose.latitude,
        longitude = cameraGeospatialPose.longitude,
        heading = cameraGeospatialPose.heading
      )
      activity.view.updateStatusText(earth, cameraGeospatialPose)
    }

    // Draw the placed anchors, if they exist.
    for ((index, earthAnchor) in earthAnchors.withIndex()) {
      render.renderObjectAtAnchor(earthAnchor, index)
    }

    // Compose the virtual scene with the background.
    backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR)
  }

  var earthAnchors: MutableList<Anchor> = emptyList<Anchor>().toMutableList()

  fun onMapClick() {
    // Step 1.2.: place an anchor at the given position.
    val earth = session?.earth ?: return
    if (earth.trackingState != TrackingState.TRACKING) {
      return
    }

    for (earthAnchor in earthAnchors) {
      earthAnchor.detach()
    }

    // The rotation quaternion of the anchor in EUS coordinates.
    val qx = 0f
    val qy = 0f
    val qz = 0f
    val qw = 1f

    val shouldAddAnchor = earthAnchors.isEmpty()
    if (shouldAddAnchor) {
      val cameraPose = earth.cameraGeospatialPose
      val closestLocation = gpsLocations.minWithOrNull(Comparator.comparingDouble {
        haversineInKm(it.lat, it.lon, cameraPose.latitude, cameraPose.longitude)
      })
      if (closestLocation != null) {
        val closestDistance = haversineInKm(closestLocation.lat, closestLocation.lon, cameraPose.latitude, cameraPose.longitude)
        if (closestDistance > 0.3) {
          showMessage("Please tap again when you got closer to the campus's NorthEast part")
          return
        }
      }
    }

    val mapView = activity.view.mapView
    val shouldAddMarker = mapView != null && mapView.earthMarkers.isEmpty()
    for (gpsLocation in gpsLocations) {
      if (shouldAddAnchor) {
        earthAnchors.add(earth.createAnchor(
          gpsLocation.lat, gpsLocation.lon, gpsLocation.elevation, qx, qy, qz, qw))
      }

      if (shouldAddMarker) {
        mapView?.earthMarkers?.add(mapView.createMarker(
          mapView.EARTH_MARKER_COLOR,
          gpsLocation.lat,
          gpsLocation.lon,
          true,
          R.drawable.ic_marker_white_48dp,
        ))
      }
    }
  }

  private fun SampleRender.renderObjectAtAnchor(anchor: Anchor, index: Int) {
    // Get the current pose of the Anchor in world space. The Anchor pose is updated
    // during calls to session.update() as ARCore refines its estimate of the world.
    anchor.pose.toMatrix(modelMatrixes[index], 0)

    // Calculate model/view/projection matrices
    Matrix.multiplyMM(modelViewMatrixes[index], 0, viewMatrix, 0, modelMatrixes[index], 0)
    Matrix.multiplyMM(modelViewProjectionMatrix[index], 0, projectionMatrix, 0, modelViewMatrixes[index], 0)

    // Update shader properties and draw
    virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix[index])
    draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer)
  }

  private fun showError(errorMessage: String) =
    activity.view.snackbarHelper.showError(activity, errorMessage)

  private fun showMessage(message: String) =
    activity.view.snackbarHelper.showMessage(activity, message)

  private fun haversineInKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    // https://stackoverflow.com/a/61990886/292502
    // Haversine to decide which locations sets to populate
    val lonDiff = (lon2 - lon1) * D2R
    val latDiff = (lat2 - lat1) * D2R
    val latSin = sin(latDiff / 2.0)
    val lonSin = sin(lonDiff / 2.0)
    val a = latSin * latSin + (cos(lat1 * D2R) * cos(lat2 * D2R) * lonSin * lonSin)
    val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    return MEAN_EARTH_RADIUS * c
  }
}
