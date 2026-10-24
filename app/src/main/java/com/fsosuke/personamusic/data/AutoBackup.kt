package com.fsosuke.personamusic.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fsosuke.personamusic.PersonaApp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Weekly backup into a folder the user picked; keeps the newest [KEEP] files. */
class AutoBackup(private val context: Context, private val backup: BackupManager, private val settings: AppSettings) {

    fun enable(tree: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        settings.setBackupFolder(tree.toString())
        schedule()
    }

    fun disable() {
        settings.setBackupFolder(null)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun schedule() {
        if (settings.backupFolder.value == null) return
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Writes a backup now; returns the file name. */
    suspend fun runNow(): String {
        val tree = Uri.parse(settings.backupFolder.value ?: error("No backup folder"))
        val resolver = context.contentResolver
        val parentId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
        val name = "$PREFIX${SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())}.json"
        val target = DocumentsContract.createDocument(resolver, parent, "application/json", name)
            ?: error("Could not create the backup file")
        backup.export(target)
        pruneOld(tree, parentId)
        return name
    }

    private fun pruneOld(tree: Uri, parentId: String) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val backups = mutableListOf<Pair<String, String>>() // name, documentId
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                if (name.startsWith(PREFIX)) backups += name to c.getString(1)
            }
        }
        backups.sortedByDescending { it.first }.drop(KEEP).forEach { (_, id) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }

    private companion object {
        const val WORK_NAME = "auto-backup"
        const val PREFIX = "persona-music-autobackup-"
        const val KEEP = 4
    }
}

class AutoBackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val autoBackup = (applicationContext as PersonaApp).container.autoBackup
        return runCatching { autoBackup.runNow() }.fold({ Result.success() }, { Result.retry() })
    }
}
