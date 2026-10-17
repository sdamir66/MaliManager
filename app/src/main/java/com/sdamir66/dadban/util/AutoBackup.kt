package com.sdamir66.dadban.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.sdamir66.dadban.data.AppDb
import com.sdamir66.dadban.data.Backup
import com.sdamir66.dadban.data.BackupCrypto

suspend fun autoBackupToInternal(context: Context, db: AppDb): Boolean {
    return try {
        val customFolderUri = getSavedBackupFolderUri(context)
        if (customFolderUri != null) {
            val folder = DocumentFile.fromTreeUri(context, customFolderUri)
            if (folder == null || !folder.exists() || !folder.canWrite()) {
                clearBackupFolderUri(context)
                return autoBackupToDefault(context, db)
            }
            val tempFile = folder.createFile("application/octet-stream", "auto_backup.tmp")
                ?: return autoBackupToDefault(context, db)
            val encrypted = BackupCrypto.encrypt(Backup.exportToJson(db))
            val wrote = context.contentResolver.openOutputStream(tempFile.uri)?.bufferedWriter()?.use {
                it.write(encrypted)
                true
            } ?: false
            if (!wrote) {
                tempFile.delete()
                return false
            }
            val existing = folder.findFile("auto_backup.json")
            existing?.delete()
            if (!tempFile.renameTo("auto_backup.json")) {
                tempFile.delete()
                return false
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
        temp.writeText(BackupCrypto.encrypt(Backup.exportToJson(db)), Charsets.UTF_8)
        if (!temp.exists() || temp.length() == 0L) return false
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
        true
    } catch (e: Exception) { e.printStackTrace(); false }
}
