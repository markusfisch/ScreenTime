package de.markusfisch.android.screentime.notification

import de.markusfisch.android.screentime.R

import android.app.Notification
import android.content.Context

fun createScreenTimeNotification(
	context: Context,
	message: String
): Notification {
	val r = context.resources
	return createNotification(
		context,
		R.drawable.notify,
		r.getString(R.string.app_name),
		message,
		getDefaultIntent(context)
	)
}
