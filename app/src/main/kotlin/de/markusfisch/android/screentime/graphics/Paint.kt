package de.markusfisch.android.screentime.graphics

import android.graphics.Paint

fun fillPaint(col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
	color = col
	style = Paint.Style.FILL
}
