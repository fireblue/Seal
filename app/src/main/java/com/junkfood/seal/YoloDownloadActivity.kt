package com.junkfood.seal

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.download.Task
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.DownloadUtil.withPreset
import com.junkfood.seal.util.PreferenceUtil
import com.junkfood.seal.util.matchUrlFromSharedText
import org.koin.android.ext.android.inject

/**
 * A share-target Activity that always performs YOLO (immediate) download.
 * Appears as a separate entry in the Android share sheet (e.g. "Seal YOLO").
 */
class YoloDownloadActivity : ComponentActivity() {

    private fun Intent.getSharedURL(): String? {
        return when (action) {
            Intent.ACTION_VIEW -> dataString
            Intent.ACTION_SEND -> {
                getStringExtra(Intent.EXTRA_TEXT)?.let { sharedContent ->
                    removeExtra(Intent.EXTRA_TEXT)
                    matchUrlFromSharedText(sharedContent)
                }
            }
            else -> null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getSharedURL()
        if (url.isNullOrEmpty()) {
            finish()
            return
        }

        App.startService()

        val preferences = DownloadUtil.DownloadPreferences.createFromPreferences()
        val preset = PreferenceUtil.getPresetForUrl(url)
        val finalPreferences =
            if (preset != null) preferences.withPreset(preset) else preferences
        val downloader: DownloaderV2 by inject()
        url.lines().filter { it.isNotBlank() }.forEach { line ->
            downloader.enqueue(Task(url = line, preferences = finalPreferences))
        }
        Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
        finish()
    }
}
