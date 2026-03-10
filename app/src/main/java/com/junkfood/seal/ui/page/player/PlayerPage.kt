@file:Suppress("UnstableApiUsage")

package com.junkfood.seal.ui.page.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.util.Log
import com.junkfood.seal.R
import com.junkfood.seal.util.FileUtil

private const val TAG = "PlayerPage"

/** Holds the file path for the player, set before navigating to Route.PLAYER. */
object PlayerState {
    var filePath: String = ""
}

private val SpeedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

@OptIn(UnstableApi::class)
@Composable
fun PlayerPage(filePath: String, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(filePath)))
            prepare()
            playWhenReady = true
        }
    }

    var speed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var isLandscape by remember { mutableStateOf(false) }

    Log.d(TAG, "=== PlayerPage COMPOSING === isLandscape=$isLandscape, activity=${activity?.hashCode()}, orientation=${activity?.requestedOrientation}")

    fun setOrientation(landscape: Boolean) {
        Log.d(TAG, "setOrientation($landscape) called, current isLandscape=$isLandscape")
        isLandscape = landscape
        activity?.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        Log.d(TAG, "setOrientation done, requestedOrientation=${activity?.requestedOrientation}")
    }

    fun navigateBack() {
        Log.d(TAG, "navigateBack() called")
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onNavigateBack()
    }

    // Immersive mode + keep screen on; cleanup on leave
    DisposableEffect(Unit) {
        Log.d(TAG, "DisposableEffect(Unit) SETUP")
        val window = activity?.window ?: return@DisposableEffect onDispose {
            Log.d(TAG, "DisposableEffect(Unit) DISPOSE - no window")
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            Log.d(TAG, "DisposableEffect(Unit) DISPOSE - releasing player, restoring orientation")
            exoPlayer.release()
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    layoutParams =
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setFullscreenButtonClickListener { fullScreen ->
                        setOrientation(fullScreen)
                    }
                }
            },
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
        ) {
            IconButton(onClick = { navigateBack() }) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Box {
                IconButton(onClick = { showSpeedMenu = true }) {
                    Icon(
                        Icons.Rounded.Speed,
                        contentDescription = stringResource(R.string.playback_speed),
                        tint = Color.White,
                    )
                }
                DropdownMenu(
                    expanded = showSpeedMenu,
                    onDismissRequest = { showSpeedMenu = false },
                ) {
                    SpeedOptions.forEach { s ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (s == 1.0f) "1x" else "${s}x",
                                    color =
                                        if (s == speed) MaterialTheme.colorScheme.primary
                                        else Color.Unspecified,
                                )
                            },
                            onClick = {
                                speed = s
                                exoPlayer.setPlaybackSpeed(s)
                                showSpeedMenu = false
                            },
                        )
                    }
                }
            }

            IconButton(onClick = { setOrientation(!isLandscape) }) {
                Icon(
                    Icons.Rounded.ScreenRotation,
                    contentDescription = stringResource(R.string.toggle_orientation),
                    tint = Color.White,
                )
            }

            IconButton(onClick = { FileUtil.openFile(path = filePath) {} }) {
                Icon(
                    Icons.Rounded.OpenInNew,
                    contentDescription = stringResource(R.string.open_file),
                    tint = Color.White,
                )
            }
        }
    }
}
