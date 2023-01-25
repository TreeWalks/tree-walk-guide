package dev.csaba.armap.treewalk.helpers

import android.graphics.Color

inline fun greyScaleWithFade(color: Int, fadeFactor: Double): Int {
    // Convert by luminosity
    val fadeFactorOffset = 256 * (1 - fadeFactor) / 2
    val grey = ((
        0.299 * Color.red(color) +
        0.587 * Color.green(color) +
        0.114 * Color.blue(color)
    ) * fadeFactor + fadeFactorOffset).toInt()

    return Color.rgb(grey, grey, grey)
}

inline fun greenFade(color: Int, fadeFactor: Int): Int {
    val red = Color.red(color)
    val blue = Color.blue(color)
    val grey = (
        0.299 * red +
        0.587 * Color.green(color) +
        0.114 * blue
    ).toInt()

    return Color.rgb(red / fadeFactor, grey, blue / fadeFactor)
}
