package de.markusfisch.android.screentime.io

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.ContentValues
import android.content.Context
import android.database.SQLException
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

fun Context.writeExternalFile(
	fileName: String,
	mimeType: String,
	write: (outputStream: OutputStream) -> Unit
): Boolean = try {
	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
		@Suppress("DEPRECATION")
		val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
		writeFile(File(directory, fileName), write)
	} else {
		writeMediaStoreFile(fileName, mimeType, write)
	}
} catch (e: IOException) {
	false
} catch (e: SecurityException) {
	false
} catch (e: SQLException) {
	false
}

internal fun writeFile(file: File, write: (OutputStream) -> Unit): Boolean {
	if (!file.createNewFile()) {
		return false
	}
	var complete = false
	try {
		FileOutputStream(file).use(write)
		complete = true
		return true
	} finally {
		if (!complete) {
			file.delete()
		}
	}
}

@SuppressLint("UseRequiresApi")
@TargetApi(Build.VERSION_CODES.Q)
private fun Context.writeMediaStoreFile(
	fileName: String,
	mimeType: String,
	write: (OutputStream) -> Unit
): Boolean {
	val resolver = contentResolver
	val uri = resolver.insert(
		MediaStore.Downloads.EXTERNAL_CONTENT_URI,
		ContentValues().apply {
			put(MediaStore.Downloads.DISPLAY_NAME, fileName)
			put(MediaStore.Downloads.MIME_TYPE, mimeType)
			put(MediaStore.Downloads.IS_PENDING, 1)
		}
	) ?: return false
	var complete = false
	try {
		val output = resolver.openOutputStream(uri) ?: throw IOException()
		output.use(write)
		complete = resolver.update(uri, ContentValues().apply {
			put(MediaStore.Downloads.IS_PENDING, 0)
		}, null, null) == 1
		return complete
	} finally {
		if (!complete) {
			try {
				resolver.delete(uri, null, null)
			} catch (e: RuntimeException) {
				// Keep the original write failure if provider cleanup fails too.
			}
		}
	}
}
