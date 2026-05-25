package com.isene.tasks.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

/**
 * Reads / writes a hyperlist file through Android's Storage Access
 * Framework. The SAF URI is captured once via Intent.ACTION_OPEN_DOCUMENT
 * (with FLAG_GRANT_PERSISTABLE_URI_PERMISSION) and held in
 * SharedPreferences. ContentResolver does the actual I/O so the app
 * works regardless of where Syncthing dropped the file.
 *
 * "Atomic" write story under SAF is best-effort: ContentResolver opens
 * the document with "wt" (truncate) and a single write. There is no
 * filesystem-level rename available through SAF, so we minimise the
 * window by buffering the whole payload and doing one write.
 */
class TaskRepository(private val context: Context) {

    fun load(uri: Uri): Hyperlist {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        } ?: throw IOException("could not open $uri for read")
        return Hyperlist.parse(text)
    }

    fun save(uri: Uri, hl: Hyperlist) {
        val payload = hl.serialize().toByteArray(Charsets.UTF_8)
        // "wt" = write + truncate. Single contiguous write keeps the
        // window where a concurrent Syncthing scan could see a half
        // file as small as ContentResolver allows.
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(payload) }
            ?: throw IOException("could not open $uri for write")
    }

    /** Last-modified ms via SAF DocumentFile, or 0 if unknown. */
    fun lastModified(uri: Uri): Long {
        return DocumentFile.fromSingleUri(context, uri)?.lastModified() ?: 0L
    }

    /** Human-readable display name for the picked file. */
    fun displayName(uri: Uri): String? {
        return DocumentFile.fromSingleUri(context, uri)?.name
    }
}
