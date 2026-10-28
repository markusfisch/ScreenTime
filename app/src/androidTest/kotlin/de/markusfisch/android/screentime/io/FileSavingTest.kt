package de.markusfisch.android.screentime.io

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.IOException

@Suppress("DEPRECATION")
fun checkMediaStoreCleanup(context: Context) {
	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
		return
	}
	val resolver = context.contentResolver
	val collection = MediaStore.setIncludePending(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
	val name = "screen-time-test-${System.nanoTime()}.db"
	fun findFile() = resolver.query(
		collection, arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.IS_PENDING),
		"${MediaStore.Downloads.DISPLAY_NAME} = ?", arrayOf(name), null
	)
	try {
		check(!context.writeExternalFile(name, "application/vnd.sqlite3") {
			it.write(byteArrayOf(1, 2, 3))
			throw IOException("Injected write failure")
		})
		findFile()?.use { check(it.count == 0) } ?: error("Missing query result")
		check(context.writeExternalFile(name, "application/vnd.sqlite3") {
			it.write(byteArrayOf(1, 2, 3))
		})
		findFile()?.use {
			check(it.count == 1 && it.moveToFirst())
			check(it.getInt(1) == 0)
		} ?: error("Missing query result")
	} finally {
		findFile()?.use {
			while (it.moveToNext()) {
				resolver.delete(ContentUris.withAppendedId(collection, it.getLong(0)), null, null)
			}
		}
	}
}
