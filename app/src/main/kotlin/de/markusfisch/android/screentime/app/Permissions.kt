package de.markusfisch.android.screentime.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build

private var permissionGrantedCallback: (() -> Any)? = null
fun runPermissionCallback() {
	permissionGrantedCallback?.invoke()
	permissionGrantedCallback = null
}

const val PERMISSION_POST_NOTIFICATIONS = 1
fun Activity.requestNotificationPermission(): Boolean = requestPermission(
	Manifest.permission.POST_NOTIFICATIONS,
	PERMISSION_POST_NOTIFICATIONS
)

const val PERMISSION_WRITE = 2
fun Activity.requestWritePermission(callback: () -> Any): Boolean {
	permissionGrantedCallback = callback
	return requestPermission(
		Manifest.permission.WRITE_EXTERNAL_STORAGE,
		PERMISSION_WRITE
	)
}

private fun Activity.requestPermission(
	permission: String,
	requestCode: Int
) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
	checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
) {
	requestPermissions(arrayOf(permission), requestCode)
	false
} else {
	true
}
