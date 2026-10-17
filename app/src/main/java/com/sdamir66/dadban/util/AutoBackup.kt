package com.sdamir66.dadban.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.sdamir66.dadban.data.AppDb
import com.sdamir66.dadban.data.Backup

suspend fun autoBackupToInternal(context: Context, db: AppDb): Boolean {
    return try {
        val customFolderUri = getSavedBackupFolderUri(context)
        if (customFolderUri != null) {
            val folder = DocumentFile.fromTreeUri(context, customFolderUri)
            if (folder == null || !folder.exists() || !folder.canWrite()) {
                clearBackupFolderUri(context)
                return autoBackupToDefault(context, db)
            }
            val existing = folder.findFile("auto_backup.json")
            existing?.delete()
            val newFile = folder.createFile("application/json", "auto_backup")
                ?: return autoBackupToDefault(context, db)
            context.contentResolver.openOutputStream(newFile.uri)?.bufferedWriter()?.use {
                it.write(Backup.exportToJson(db))
            }
            true
        } else {
            autoBackupToDefault(context, db)
        }
    } catch (e: Exception) { e.printStackTrace(); false }
}

private suspend fun autoBackupToDefault(context: Context, db: AppDb): Boolean {
    return try {
        val file = java.io.File(context.filesDir, "auto_backup.json")
        val uri = Uri.fromFile(file)
        Backup.restoreOrExport(context, db, uri, false)
        true
    } catch (e: Exception) { e.printStackTrace(); false }
}
