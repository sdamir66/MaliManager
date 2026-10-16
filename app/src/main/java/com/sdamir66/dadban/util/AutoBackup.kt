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
        val temp = java.io.File(context.filesDir, "auto_backup.json.tmp")
        temp.writeText(Backup.exportToJson(db), Charsets.UTF_8)
        if (!temp.exists() || temp.length() == 0L) return false
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
        true
    } catch (e: Exception) { e.printStackTrace(); false }
}
