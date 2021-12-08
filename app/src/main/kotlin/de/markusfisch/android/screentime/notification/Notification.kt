package de.markusfisch.android.screentime.notification

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import de.markusfisch.android.screentime.R

private const val CHANNEL_RECORDING = "screen_time_recording"

fun buildNotification(
	context: Context,
	icon: Int,
	title: String,
	intent: Intent
): Notification {
	val pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)
	return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
		val remoteViews = RemoteViews(
			context.packageName,
			R.layout.notification
		)
		remoteViews.setTextViewText(R.id.notification_title, title)
		@Suppress("DEPRECATION")
		val notification = Notification(icon, title, System.currentTimeMillis())
		notification.contentIntent = pendingIntent
		@Suppress("DEPRECATION")
		notification.contentView = remoteViews
		notification.flags = notification.flags or
				Notification.FLAG_ONGOING_EVENT or
				Notification.FLAG_ONLY_ALERT_ONCE
		notification
	} else {
		val builder = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
			@Suppress("DEPRECATION")
			val b = Notification.Builder(context)
			if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
				@Suppress("DEPRECATION")
				b.setPriority(Notification.PRIORITY_MIN)
			}
			b
		} else {
			createChannel(context)
			Notification.Builder(context, CHANNEL_RECORDING)
		}.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setSmallIcon(icon)
			.setContentTitle(title)
			.setContentIntent(pendingIntent)

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
			@Suppress("DEPRECATION")
			builder.notification
		} else {
			builder.build()
		}
	}
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
