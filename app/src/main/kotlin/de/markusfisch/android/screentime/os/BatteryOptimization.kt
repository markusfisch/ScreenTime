package de.markusfisch.android.screentime.os

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

fun Context.isIgnoringBatteryOptimizations(): Boolean {
	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
		return true
	}
	val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
	return pm.isIgnoringBatteryOptimizations(packageName)
}

// This app does not consume any significant energy and should, by its very
// nature, always be running to fulfil its purpose.
@SuppressLint("BatteryLife")
@TargetApi(Build.VERSION_CODES.M)
fun Context.requestDisableBatteryOptimization() {
	startActivity(Intent().apply {
		action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
		data = Uri.parse("package:$packageName")
	})
}