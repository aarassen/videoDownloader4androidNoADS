package com.adnanearaassen.videodownloader.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.adnanearaassen.videodownloader.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Publishes a finished MP4 to its destination and returns the resulting content Uri.
 *
 * Two modes, chosen by the user in Settings:
 *  - **Custom folder (SAF):** if a `ACTION_OPEN_DOCUMENT_TREE` folder is configured, the
 *    file is written there via [DocumentFile]. This can be any location the user picked
 *    (internal storage, SD card, etc.). Such files aren't guaranteed to appear in the
 *    system gallery automatically since they live outside the media collections.
 *  - **Default (MediaStore):** otherwise the file goes into the shared
 *    Movies/VideoDownloader collection via MediaStore (no storage permission on API 29+,
 *    and it shows up in the gallery automatically). The `IS_PENDING` flag hides the entry
 *    while bytes stream in, then is cleared — the recommended scoped-storage pattern.
 */
class MediaStoreSaver(
    private val context: Context,
    private val settings: SettingsRepository,
) {

    /**
     * Copies [localFile] to the configured destination and returns its Uri.
     * @param displayName base file name; ".mp4" is appended if missing.
     */
    suspend fun save(
        localFile: File,
        displayName: String,
        mimeType: String = "video/mp4",
    ): Uri = withContext(Dispatchers.IO) {
        val treeUri = settings.currentFolder()
        if (treeUri != null) {
            saveToTree(treeUri, localFile, displayName, mimeType)
        } else {
            saveToMediaStore(localFile, displayName, mimeType)
        }
    }

    // ---- SAF (user-picked folder) -----------------------------------------

    private fun saveToTree(
        treeUri: Uri,
        localFile: File,
        displayName: String,
        mimeType: String,
    ): Uri {
        val dir = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Selected folder is no longer accessible — pick it again in Settings")
        if (!dir.canWrite()) {
            error("No write access to the selected folder — pick it again in Settings")
        }

        val name = ensureExtension(displayName)
        // Replace an existing file of the same name so retries don't accumulate copies.
        dir.findFile(name)?.takeIf { it.isFile }?.delete()

        val doc = dir.createFile(mimeType, name)
            ?: error("Could not create file in the selected folder")

        try {
            context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                localFile.inputStream().use { input -> input.copyTo(out, DEFAULT_BUFFER) }
            } ?: error("Could not open output stream for ${doc.uri}")
            return doc.uri
        } catch (e: Exception) {
            runCatching { doc.delete() }
            throw e
        }
    }

    // ---- MediaStore (default) ---------------------------------------------

    private fun saveToMediaStore(
        localFile: File,
        displayName: String,
        mimeType: String,
    ): Uri {
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val relativePath =
            Environment.DIRECTORY_MOVIES + "/" + SettingsRepository.DEFAULT_MEDIASTORE_SUBDIR
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, ensureExtension(displayName))
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values)
            ?: error("MediaStore refused to create a new entry")

        try {
            resolver.openOutputStream(uri)?.use { out ->
                localFile.inputStream().use { input -> input.copyTo(out, DEFAULT_BUFFER) }
            } ?: error("Could not open output stream for $uri")

            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    /**
     * Deletes the actual video file the given [uriString] points at (from the previous
     * [save]). Handles both destinations:
     *  - MediaStore (`content://media/...`): deleted via the ContentResolver. Since our
     *    app created the entry, no user consent dialog is needed.
     *  - SAF document (`content://<provider>/tree/.../document/...`): deleted via
     *    DocumentsContract using our persisted write permission.
     *
     * Returns true if a file was removed. Failures (already gone, revoked access) are
     * swallowed and return false — the caller still drops the list record either way.
     */
    suspend fun deleteFile(uriString: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val uri = uriString.toUri()
            if (uri.authority == MediaStore.AUTHORITY) {
                context.contentResolver.delete(uri, null, null) > 0
            } else {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            }
        }.getOrDefault(false)
    }

    private fun ensureExtension(name: String): String =
        if (name.endsWith(".mp4", ignoreCase = true)) name else "$name.mp4"

    companion object {
        private const val DEFAULT_BUFFER = 1 shl 16 // 64 KB
    }
}
