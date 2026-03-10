package com.junkfood.seal.ui.page.filemanager

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.junkfood.seal.App
import com.junkfood.seal.R
import com.junkfood.seal.util.FileUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MediaExtensions =
    setOf(
        "mp4", "mkv", "webm", "avi", "mov", "flv", "m4v",
        "mp3", "m4a", "ogg", "opus", "flac", "wav", "aac", "wma",
    )

private val AudioExtensions = setOf("mp3", "m4a", "ogg", "opus", "flac", "wav", "aac", "wma")

private val VideoExtensions = setOf("mp4", "mkv", "webm", "avi", "mov", "flv", "m4v")

private enum class SortMode { DATE, NAME, SIZE }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileManagerPage(
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (String) -> Unit,
) {
    val context = LocalContext.current
    val rootDir = remember { File(App.videoDownloadDir).also { it.mkdirs() } }

    // Check and request MANAGE_EXTERNAL_STORAGE on Android 11+
    var hasStoragePermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
        )
    }

    var currentDir by remember { mutableStateOf(rootDir) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var sortMode by remember { mutableStateOf(SortMode.DATE) }
    var showSortMenu by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // File to delete (show confirmation dialog)
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    // File for context menu
    var contextMenuFile by remember { mutableStateOf<File?>(null) }

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    fun loadFiles() {
        if (!hasStoragePermission) return
        val listed = currentDir.listFiles()
            ?.filter { !it.name.startsWith(".") }
            ?: emptyList()
        files =
            when (sortMode) {
                SortMode.DATE -> listed.sortedByDescending { it.lastModified() }
                SortMode.NAME -> listed.sortedBy { it.name.lowercase() }
                SortMode.SIZE -> listed.sortedByDescending { if (it.isDirectory) Long.MAX_VALUE else it.length() }
            }.let { sorted ->
                sorted.filter { it.isDirectory } + sorted.filter { it.isFile }
            }
    }

    LaunchedEffect(currentDir, sortMode, refreshTrigger, hasStoragePermission) { loadFiles() }

    // Re-check permission and refresh on resume
    LifecycleResumeEffect(Unit) {
        hasStoragePermission = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
        refreshTrigger++
        onPauseOrDispose {}
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    // Breadcrumb: relative path from root
    val relativePath =
        remember(currentDir, rootDir) {
            currentDir.absolutePath
                .removePrefix(rootDir.absolutePath)
                .removePrefix("/")
                .ifEmpty { "" }
        }

    val title =
        if (relativePath.isEmpty()) stringResource(R.string.file_manager)
        else relativePath

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentDir != rootDir && currentDir.absolutePath.startsWith(rootDir.absolutePath)) {
                                currentDir = currentDir.parentFile ?: rootDir
                            } else {
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Rounded.SortByAlpha, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            SortMode.entries.forEach { mode ->
                                val label =
                                    when (mode) {
                                        SortMode.DATE -> stringResource(R.string.sort_by_date)
                                        SortMode.NAME -> stringResource(R.string.sort_by_name)
                                        SortMode.SIZE -> stringResource(R.string.sort_by_size)
                                    }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            color =
                                                if (mode == sortMode)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurface,
                                        )
                                    },
                                    onClick = {
                                        sortMode = mode
                                        showSortMenu = false
                                    },
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        if (!hasStoragePermission) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.permission_issue),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.permission_issue_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                androidx.compose.material3.FilledTonalButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:" + context.packageName)
                                if (resolveActivity(context.packageManager) != null) {
                                    context.startActivity(this)
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.grant_permission))
                }
            }
            return@Scaffold
        }

        if (files.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.empty_directory),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = paddingValues,
            ) {
                items(files, key = { it.absolutePath }) { file ->
                    val ext = file.extension.lowercase()
                    val icon =
                        when {
                            file.isDirectory -> Icons.Rounded.Folder
                            ext in VideoExtensions -> Icons.Rounded.VideoFile
                            ext in AudioExtensions -> Icons.Rounded.AudioFile
                            else -> Icons.AutoMirrored.Rounded.InsertDriveFile
                        }
                    val iconTint =
                        when {
                            file.isDirectory -> MaterialTheme.colorScheme.primary
                            ext in VideoExtensions -> MaterialTheme.colorScheme.tertiary
                            ext in AudioExtensions -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                    ListItem(
                        modifier =
                            Modifier.combinedClickable(
                                onClick = {
                                    if (file.isDirectory) {
                                        currentDir = file
                                    } else if (ext in MediaExtensions) {
                                        onNavigateToPlayer(file.absolutePath)
                                    } else {
                                        FileUtil.openFile(path = file.absolutePath) {}
                                    }
                                },
                                onLongClick = {
                                    if (file.isFile) {
                                        contextMenuFile = file
                                    }
                                },
                            ),
                        headlineContent = {
                            Text(
                                text = file.name,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            if (file.isFile) {
                                val size = Formatter.formatFileSize(context, file.length())
                                val date = dateFormatter.format(Date(file.lastModified()))
                                Text(text = "$size · $date")
                            } else {
                                val count = file.listFiles()?.size ?: 0
                                val date = dateFormatter.format(Date(file.lastModified()))
                                Text(text = "$count items · $date")
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(28.dp),
                            )
                        },
                    )
                }
            }
        }

        // Context menu dialog
        contextMenuFile?.let { file ->
            AlertDialog(
                onDismissRequest = { contextMenuFile = null },
                title = {
                    Text(
                        text = file.name,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                text = {
                    Column {
                        val size = Formatter.formatFileSize(context, file.length())
                        Text(text = size, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(16.dp))

                        // Share
                        ListItem(
                            modifier =
                                Modifier.combinedClickable(
                                    onClick = {
                                        FileUtil.createIntentForSharingFile(file.absolutePath)
                                            ?.let { intent ->
                                                context.startActivity(
                                                    android.content.Intent.createChooser(
                                                        intent,
                                                        context.getString(R.string.share),
                                                    )
                                                )
                                            }
                                        contextMenuFile = null
                                    }
                                ),
                            headlineContent = { Text(stringResource(R.string.share)) },
                            leadingContent = {
                                Icon(Icons.Rounded.Share, contentDescription = null)
                            },
                        )

                        // Open with external app
                        ListItem(
                            modifier =
                                Modifier.combinedClickable(
                                    onClick = {
                                        FileUtil.openFile(path = file.absolutePath) {}
                                        contextMenuFile = null
                                    }
                                ),
                            headlineContent = { Text(stringResource(R.string.open_file)) },
                            leadingContent = {
                                Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                            },
                        )

                        // Delete
                        ListItem(
                            modifier =
                                Modifier.combinedClickable(
                                    onClick = {
                                        contextMenuFile = null
                                        fileToDelete = file
                                    }
                                ),
                            headlineContent = {
                                Text(
                                    stringResource(R.string.delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { contextMenuFile = null }) {
                        Text(stringResource(R.string.dismiss))
                    }
                },
            )
        }

        // Delete confirmation dialog
        fileToDelete?.let { file ->
            AlertDialog(
                onDismissRequest = { fileToDelete = null },
                title = { Text(stringResource(R.string.delete)) },
                text = {
                    Text(String.format(stringResource(R.string.delete_file_confirm), file.name))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            file.delete()
                            fileToDelete = null
                            refreshTrigger++
                        }
                    ) {
                        Text(
                            stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { fileToDelete = null }) {
                        Text(stringResource(R.string.dismiss))
                    }
                },
            )
        }
    }
}
