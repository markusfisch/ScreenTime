package de.markusfisch.android.screentime.notification

import de.markusfisch.android.screentime.R

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

const val CHANNEL_RECORDING = "screen_time_recording"

fun buildNotification(
	context: Context,
	icon: Int,
	title: String,
	intent: Intent
): Notification {
	return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
		@Suppress("DEPRECATION")
		Notification.Builder(context).setPriority(Notification.PRIORITY_MIN)
	} else {
		createChannel(context)
		Notification.Builder(context, CHANNEL_RECORDING)
	}.setOngoing(true)
		.setOnlyAlertOnce(true)
		.setSmallIcon(icon)
		.setContentTitle(title)
		.setContentIntent(
			PendingIntent.getActivity(context, 0, intent, 0)
		)
		.build()
}

@TargetApi(Build.VERSION_CODES.O)
fun createChannel(context: Context) {
	val nm = context.getSystemService(
		Context.NOTIFICATION_SERVICE
	) as NotificationManager
	if (nm.getNotificationChannel(CHANNEL_RECORDING) == null) {
		val channel = NotificationChannel(
			CHANNEL_RECORDING,
			context.getString(R.string.app_name),
			NotificationManager.IMPORTANCE_LOW
		)
		channel.description = context.getString(R.string.recording)
		channel.lockscreenVisibility = Notification.VISIBILITY_SECRET
		channel.setSound(null, null)
		channel.setShowBadge(false)
		channel.enableLights(false)
		channel.enableVibration(false)
		nm.createNotificationChannel(channel)
	}
}
