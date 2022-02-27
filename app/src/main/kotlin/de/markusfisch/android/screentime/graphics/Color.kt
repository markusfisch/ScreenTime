package de.markusfisch.android.screentime.graphics

import android.content.Context
import android.os.Build

fun Context.loadColor(resId: Int): Int = if (
	Build.VERSION.SDK_INT < Build.VERSION_CODES.M
) {
    @Suppress("DEPRECATION")
    resources.getColor(resId)
} else {
	resources.getColor(resId, theme)
}