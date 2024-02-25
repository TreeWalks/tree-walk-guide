package dev.csaba.armap.treewalk

import android.content.Intent
import android.location.Location
import android.opengl.Matrix
import android.util.Log
import android.view.MotionEvent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import com.google.android.gms.maps.model.LatLng
import com.google.ar.core.*
import com.google.ar.core.exceptions.CameraNotAvailableException
import dev.csaba.armap.common.helpers.DisplayRotationHelper
import dev.csaba.armap.common.helpers.TrackingStateHelper
import dev.csaba.armap.common.samplerender.Framebuffer
import dev.csaba.armap.common.samplerender.Mesh
import dev.csaba.armap.common.samplerender.SampleRender
import dev.csaba.armap.common.samplerender.Shader
import dev.csaba.armap.common.samplerender.arcore.BackgroundRenderer
import dev.csaba.armap.treewalk.data.*
import dev.csaba.armap.treewalk.helpers.*
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin


class TreeWalkGeoRenderer(val activity: TreeWalkGeoActivity) :
  SampleRender.Renderer, DefaultLifecycleObserver {

  companion object {
    const val TAG = "TreeWalkGeoRenderer"

    private const val Z_NEAR = 0.1f
    private const val Z_FAR = 1000f

    private const val HOVER_ABOVE_TERRAIN = 0.0  // meters
    private const val POST_PROXIMITY_THRESHOLD = 0.003  // kilometers

    private const val CUBE_HIT_AREA_RADIUS = 1.0
    // private const val SEMANTICS_CONFIDENCE_THRESHOLD = 0.5

    private const val TWEAK_GPS_DX = 0.00012  // ~1065 cm in Fresno
    private const val TWEAK_GPS_DY = 0.000106  // ~1169 cm in Fresno
  }

  private lateinit var backgroundRenderer: BackgroundRenderer
  private lateinit var virtualSceneFramebuffer: Framebuffer
  private var hasSetTextureNames = false

  // Virtual objects
  private lateinit var pineMesh: Mesh
  private lateinit var postMesh: Mesh
  private lateinit var redObjectShader: Shader
  private lateinit var greenObjectShader: Shader
  private lateinit var blueObjectShader: Shader

  // Temporary matrix allocated here to reduce number of allocations for each frame.
  private val viewMatrix = FloatArray(16)
  private val projectionMatrix = FloatArray(16)
  private val centerVertexOfCube = floatArrayOf(0f, 0f, 0f, 1f)
  private val vertexResult = FloatArray(4)

  val stops: MutableList<LocationData> = emptyList<LocationData>().toMutableList()
  private val locationRenderModel = RenderModel(LatLng(0.0, 0.0), ObjectKind.TREE)
  private val nwGeoRenderModel = RenderModel(LatLng(0.0, 0.0), ObjectKind.POST)
  private val neGeoRenderModel = RenderModel(LatLng(0.0, 0.0), ObjectKind.POST)
  private val seGeoRenderModel = RenderModel(LatLng(0.0, 0.0), ObjectKind.POST)
  private val swGeoRenderModel = RenderModel(LatLng(0.0, 0.0), ObjectKind.POST)
  private val nextLocRenderModel = RenderModel(LatLng(0.0, 0.0), ObjectKind.TREE)

  // private val arrowModel = LocationModel(LatLng(0.0, 0.0), ObjectKind.ARROW)
  // private val wateringCanModel = LocationModel(LatLng(0.0, 0.0), ObjectKind.WATERING_CAN)
  private var scaffolded = false  // The stops array were scaffolded, but no anchors yet
  private var anchoring = false  // Anchors are created into the scaffold
  var anchored = false  // Anchors were created as well into the scaffold

  private val session
    get() = activity.arCoreSessionHelper.session

  private val displayRotationHelper = DisplayRotationHelper(activity)
  private val trackingStateHelper = TrackingStateHelper(activity)

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
      virtualSceneFramebuffer = Framebuffer(render, 1, 1)

      // Virtual object to render (Geospatial Marker)
      pineMesh = Mesh.createFromAsset(render, "models/pine.obj")
      postMesh = Mesh.createFromAsset(render, "models/post.obj")
      redObjectShader =
        Shader.createFromAssets(
          render,
          "shaders/ar_unlit_object.vert",
          "shaders/ar_unlit_red_object.frag",
          null
        )
      greenObjectShader =
        Shader.createFromAssets(
          render,
          "shaders/ar_unlit_object.vert",
          "shaders/ar_unlit_green_object.frag",
          null
        )
      blueObjectShader =
        Shader.createFromAssets(
          render,
          "shaders/ar_unlit_object.vert",
          "shaders/ar_unlit_blue_object.frag",
          null
        )

      backgroundRenderer.setUseDepthVisualization(render, false)
      backgroundRenderer.setUseOcclusion(render, false)
    } catch (e: IOException) {
      Log.e(TAG, "Failed to read a required asset file", e)
    }
  }

  override fun onSurfaceChanged(render: SampleRender, width: Int, height: Int) {
    displayRotationHelper.onSurfaceChanged(width, height)
    virtualSceneFramebuffer.resize(width, height)
  }

  override fun onDrawFrame(render: SampleRender) {
    val session = session ?: return

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
    // camera frame rate.
    val frame =
      try {
        session.update()
      } catch (e: CameraNotAvailableException) {
        Log.e(TAG, "Camera not available during onDrawFrame", e)
        return
      }

    val camera = frame.camera

    // Handle one tap per frame.
    handleTap(frame, camera)

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

    // Step 1.1.: Obtain Geospatial information and display it on the map.
    val earth = session.earth
    if (earth?.trackingState == TrackingState.TRACKING) {
      if (anchored && activity.targetStopIndex >= 0) {
        val stop = stops[activity.targetStopIndex]
        if (activity.appState == AppState.INVOKE_WATERING) {
          // TODO: how concurrency safe is this? (To flip that latch)
          activity.appState = AppState.WATERING_IN_PROGRESS
          val cameraPose = earth.cameraGeospatialPose
          val withinFence = isGpsInGeoFence(cameraPose.latitude, cameraPose.latitude, stop)
          if (!withinFence) {
            activity.showResourceMessage(R.string.geofence_area)
          }

          if (withinFence || activity.developerMode) {
            val (semanticsImage, cameraImage) = acquireImages(frame)
            activity.semanticsImage = semanticsImage
            activity.cameraImage = cameraImage
            val treeDetectIntent = Intent(TreeWalkGeoActivity.WATERING_BROADCAST_FILTER)
            activity.baseContext.sendBroadcast(treeDetectIntent)
          }
        }

        // Draw the placed anchors, if any exists and visible.
        render.renderObject(locationRenderModel, !stop.visited, false)
        render.renderObject(nwGeoRenderModel, true, false)
        render.renderObject(neGeoRenderModel, true, false)
        render.renderObject(seGeoRenderModel, true, false)
        render.renderObject(swGeoRenderModel, true, false)
        render.renderObject(nextLocRenderModel, false, true)
      } else {
        if (scaffolded && !anchoring) {
          activity.lifecycle.coroutineScope.launch { createAnchors() }
        }
      }
    }

    // Compose the virtual scene with the background.
    backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR)
  }

  private fun processLocalizedData(localizedString: List<String>): LocalizedData {
    return LocalizedData(
      localizedString[0],
      localizedString[4],
      localizedString[1],
      localizedString[2],
      localizedString[3],
      localizedString[5],
      localizedString[6],
    )
  }

  fun processLocations(locations: List<String>, locationsEn: List<String>, locationsEs: List<String>, tweak: Boolean, loc: Location?) {
    if (stops.isNotEmpty()) {
      return
    }

    val takeCount = if (tweak) min(3, locations.size) else locations.size
    var startLat = (loc?.latitude ?: 0.0) + TWEAK_GPS_DY / 10.0
    val startLon = loc?.longitude ?: 0.0
    zipMulti(locations.takeLast(takeCount), locationsEn.takeLast(takeCount), locationsEs.takeLast(takeCount)) {
      val parts = splitAndCleanse(it[0])
      val pinGps = if (tweak) LatLng(startLat, startLon) else LatLng(parts[0].trim().toDouble(), parts[1].trim().toDouble())
      val stopKind = ObjectKind.getByName(parts[6].trim())
      val nwGeoLat = if (tweak) startLat + TWEAK_GPS_DY / 3.0 else parts[2].trim().toDouble()
      val nwGeoLon = if (tweak) startLon - TWEAK_GPS_DX / 3.0 else parts[3].trim().toDouble()
      val nwGeoGps = LatLng(nwGeoLat, nwGeoLon)
      val seGeoLat = if (tweak) startLat - TWEAK_GPS_DY / 3.0 else parts[4].trim().toDouble()
      val seGeoLon = if (tweak) startLon + TWEAK_GPS_DX / 3.0 else parts[5].trim().toDouble()
      val seGeoGps = LatLng(seGeoLat, seGeoLon)
      stops.add(LocationData(
        pinGps,
        nwGeoGps,
        seGeoGps,
        stopKind,
        parts[7].trim(), // height
        parts[8].trim(), // weight
        parts[9].trim(), // scientificName
        parts[10].trim(), // scientificAlternates
        processLocalizedData(splitAndCleanse(it[1])),
        processLocalizedData(splitAndCleanse(it[2])),
        LocationModel(pinGps, stopKind),
        LocationModel(nwGeoGps, ObjectKind.POST),
        LocationModel(LatLng(nwGeoLat, seGeoLon), ObjectKind.POST),
        LocationModel(seGeoGps, ObjectKind.POST),
        LocationModel(LatLng(seGeoLat, nwGeoLon), ObjectKind.POST),
        parts[11].trim(), // phylum
        parts[12].trim() == "true",
        parts[13].trim() == "true",
        parts[14].trim() == "true",
        parts[15].trim() == "true",
        parts[16].trim() == "true",
        parts[17].trim() == "true",
      ))

      if (tweak) {
        startLat += TWEAK_GPS_DY
      }
    }

    scaffolded = true
  }

  private fun createAnchorsCoreCore(renderModel: RenderModel, locationModel: LocationModel, earth: Earth) {
    renderModel.gpsLocation = locationModel.gpsLocation
    renderModel.kind = locationModel.kind
    renderModel.addAnchor(earth, HOVER_ABOVE_TERRAIN)
  }

  private fun createAnchorsCore(earth: Earth) {
    val stop = stops[activity.targetStopIndex]
    createAnchorsCoreCore(locationRenderModel, stop.locationModel, earth)
    createAnchorsCoreCore(nwGeoRenderModel, stop.nwGeoFenceModel, earth)
    createAnchorsCoreCore(neGeoRenderModel, stop.neGeoFenceModel, earth)
    createAnchorsCoreCore(seGeoRenderModel, stop.seGeoFenceModel, earth)
    createAnchorsCoreCore(swGeoRenderModel, stop.swGeoFenceModel, earth)
    val nextStop = stops[activity.nextStopIndex()]
    createAnchorsCoreCore(nextLocRenderModel, nextStop.locationModel, earth)
  }

  fun createAnchors() {
    if (anchoring || anchored) {
      return
    }

    anchoring = true

    // Step 1.2.: place an anchor at the given position.
    val earth = session?.earth ?: return
    if (earth.trackingState != TrackingState.TRACKING) {
      anchoring = false
      return
    }

    createAnchorsCore(earth)

    anchored = true
    anchoring = false
  }

  private fun SampleRender.renderObject(renderModel: RenderModel, rotate: Boolean, bounce: Boolean) {
    if (renderModel.anchor == null) {
      return
    }

    if (renderModel.anchor?.terrainAnchorState != Anchor.TerrainAnchorState.SUCCESS) {
      return
    }

    if (renderModel.anchor?.trackingState != TrackingState.TRACKING) {
      return
    }

    // Calculate model/view/projection matrices
    val currentTimeMillis = System.currentTimeMillis()
    val bounceMatrix = FloatArray(16)
    if (bounce) {
      Matrix.setIdentityM(bounceMatrix, 0)
      // Bounce animation follows a half sine wave
      val angleRadian = currentTimeMillis % 1000 * Math.PI / 1000f
      val deltaY = sin(angleRadian)
      // Y translation position:
      // https://www.brainvoyager.com/bv/doc/UsersGuide/CoordsAndTransforms/SpatialTransformationMatrices.html
      // Combined with OpenGL ES format matrices:
      // 4 x 4 column-vector matrices stored in column-major order
      // (row major 7. (0-based) pos. is 13. (0-based) column major pos.)
      bounceMatrix[13] = deltaY.toFloat()
    }

    val rotationMatrix = FloatArray(16)
    if (rotate) {
      // Spin around once per second
      val angleDegrees = currentTimeMillis % 1000 * 360f / 1000f
      Matrix.setRotateM(rotationMatrix, 0, angleDegrees, 0f, 1f, 0f)
    }

    var transformationMatrix = FloatArray(16)
    if (rotate && bounce) {
      Matrix.multiplyMM(transformationMatrix, 0, bounceMatrix, 0, rotationMatrix, 0)
    } else if (rotate) {
      transformationMatrix = rotationMatrix
    } else if (bounce) {
      transformationMatrix = bounceMatrix
    }

    val transformedModelMatrix = FloatArray(16)
    // Get the current pose of the Anchor in world space. The Anchor pose is updated
    // during calls to session.update() as ARCore refines its estimate of the world.
    renderModel.anchor?.pose?.toMatrix(renderModel.modelMatrix, 0)
    Matrix.multiplyMM(transformedModelMatrix, 0, renderModel.modelMatrix, 0, transformationMatrix, 0)
    Matrix.multiplyMM(renderModel.modelViewMatrix, 0, viewMatrix, 0, transformedModelMatrix, 0)
    Matrix.multiplyMM(renderModel.modelViewProjectionMatrix, 0, projectionMatrix, 0, renderModel.modelViewMatrix, 0)

    // Update shader properties and draw
    val virtualObjectShader = when (ObjectColor.getColor(renderModel.kind)) {
      ObjectColor.RED -> redObjectShader
      ObjectColor.GREEN -> greenObjectShader
      ObjectColor.BLUE -> blueObjectShader
    }

    virtualObjectShader.setMat4("u_ModelViewProjection", renderModel.modelViewProjectionMatrix)
    val virtualObjectMesh = when (ObjectShape.getShape(renderModel.kind)) {
      ObjectShape.PINE -> pineMesh
      ObjectShape.POST -> postMesh
    }

    draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer)
  }

  // Handle only one tap per frame, as taps are usually low frequency compared to frame rate.
  private fun handleTap(frame: Frame, camera: Camera) {
    if (!activity.captureTaps) {
      return
    }

    if (camera.trackingState != TrackingState.TRACKING) {
      return
    }

    val earth = session?.earth ?: return
    if (earth.trackingState != TrackingState.TRACKING) {
      return
    }

    if (activity.appState == AppState.INITIALIZING || !anchored) {
      return
    }

    if (activity.appState == AppState.WATERING_IN_PROGRESS ||
      activity.appState == AppState.INVOKE_WATERING ||
      activity.appState == AppState.WATERING_FINISHED)
    {
      // Silent return
      return
    }

    if (activity.appState != AppState.TARGETING_STOP) {
      return
    }

    if (activity.appState == AppState.TARGETING_STOP) {
      val tap = activity.view.tapHelper.poll() ?: return
      if (activity.targetStopIndex < 0) {
        activity.showResourceMessage(R.string.missing_target)
        return
      }

      val stop = stops[activity.targetStopIndex]
      if (stop.visited) {
        activity.showResourceMessage(R.string.already_visited)
        return
      }

      val cameraPose = earth.cameraGeospatialPose
      stop.locationModel.distanceFrom(cameraPose.latitude, cameraPose.longitude)
      if (stop.locationModel.distance > POST_PROXIMITY_THRESHOLD) {
        activity.showResourceMessage(R.string.move_closer)
        return
      }

      if (isMotionEventApproxHitLocation(locationRenderModel, tap) ||
        stop.locationModel.distance < POST_PROXIMITY_THRESHOLD / 2)
      {
        stop.visited = true
        activity.advanceStop(stop.getLocalizedTitle(activity.currentLanguage))
        anchored = false
        createAnchors()
      }
    }
  }

  private fun isGpsInGeoFence(lat: Double, lon: Double, location: LocationData): Boolean {
    val minLat = min(location.geoFenceSE.latitude, location.geoFenceNW.latitude)
    val maxLat = max(location.geoFenceSE.latitude, location.geoFenceNW.latitude)
    val minLon = min(location.geoFenceSE.longitude, location.geoFenceNW.longitude)
    val maxLon = max(location.geoFenceSE.longitude, location.geoFenceNW.longitude)

    return lat in minLat..maxLat && lon in minLon..maxLon
  }

  // https://stackoverflow.com/questions/46728036/detecting-if-an-tap-event-with-arcore-hits-an-already-added-3d-object
  // https://github.com/hl3hl3/ARCoreMeasure/blob/master/app/src/main/java/com/hl3hl3/arcoremeasure/ArMeasureActivity.java
  private fun isMotionEventApproxHitLocation(renderModel: RenderModel, event: MotionEvent): Boolean {
    Matrix.multiplyMV(vertexResult, 0, renderModel.modelViewProjectionMatrix, 0, centerVertexOfCube, 0)
    /**
     * vertexResult = [x, y, z, w]
     *
     * coordinates in View
     * ┌─────────────────────────────────────────┐╮
     * │[0, 0]                     [viewWidth, 0]│
     * │       [viewWidth/2, viewHeight/2]       │view height
     * │[0, viewHeight]   [viewWidth, viewHeight]│
     * └─────────────────────────────────────────┘╯
     * ╰                view width               ╯
     *
     * coordinates in GLSurfaceView frame
     * ┌─────────────────────────────────────────┐╮
     * │[-1.0,  1.0]                  [1.0,  1.0]│
     * │                 [0, 0]                  │view height
     * │[-1.0, -1.0]                  [1.0, -1.0]│
     * └─────────────────────────────────────────┘╯
     * ╰                view width               ╯
     */
    // circle hit test
    val radius = virtualSceneFramebuffer.width / 2 * (CUBE_HIT_AREA_RADIUS / vertexResult[3])
    val dx = event.x - virtualSceneFramebuffer.width / 2 * (1 + vertexResult[0] / vertexResult[3])
    val dy = event.y - virtualSceneFramebuffer.height / 2 * (1 - vertexResult[1] / vertexResult[3])
    return dx * dx + dy * dy < radius * radius
  }

  private fun acquireImages(frame: Frame): Pair<android.media.Image, android.media.Image> {
    val semanticsImage = frame.acquireSemanticImage()
    val cameraImage = frame.acquireCameraImage()
    return Pair(semanticsImage, cameraImage)
  }
}
