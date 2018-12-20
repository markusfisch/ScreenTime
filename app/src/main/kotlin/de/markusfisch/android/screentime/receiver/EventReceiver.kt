package de.markusfisch.android.screentime.receiver

import de.markusfisch.android.screentime.service.TrackerService

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

const val UPDATE_NOTIFICATION = "update_notification"
const val SCREEN_STATE = "screen_state"
const val TIMESTAMP = "timestamp"

class EventReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent?) {
		when (intent?.action) {
			Intent.ACTION_BOOT_COMPLETED -> sendIntent(context)
			Intent.ACTION_SCREEN_ON -> sendUpdateIntent(context)
			Intent.ACTION_SCREEN_OFF -> sendStateIntent(context, false)
			Intent.ACTION_USER_PRESENT -> sendStateIntent(context, true)
			else -> return
		}
	}

	private fun sendIntent(context: Context) {
		val intent = Intent(context, TrackerService::class.java)
		context.startService(intent)
	}

	private fun sendUpdateIntent(context: Context) {
		val intent = Intent(context, TrackerService::class.java)
		intent.putExtra(UPDATE_NOTIFICATION, true)
		context.startService(intent)
	}

	private fun sendStateIntent(context: Context, state: Boolean) {
		val intent = Intent(context, TrackerService::class.java)
		intent.putExtra(SCREEN_STATE, state)
		intent.putExtra(TIMESTAMP, System.currentTimeMillis())
		context.startService(intent)
	}
}
