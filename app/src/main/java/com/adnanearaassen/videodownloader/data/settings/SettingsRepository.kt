package com.adnanearaassen.videodownloader.data.settings

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists user settings — currently the download destination folder — in
 * SharedPreferences and exposes it as a [StateFlow].
 *
 * The destination is a **Storage Access Framework tree URI** chosen by the user via
 * `ACTION_OPEN_DOCUMENT_TREE` (any folder, including SD cards). When it's null, downloads
 * fall back to the default MediaStore location (Movies/VideoDownloader) so the app works
 * out of the box. Kept dependency-free (no DataStore).
 */
class SettingsRepository(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _folderUri = MutableStateFlow(loadUri())
    /** The user-picked SAF folder, or null to use the default MediaStore location. */
    val folderUri: StateFlow<Uri?> = _folderUri.asStateFlow()

    fun setCustomFolder(uri: Uri) {
        prefs.edit { putString(KEY_TREE_URI, uri.toString()) }
        _folderUri.value = uri
    }

    fun clearCustomFolder() {
        prefs.edit { remove(KEY_TREE_URI) }
        _folderUri.value = null
    }

    /** Synchronous snapshot for the saver (called off the main thread). */
    fun currentFolder(): Uri? = _folderUri.value

    private fun loadUri(): Uri? =
        prefs.getString(KEY_TREE_URI, null)?.let(Uri::parse)

    companion object {
        private const val PREFS = "vd_settings"
        private const val KEY_TREE_URI = "download_tree_uri"

        /** Default MediaStore sub-path used when no custom folder is set. */
        const val DEFAULT_MEDIASTORE_SUBDIR = "VideoDownloader"
    }
}
