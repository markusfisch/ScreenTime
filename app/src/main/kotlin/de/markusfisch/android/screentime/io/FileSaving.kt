package de.markusfisch.android.screentime.io

import android.content.ContentValues
import android.content.Context
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
	openExternalOutputStream(fileName, mimeType).use { write(it) }
	true
} catch (e: IOException) {
	false
}

private fun Context.openExternalOutputStream(
	fileName: String,
	mimeType: String
): OutputStream = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
	@Suppress("DEPRECATION")
	val file = File(
		Environment.getExternalStoragePublicDirectory(
			Environment.DIRECTORY_DOWNLOADS
		),
		fileName
	)
	if (file.exists()) {
		throw IOException()
	}
	FileOutputStream(file)
} else {
	val uri = contentResolver.insert(
		MediaStore.Downloads.EXTERNAL_CONTENT_URI,
		ContentValues().apply {
			put(MediaStore.Downloads.DISPLAY_NAME, fileName)
			put(MediaStore.Downloads.MIME_TYPE, mimeType)
		}
	) ?: throw IOException()
	contentResolver.openOutputStream(uri) ?: throw IOException()
}
