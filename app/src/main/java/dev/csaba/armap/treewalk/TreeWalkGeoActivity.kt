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

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.media.Image
import android.media.MediaPlayer
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.get
import androidx.core.graphics.set
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.coroutineScope
import androidx.preference.PreferenceManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.games.AchievementsClient
import com.google.android.gms.games.LeaderboardsClient
import com.google.android.gms.games.PlayGames
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.ar.core.Config
import com.google.ar.core.SemanticsLabel
import com.google.ar.core.Session
import com.google.ar.core.exceptions.*
import com.oguzdev.circularfloatingactionmenu.library.FloatingActionButton
import com.oguzdev.circularfloatingactionmenu.library.FloatingActionMenu
import com.oguzdev.circularfloatingactionmenu.library.SubActionButton
import dev.csaba.armap.common.helpers.FullScreenHelper
import dev.csaba.armap.common.samplerender.SampleRender
import dev.csaba.armap.treewalk.data.AppState
import dev.csaba.armap.treewalk.helpers.*
import io.reactivex.disposables.Disposables
import io.reactivex.plugins.RxJavaPlugins
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileReader
import java.util.*
import kotlin.math.floor
import kotlin.math.min


class TreeWalkGeoActivity : AppCompatActivity() {
  companion object {
    private const val TAG = "TreeWalkGeoActivity"
    private const val LOCATIONS_FILE_NAME = "locations.xml"
    private const val LOCATIONS_EN_FILE_NAME = "locations_es.xml"
    private const val LOCATIONS_ES_FILE_NAME = "locations_en.xml"
    const val WEBSITE_URL = "https://treewalks.github.io/"
    private const val DEFAULT_LANGUAGE = "en"
    const val WATERING_BROADCAST_FILTER = "Watering_broadcast_receiver_intent_filter"
    const val WATERING_BONUS = 50
    const val RC_APPLY_SETTINGS = 9001
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
  private var currentLocale = Locale.US
    set(value) {
      field = value
      textToSpeech?.language = value
    }
  var currentLanguage = DEFAULT_LANGUAGE
    set(value) {
      field = value
      currentLocale = Locale.forLanguageTag(value)
    }
  private var hasSemanticApi = false
  private var textToSpeech: TextToSpeech? = null
  private var googleSignInClient: GoogleSignInClient? = null
  private var achievementClient: AchievementsClient? = null
  private var leaderboardsClient: LeaderboardsClient? = null
  private var score = 0L
  var targetStopIndex = -1
  var appState: AppState = AppState.INITIALIZING
  private var speakEnabled = false
  private var mediaPlayer: MediaPlayer? = null
  private var fabMenuIcon: ImageView? = null
  private var localTest = false
  private lateinit var fusedLocationClient: FusedLocationProviderClient
  private var lastLocation: Location? = null
  var semanticsImage: Image? = null
  var cameraImage: Image? = null
  var developerMode = false

  private var broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      showWateringDialog()
    }
  }

  private fun targetStopNumber(): Int {
    return if (targetStopIndex >= 0) targetStopIndex + 1 else targetStopIndex
  }

  fun nextStopIndex(): Int {
    if (renderer.stops.isEmpty()) {
      return -1
    }

    if (targetStopIndex < 0) {
      return -1
    }

    return (targetStopIndex + 1) % renderer.stops.size
  }

  private fun nextStopNumber(): Int {
    if (renderer.stops.isEmpty()) {
      return -1
    }

    return nextStopIndex() + 1
  }

  private fun createCircularFABMenu() {
    // Set up the white button on the lower right corner
    // more or less with default parameter
    val fabMenuIcon = ImageView(this)
    fabMenuIcon.setImageDrawable(ContextCompat.getDrawable(baseContext, R.drawable.baseline_add_24))
    val rightLowerButton: FloatingActionButton = FloatingActionButton.Builder(this)
      .setContentView(fabMenuIcon)
      .build()

    val fabSubBuilder: SubActionButton.Builder = SubActionButton.Builder(this)
    val wateringIcon = ImageView(this)
    val translateIcon = ImageView(this)
    val informationIcon = ImageView(this)
    val settingsIcon = ImageView(this)
    val gameIcon = ImageView(this)

    wateringIcon.setImageDrawable(ContextCompat.getDrawable(baseContext, R.drawable.baseline_shower_24))
    translateIcon.setImageDrawable(ContextCompat.getDrawable(baseContext, R.drawable.baseline_translate_24))
    informationIcon.setImageDrawable(ContextCompat.getDrawable(baseContext, R.drawable.baseline_info_outline_24))
    settingsIcon.setImageDrawable(ContextCompat.getDrawable(baseContext, R.drawable.baseline_settings_24))
    gameIcon.setImageDrawable(ContextCompat.getDrawable(baseContext, R.drawable.baseline_leaderboard_24))

    // Build the menu with default options: light theme, 90 degrees, 72dp radius.
    // Set default SubActionButtons
    val circularMenu: FloatingActionMenu = FloatingActionMenu.Builder(this)
      .setRadius(500)
      .addSubActionView(fabSubBuilder.setContentView(wateringIcon).build(), 196, 196)
      .addSubActionView(fabSubBuilder.setContentView(translateIcon).build(), 196, 196)
      .addSubActionView(fabSubBuilder.setContentView(informationIcon).build(), 196, 196)
      .addSubActionView(fabSubBuilder.setContentView(settingsIcon).build(), 196, 196)
      .addSubActionView(fabSubBuilder.setContentView(gameIcon).build(), 196, 196)
      .attachTo(rightLowerButton).build()

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

    settingsIcon.setOnClickListener {
      // Intent to open settings activity.
      val settingsIntent = Intent(this, SettingsActivity::class.java)
      startActivityForResult(settingsIntent, RC_APPLY_SETTINGS)
    }

    wateringIcon.setOnClickListener {
      if (targetStopIndex < 0) {
        showResourceMessage(R.string.missing_target)
        appState = AppState.LOOKING_FOR_CLOSEST_STOP
      }

      if (appState != AppState.WATERING_IN_PROGRESS &&
        appState != AppState.INVOKE_WATERING &&
        appState != AppState.WATERING_FINISHED)
      {
        when (appState) {
          AppState.TARGETING_STOP -> {
            appState = AppState.INVOKE_WATERING
          }

          else -> showResourceMessage(R.string.wrong_mode)
        }
      }
    }

    gameIcon.setOnClickListener {
      val gameDialog = AlertDialog.Builder(this).create()
      val dialogView: View =
        LayoutInflater.from(this).inflate(R.layout.game_dialog, null)
      gameDialog.setView(dialogView)
      val positiveButtonClick = { _: DialogInterface, _: Int ->
        gameDialog.dismiss()
      }
      gameDialog.setButton(
        DialogInterface.BUTTON_POSITIVE,
        "OK",
        OnClickListener(function = positiveButtonClick)
      )

      val scoreText: TextView = dialogView.findViewById(R.id.scoreNumberText) as TextView
      scoreText.text = score.toString()

      val leaderboardButton: Button = dialogView.findViewById(R.id.leaderboardButton) as Button
      leaderboardButton.setOnClickListener { showTopPlayers() }

      val achievementsButton: Button = dialogView.findViewById(R.id.achievementsButton) as Button
      achievementsButton.setOnClickListener { showAchievements() }

      gameDialog.show()
    }

    informationIcon.setOnClickListener {
      val currentStop = readCurrentStopFromPreferences()
      if (currentStop != targetStopIndex) {
        targetStopIndex = currentStop
        renderer.anchored = false
        renderer.createAnchors()
      }

      val infoDialog = AlertDialog.Builder(this).create()
      val dialogView: View =
        LayoutInflater.from(this).inflate(R.layout.info_dialog, null)
      infoDialog.setView(dialogView)

      val stop = renderer.stops[targetStopIndex]

      val positiveButtonClick = { _: DialogInterface, _: Int ->
        infoDialog.dismiss()
      }
      infoDialog.setButton(
        DialogInterface.BUTTON_POSITIVE,
        resources.getString(R.string.ok),
        OnClickListener(function = positiveButtonClick)
      )

      val neutralButtonClick = { _: DialogInterface, _: Int ->
        stop.visited = true
        advanceStop(stop.getLocalizedTitle(currentLanguage))
        renderer.anchored = false
        renderer.createAnchors()
        infoDialog.dismiss()
      }
      val neutralButtonIcon = ContextCompat.getDrawable(baseContext, R.drawable.baseline_done_24)
      neutralButtonIcon?.setBounds(
        0, 0,
        neutralButtonIcon.bounds.width() / 2,
        neutralButtonIcon.bounds.width() / 2,
      )
      infoDialog.setButton(
        DialogInterface.BUTTON_NEUTRAL,
        resources.getString(R.string.mark),
        neutralButtonIcon,
        OnClickListener(function = neutralButtonClick)
      )

      val negativeButtonIcon = ContextCompat.getDrawable(baseContext, R.drawable.baseline_open_in_new_light_24)
      negativeButtonIcon?.setBounds(
        0, 0,
        negativeButtonIcon.bounds.width() / 2,
        negativeButtonIcon.bounds.width() / 2,
      )
      val negativeButtonClick = { _: DialogInterface, _: Int ->
        openBrowserWindow(stop.getLocalizedUrl(currentLanguage), baseContext)
      }
      infoDialog.setButton(
        DialogInterface.BUTTON_NEGATIVE,
        resources.getString(R.string.page),
        negativeButtonIcon,
        OnClickListener(function = negativeButtonClick)
      )

      val nameTitle: TextView = dialogView.findViewById(R.id.nameTitle) as TextView
      nameTitle.text = "${targetStopNumber()}. ${stop.getLocalizedTitle(currentLanguage)}"

      val latinNameText: TextView = dialogView.findViewById(R.id.latinNameText) as TextView
      if (stop.scientificName.isNotEmpty()) {
        latinNameText.text = stop.scientificName
      } else {
        latinNameText.visibility = View.GONE
      }

      val heightText: TextView = dialogView.findViewById(R.id.heightText) as TextView
      if (stop.height.isNotEmpty()) {
        heightText.text = "${stop.height} '"
      } else {
        val heightTitle: TextView = dialogView.findViewById(R.id.heightTitle) as TextView
        heightTitle.visibility = View.GONE
        heightText.visibility = View.GONE
      }

      val widthText: TextView = dialogView.findViewById(R.id.widthText) as TextView
      if (stop.width.isNotEmpty()) {
        widthText.text = "${stop.width} '"
      } else {
        val widthTitle: TextView = dialogView.findViewById(R.id.widthTitle) as TextView
        widthTitle.visibility = View.GONE
        widthText.visibility = View.GONE
      }

      val descriptionText: TextView = dialogView.findViewById(R.id.descriptionText) as TextView
      descriptionText.text = stop.getLocalizedDescription(currentLanguage)

      infoDialog.show()
    }

    // Listen menu open and close events to animate the button content view
    circularMenu.setStateChangeListener(object : FloatingActionMenu.MenuStateChangeListener {
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

  private fun initGoogleClientAndSignin() {
    try {
      googleSignInClient = GoogleSignIn.getClient(this,
        GoogleSignInOptions.Builder(
          GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN).build())

      googleSignInClient?.silentSignIn()?.addOnCompleteListener {
        if (it.isSuccessful) {
          achievementClient = PlayGames.getAchievementsClient(this)
          leaderboardsClient = PlayGames.getLeaderboardsClient(this)
        } else {
          Log.e(TAG, "signInError", it.exception)
        }
      }?.addOnFailureListener {
        Log.e(TAG, "signInFailure", it)
      }
    }
    catch (e: Exception) {
      Log.e(TAG, "Google SignIn pacman handler", e)
    }
  }

  private fun perAppLanguageInit() {
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

    perAppLanguageInit()

    textToSpeech = TextToSpeech(applicationContext) { status ->
      if (status != TextToSpeech.ERROR) {
        textToSpeech?.language = currentLocale
      }
    }

    // Create circular FAB menu
    createCircularFABMenu()

    initGoogleClientAndSignin()

    fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

    developerMode = Settings.Secure.getInt(
      contentResolver,
      Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
    ) > 0

    val sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    localTest = sharedPref.getBoolean("local_test", false)
    speakEnabled = sharedPref.getBoolean("speech_helper", false)
    if (localTest) {
      if (ActivityCompat.checkSelfPermission(
          this,
          Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
          this,
          Manifest.permission.ACCESS_COARSE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
      ) {
        // According to documentation: Consider calling
        //    ActivityCompat#requestPermissions
        // here to request the missing permissions, and then overriding
        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
        //                                          int[] grantResults)
        // to handle the case where the user grants the permission. See the documentation
        // for ActivityCompat#requestPermissions for more details.
        // HOWEVER!
        // Since this is an AR application these privileges should be already granted!!!
      } else {
        fusedLocationClient.lastLocation
          .addOnSuccessListener { location: Location? ->
            lastLocation = location
            // Got last known location. In some rare situations this can be null.
            lifecycle.coroutineScope.launch(Dispatchers.IO) { downloadAllDataAsync() }
          }
      }
    } else {
      lifecycle.coroutineScope.launch(Dispatchers.IO) { downloadAllDataAsync() }
    }
  }

  override fun onResume() {
    super.onResume()
    registerReceiver(broadcastReceiver, IntentFilter(WATERING_BROADCAST_FILTER))
  }

  override fun onPause() {
    super.onPause()
    textToSpeech?.stop()
  }

  override fun onDestroy() {
    super.onDestroy()
    unregisterReceiver(broadcastReceiver)
    textToSpeech?.shutdown()
    disposable.dispose()
  }

  @Deprecated("Deprecated in Java")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    if (requestCode == RC_APPLY_SETTINGS) {
      val currentStop = readCurrentStopFromPreferences()
      if (currentStop != targetStopIndex) {
        targetStopIndex = currentStop
        renderer.anchored = false
        renderer.createAnchors()
      }
    }
  }

  private fun readCurrentStopFromPreferences(): Int {
    val sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    val unsanitizedTargetStop = sharedPref.getInt("current_stop", 1)
    val sanitizedTargetStop =
      min(unsanitizedTargetStop, renderer.stops.size - 1).coerceAtLeast(1)
    return sanitizedTargetStop - 1
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun downloadAllDataAsync() = coroutineScope {
    async {
      val deferredLocation: Deferred<List<String>> = lifecycle.coroutineScope.async(Dispatchers.IO) {
        downloadData(LOCATIONS_FILE_NAME, R.array.locations)
      }
      val deferredLocationEn: Deferred<List<String>> = lifecycle.coroutineScope.async(Dispatchers.IO) {
        downloadData(LOCATIONS_EN_FILE_NAME, R.array.locations_en)
      }
      val deferredLocationEs: Deferred<List<String>> = lifecycle.coroutineScope.async(Dispatchers.IO) {
        downloadData(LOCATIONS_ES_FILE_NAME, R.array.locations_es)
      }

      val deferredList = listOf(deferredLocation, deferredLocationEn, deferredLocationEs)
      deferredList.awaitAll().apply {
        val tweak = (localTest && lastLocation != null)
        renderer.processLocations(
          deferredLocation.getCompleted(),
          deferredLocationEn.getCompleted(),
          deferredLocationEs.getCompleted(),
          tweak,
          lastLocation,
        )

        if (appState == AppState.INITIALIZING) {
          targetStopIndex = readCurrentStopFromPreferences()
          appState = AppState.TARGETING_STOP
        }
      }

      return@async
    }
  }

  private fun processLocationFile(fileContent: String): List<String> {
    val walkStops: MutableList<String> = emptyList<String>().toMutableList()
    val walkParts = fileContent.split("<string-array name=\"")
    if (walkParts.size <= 1) {
      return walkStops
    }

    val walkPart = walkParts[1]
    if (walkPart.indexOf('"') < 0) {
      return walkStops
    }

    val itemParts = walkPart.split("<item>")
    if (itemParts.size <= 1) {
      return walkStops
    }

    for (itemPart in itemParts.subList(1, itemParts.size)) {
      val itemParts2 = itemPart.split("</item>")
      if (itemParts2.isNotEmpty() && itemParts2.indexOf(",") >= 0) {
        walkStops.add(itemParts2[0])
      }
    }

    return walkStops
  }

  private fun downloadData(fileName: String, arrayId: Int): List<String> {
    val cachedFile = File(cacheDir, fileName)
    var length = 0L
    try {
      length = fileDownloader.download(WEBSITE_URL + fileName, cachedFile)
    }
    catch (e: Exception) {
      Log.e(TAG, "file download error with $fileName", e)
    }

    val stringList: List<String>
    if (!cachedFile.exists() || length <= 0) {
      Log.w(TAG, "Unsuccessful file caching of ${cachedFile.path}")
      stringList = resources.getStringArray(arrayId).toList()
    } else {
      val reader = FileReader(cachedFile)
      stringList = processLocationFile(reader.readText())
      reader.close()
    }

    return stringList
  }

  // Configure the session, setting the desired options according to your use case.
  private fun configureSession(session: Session) {
    session.configure(
      if (hasSemanticApi)
        session.config.apply {
          // Enable Geospatial Mode.
          geospatialMode = Config.GeospatialMode.ENABLED
          // This finding mode is probably the default
          // https://developers.google.com/ar/develop/java/geospatial/terrain-anchors
          planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
          // https://developers.google.com/ar/geospatial-api-challenge/semantics-api/java
          semanticsMode = Config.SemanticsMode.ENABLED
        }
      else
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

  private fun showTopPlayers() {
    leaderboardsClient?.allLeaderboardsIntent?.addOnSuccessListener {intent ->
      startActivityForResult(intent, 0)
    }
  }

  private fun showAchievements() {
    achievementClient?.achievementsIntent?.addOnSuccessListener { intent ->
      startActivityForResult(intent, 0)
    }
  }

  private fun unlockAchievement(stopIndex: Int) {
    val achievementId = when (stopIndex) {
      0 -> R.string.achievement_stop_1_visited
      1 -> R.string.achievement_stop_2_visited
      2 -> R.string.achievement_stop_3_visited
      3 -> R.string.achievement_stop_4_visited
      4 -> R.string.achievement_stop_5_visited
      5 -> R.string.achievement_stop_6_visited
      6 -> R.string.achievement_stop_7_visited
      7 -> R.string.achievement_stop_8_visited
      8 -> R.string.achievement_stop_9_visited
      9 -> R.string.achievement_stop_10_visited
      10 -> R.string.achievement_stop_11_visited
      11 -> R.string.achievement_stop_12_visited
      12 -> R.string.achievement_stop_13_visited
      13 -> R.string.achievement_stop_14_visited
      14 -> R.string.achievement_stop_15_visited
      15 -> R.string.achievement_stop_16_visited
      16 -> R.string.achievement_stop_17_visited
      17 -> R.string.achievement_stop_18_visited
      18 -> R.string.achievement_stop_19_visited
      19 -> R.string.achievement_stop_20_visited
      20 -> R.string.achievement_stop_21_visited
      21 -> R.string.achievement_stop_22_visited
      22 -> R.string.achievement_stop_23_visited
      23 -> R.string.achievement_stop_24_visited
      else -> R.string.achievement_stop_1_visited
    }
    achievementClient?.unlock(getString(achievementId))
    score += 1000
    submitScore()
  }

  private fun wateringBonus() {
    score += WATERING_BONUS
    val xpString = resources.getString(R.string.experience_points)
    val xpNumberString = "$WATERING_BONUS "
    showMessage(xpNumberString + xpString)
    submitScore()
  }

  private fun submitScore() {
    leaderboardsClient?.submitScore(getString(R.string.leaderboard_tree_walk), score)
  }

  private fun speak(text: String) {
    // TODO: should be suspend function?
    textToSpeech?.speak(text,TextToSpeech.QUEUE_FLUSH, null, null)
  }

  private fun showMessage(message: String, allowSpeak: Boolean = true) {
    view.snackbarHelper.showMessage(this, message)
    if (speakEnabled && allowSpeak) {
      speak(message)
    }
  }

  fun showResourceMessage(messageId: Int, allowSpeak: Boolean = true) {
    showMessage(resources.getString(messageId), allowSpeak)
  }

  private suspend fun processSemanticImage(
    cameraBitmap: Bitmap,
    blendedBitmap: Bitmap,
    imageView: ImageView,
    progressBar: ProgressBar,
  ) = coroutineScope {
    if (cameraImage != null && semanticsImage != null) {
      val camImg = cameraImage!!
      val semImg = semanticsImage!!
      assert(camImg.width >= semImg.width)
      assert(camImg.height >= semImg.height)
      assert(camImg.width / camImg.height <= semImg.width / semImg.height)
      // Example:
      // AR framebuffer 1080 x 2287 (~16:7.5 ??? WTF)
      // AR semantics 256 x 144 (16:9)
      // AR camera 640 x 480 (4:3)
      // semanticsScale = 480 / 144 = 3
      // Transformed = 480 x 640
      val heightScale: Double = camImg.height.toDouble() / semImg.height
      val widthScale: Double = camImg.width.toDouble() / semImg.width
      val semanticsPlane = semImg.planes[0]
      val percentStride = blendedBitmap.width * blendedBitmap.height / 100
      var pixelIndex = 0
      var progress = 0
      for (x in 0 until blendedBitmap.width step 1) {
        for (y in 0 until blendedBitmap.height step 1) {
          val mirroredX = blendedBitmap.width - x - 1
          val byteIndex =
            floor(y / widthScale) * semanticsPlane.pixelStride +
            floor(mirroredX / heightScale) * semanticsPlane.rowStride
          if (semanticsPlane.buffer.get(byteIndex.toInt()).toInt() != SemanticsLabel.TREE.ordinal) {
            blendedBitmap[x, y] = greyScaleWithFade(cameraBitmap[y, mirroredX], 0.3)
          } else {
            blendedBitmap[x, y] = greenFade(cameraBitmap[y, mirroredX], 2)
          }

          pixelIndex += 1
          if (pixelIndex % percentStride == 0) {
            progress += 1
            this@TreeWalkGeoActivity.runOnUiThread {
              imageView.invalidate()
            }
          }
        }
      }

      appState = AppState.WATERING_FINISHED
      this@TreeWalkGeoActivity.runOnUiThread {
        progressBar.visibility = View.GONE
        wateringBonus()
        stopSound()
      }
    }
  }

  fun showWateringDialog() {
    if (cameraImage == null || semanticsImage == null) {
      appState = AppState.TARGETING_STOP
      return
    }

    val dialogBuilder = AlertDialog
      .Builder(this)
      .setTitle(R.string.watering_trees)
      .setCancelable(false)

    val wateringDialog = dialogBuilder.create()

    val positiveButtonClick = { dialog: DialogInterface, _: Int ->
      if (appState == AppState.WATERING_FINISHED) {
        fabMenuIcon?.setImageDrawable(
          ContextCompat.getDrawable(baseContext, R.drawable.baseline_add_24)
        )
        appState = AppState.TARGETING_STOP
        dialog.dismiss()
      } else {
        showResourceMessage(R.string.watering_in_progress)
      }
    }
    wateringDialog.setButton(
      DialogInterface.BUTTON_POSITIVE,
      resources.getString(R.string.close),
      OnClickListener(function = positiveButtonClick)
    )

    val dialogView: View =
      LayoutInflater.from(this).inflate(R.layout.watering_dialog, null)
    wateringDialog.setView(dialogView)

    val semImg = semanticsImage!!
    semanticsImage = semImg
    val camImg = cameraImage!!
    cameraImage = camImg
    val cameraBitmap = Bitmap.createBitmap(camImg.width, camImg.height, Bitmap.Config.ARGB_8888)
    YuvToRgbConverter(baseContext).yuvToRgb(camImg, cameraBitmap)

    val imageView = dialogView.findViewById(R.id.blended_image_view) as ImageView
    val blendedBitmap = Bitmap.createBitmap(camImg.height, camImg.width, cameraBitmap.config)
    imageView.setImageBitmap(blendedBitmap)

    val progressBar = dialogView.findViewById(R.id.progressbar) as ProgressBar

    fabMenuIcon?.setImageDrawable(
      ContextCompat.getDrawable(baseContext, R.drawable.baseline_shower_24)
    )
    playSound(baseContext, R.raw.watering)

    wateringDialog.show()

    lifecycle.coroutineScope.launch(Dispatchers.IO) {
      processSemanticImage(cameraBitmap, blendedBitmap, imageView, progressBar)
    }
  }

  private fun stopSound() {
    mediaPlayer?.release()
    mediaPlayer = null
  }

  private fun playSound(ctx: Context?, resourceId: Int) {
    if (resourceId == 0) {
      return
    }

    stopSound()
    mediaPlayer = MediaPlayer.create(ctx, resourceId)
    mediaPlayer?.start()
    mediaPlayer?.isLooping = true
  }

  fun advanceStop(currentTitle: String) {
    unlockAchievement(targetStopIndex)
    val nextStopString = resources.getString(R.string.visited)
    val stopNumberString = " ${targetStopNumber()}. "
    showMessage(nextStopString + stopNumberString + currentTitle)
    targetStopIndex = nextStopIndex()
    val sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    with (sharedPref.edit()) {
      putInt("current_stop", nextStopNumber())
      apply()
    }
  }
}
