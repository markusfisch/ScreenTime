package de.markusfisch.android.screentime.dialog

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import de.markusfisch.android.screentime.R
import de.markusfisch.android.screentime.app.prefs
import de.markusfisch.android.screentime.os.hasUsageAccess
import de.markusfisch.android.screentime.os.TrackingStatus
import de.markusfisch.android.screentime.service.trackingStatus

fun Activity.showTrackingStatus() {
	val message = when (trackingStatus) {
		TrackingStatus.ANDROID -> R.string.tracking_android
		TrackingStatus.UNSUPPORTED_ANDROID -> R.string.tracking_screen_unsupported
		TrackingStatus.USAGE_ACCESS_DENIED -> R.string.tracking_screen_permission
		TrackingStatus.ANDROID_UNAVAILABLE -> R.string.tracking_screen_unavailable
		null -> R.string.tracking_unavailable
	}
	val builder = AlertDialog.Builder(this)
		.setTitle(R.string.tracking_status)
		.setMessage(message)
		.setPositiveButton(android.R.string.ok, null)
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
		builder.setNeutralButton(R.string.usage_access) { _, _ ->
			openUsageAccessSettings()
		}
	}
	builder.show()
}

fun Activity.requestUsageAccessOnce() {
	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || isFinishing ||
		hasUsageAccess() || prefs.usageAccessPromptShown
	) {
		return
	}
	prefs.usageAccessPromptShown = true
	AlertDialog.Builder(this)
		.setTitle(R.string.tracking_status)
		.setMessage(R.string.usage_access_explanation)
		.setPositiveButton(R.string.allow_usage_access) { _, _ ->
			openUsageAccessSettings()
		}
		.setNegativeButton(R.string.later, null)
		.show()
}

private fun Activity.openUsageAccessSettings() {
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
		try {
			startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
		} catch (_: ActivityNotFoundException) {
			Toast.makeText(this, R.string.usage_access_unavailable, Toast.LENGTH_LONG).show()
		}
	}
}
