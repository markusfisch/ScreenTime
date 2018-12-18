package de.markusfisch.android.screentime.receiver

import de.markusfisch.android.screentime.service.TrackerService

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

const val SCREEN_STATE = "screen_state"
const val TIMESTAMP = "timestamp"

class EventReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent?) {
		when (intent?.action) {
			Intent.ACTION_BOOT_COMPLETED -> sendIntent(context)
			Intent.ACTION_SCREEN_OFF -> sendIntent(context, false)
			Intent.ACTION_USER_PRESENT -> sendIntent(context, true)
			else -> return
		}
	}

	private fun sendIntent(context: Context, state: Boolean? = null) {
		val intent = Intent(context, TrackerService::class.java)
		state?.let {
			intent.putExtra(SCREEN_STATE, state)
			intent.putExtra(TIMESTAMP, System.currentTimeMillis())
		}
		context.startService(intent)
	}
}
