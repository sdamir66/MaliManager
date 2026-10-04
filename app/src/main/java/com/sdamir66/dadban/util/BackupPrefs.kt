package com.sdamir66.dadban.util

import android.content.Context
import android.net.Uri

private const val PREFS_NAME = "mali_prefs"
private const val KEY_AUTO_BACKUP_URI = "auto_backup_uri"

fun getSavedBackupFolderUri(context: Context): Uri? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val uriString = prefs.getString(KEY_AUTO_BACKUP_URI, null) ?: return null
    return try { Uri.parse(uriString) } catch (e: Exception) { null }
}

fun saveBackupFolderUri(context: Context, uri: Uri) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_AUTO_BACKUP_URI, uri.toString()).apply()
}

fun clearBackupFolderUri(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().remove(KEY_AUTO_BACKUP_URI).apply()
}
