package com.articleimages.downloader.data

import android.content.Context

class Settings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var preferOriginal: Boolean
        get() = prefs.getBoolean(KEY_PREFER_ORIGINAL, true)
        set(value) = prefs.edit().putBoolean(KEY_PREFER_ORIGINAL, value).apply()

    var useCustomDir: Boolean
        get() = prefs.getBoolean(KEY_USE_CUSTOM_DIR, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_CUSTOM_DIR, value).apply()

    var treeUri: String?
        get() = prefs.getString(KEY_TREE_URI, null)
        set(value) = prefs.edit().putString(KEY_TREE_URI, value).apply()

    private companion object {
        const val KEY_PREFER_ORIGINAL = "prefer_original"
        const val KEY_USE_CUSTOM_DIR = "use_custom_dir"
        const val KEY_TREE_URI = "tree_uri"
    }
}
