package de.markusfisch.android.screentime.notification

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import de.markusfisch.android.screentime.R

private const val CHANNEL_RECORDING = "screen_time_recording"

fun Context.buildNotification(
	icon: Int,
	title: String,
	intent: Intent
): Notification {
	val pendingIntent = PendingIntent.getActivity(
		this,
		0,
		intent,
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			PendingIntent.FLAG_IMMUTABLE
		} else 0
	)
	val builder = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
		@Suppress("DEPRECATION")
		val b = Notification.Builder(this)
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
			@Suppress("DEPRECATION")
			b.setPriority(Notification.PRIORITY_MIN)
		}
		b
	} else {
		createChannel()
		Notification.Builder(this, CHANNEL_RECORDING)
	}.setOngoing(true)
		.setOnlyAlertOnce(true)
		.setSmallIcon(icon)
		.setContentTitle(title)
		.setContentIntent(pendingIntent)
	return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
		@Suppress("DEPRECATION")
		builder.notification
	} else {
		builder.build()
	}
}

@TargetApi(Build.VERSION_CODES.O)
fun Context.createChannel() {
	val nm = getSystemService(
		Context.NOTIFICATION_SERVICE
	) as NotificationManager
	if (nm.getNotificationChannel(CHANNEL_RECORDING) == null) {
		val channel = NotificationChannel(
			CHANNEL_RECORDING,
			getString(R.string.app_name),
			NotificationManager.IMPORTANCE_LOW
		)
		channel.description = getString(R.string.recording)
		channel.lockscreenVisibility = Notification.VISIBILITY_SECRET
		channel.setSound(null, null)
		channel.setShowBadge(false)
		channel.enableLights(false)
		channel.enableVibration(false)
		nm.createNotificationChannel(channel)
	}
}
