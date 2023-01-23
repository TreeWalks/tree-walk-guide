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
package dev.csaba.armap.treewalk.helpers

import android.opengl.GLSurfaceView
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.csaba.armap.treewalk.TreeWalkGeoActivity
import dev.csaba.armap.treewalk.R
import dev.csaba.armap.common.helpers.SnackbarHelper
import dev.csaba.armap.common.helpers.TapHelper

/** Contains UI elements for Tree Walk Geo. */
class TreeWalkGeoView(val activity: TreeWalkGeoActivity) : DefaultLifecycleObserver {
  val root: View = View.inflate(activity, R.layout.activity_main, null)
  val surfaceView: GLSurfaceView = root.findViewById(R.id.surfaceview)

  val snackbarHelper = SnackbarHelper()
  val tapHelper = TapHelper(activity).also { surfaceView.setOnTouchListener(it) }

  override fun onResume(owner: LifecycleOwner) {
    surfaceView.onResume()
  }

  override fun onPause(owner: LifecycleOwner) {
    surfaceView.onPause()
  }
}
