package de.markusfisch.android.screentime.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build

const val PERMISSION_POST_NOTIFICATIONS = 1
fun Activity.requestNotificationPermission() {
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
		checkSelfPermission(
			Manifest.permission.POST_NOTIFICATIONS
		) != PackageManager.PERMISSION_GRANTED
	) {
		requestPermissions(
			arrayOf(Manifest.permission.POST_NOTIFICATIONS),
			PERMISSION_POST_NOTIFICATIONS
		)
	}
}
