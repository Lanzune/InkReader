package com.inkreader.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.inkreader.formats.txt.TxtFormatProvider
import com.inkreader.kernel.api.BookDocument
import com.inkreader.kernel.api.BuiltInThemes
import com.inkreader.kernel.api.PageInfo
import com.inkreader.kernel.api.ReaderTheme
import com.inkreader.kernel.core.BookOpener
import com.inkreader.kernel.core.ContentBookSource
import com.inkreader.kernel.core.KernelEngine
import com.inkreader.kernel.core.ReadingStateStore
import com.inkreader.kernel.core.SavedReading
import com.inkreader.plugin.quickjs.PluginSnapshot
import com.inkreader.plugin.quickjs.QuickJsPluginHost
import com.inkreader.reader.engine.ReaderSurfaceView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                InkReaderApp()
            }
        }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Loading : Screen
    data class Reading(
        val book: BookDocument,
        val uri: Uri,
        val encoding: String?,
        val initialOffset: Int,
    ) : Screen
}

@Composable
private fun InkReaderApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val readingState = remember(context) { ReadingStateStore(context) }
    var saved: SavedReading? by remember { mutableStateOf(readingState.load()) }
    var screen: Screen by remember { mutableStateOf(Screen.Home) }
    var error: String? by remember { mutableStateOf(null) }
    var pluginSnapshot: PluginSnapshot? by remember { mutableStateOf(null) }
    var pluginError: String? by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val script = context.assets
                    .open("plugins/sample-theme.js")
                    .bufferedReader()
                    .use { it.readText() }
                QuickJsPluginHost().load(script)
            }
        }
        result.onSuccess { pluginSnapshot = it }
            .onFailure { pluginError = it.message ?: "示例插件加载失败" }
    }

    fun openUri(uri: Uri, encoding: String? = null, initialOffset: Int = 0) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // 某些 provider 不支持持久权限；本次会话仍可读取。
        }

        screen = Screen.Loading
        error = null
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val source = ContentBookSource(context, uri)
                    try {
                        BookOpener(listOf(TxtFormatProvider())).open(source, encoding)
                    } finally {
                        source.close()
                    }
                }
            }
            result.onSuccess { book ->
                val record = SavedReading(
                    uri = uri.toString(),
                    title = book.metadata.title,
                    charOffset = initialOffset,
                    encoding = encoding,
                )
                readingState.save(record.uri, record.title, record.charOffset, record.encoding)
                saved = record
                screen = Screen.Reading(book, uri, encoding, initialOffset)
            }.onFailure { failure ->
                error = failure.message ?: "打开文件失败"
                screen = Screen.Home
            }
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) openUri(uri)
    }

    when (val current = screen) {
        Screen.Home -> HomeScreen(
            saved = saved,
            error = error,
            pluginSnapshot = pluginSnapshot,
            pluginError = pluginError,
            onOpen = { openDocument.launch(arrayOf("*/*")) },
            onContinue = {
                saved?.let { record ->
                    openUri(Uri.parse(record.uri), record.encoding, record.charOffset)
                }
            },
        )

        Screen.Loading -> LoadingScreen()

        is Screen.Reading -> ReaderScreen(
            book = current.book,
            pluginTheme = pluginSnapshot?.theme,
            initialCharOffset = current.initialOffset,
            onProgress = { offset ->
                val record = SavedReading(
                    uri = current.uri.toString(),
                    title = current.book.metadata.title,
                    charOffset = offset,
                    encoding = current.encoding,
                )
                readingState.save(record.uri, record.title, record.charOffset, record.encoding)
                saved = record
            },
            onBack = { screen = Screen.Home },
        )
    }
}

@Composable
private fun HomeScreen(
    saved: SavedReading?,
    error: String?,
    pluginSnapshot: PluginSnapshot?,
    pluginError: String?,
    onOpen: () -> Unit,
    onContinue: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(text = "InkReader", style = MaterialTheme.typography.headlineLarge)
            Text(text = "M2 · TXT 阅读与进度", style = MaterialTheme.typography.titleMedium)
            Text(
                text = KernelEngine().versionLabel(),
                style = MaterialTheme.typography.bodySmall,
            )
            if (saved != null) {
                Button(onClick = onContinue) {
                    Text("继续阅读：${saved.title}")
                }
            }
            OutlinedButton(onClick = onOpen) {
                Text(if (saved == null) "打开本地 TXT" else "打开另一本 TXT")
            }
            when {
                pluginSnapshot != null -> {
                    Text(
                        text = "插件主题：${pluginSnapshot.theme.name}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = pluginSnapshot.commandResult,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                pluginError != null -> {
                    Text(
                        text = "示例插件加载失败：$pluginError",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                else -> {
                    Text(
                        text = "正在加载 QuickJS 示例插件…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "已支持 UTF-8/GB18030/Big5/UTF-16 自动识别；最近阅读和字符位置会保存。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(
                text = "正在打开…",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ReaderScreen(
    book: BookDocument,
    pluginTheme: ReaderTheme?,
    initialCharOffset: Int,
    onProgress: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val chapter = book.chapters.first()
    var pageInfo by remember { mutableStateOf(PageInfo(0, 1)) }
    var theme by remember { mutableStateOf(pluginTheme ?: BuiltInThemes.Light) }
    var controlsVisible by remember { mutableStateOf(true) }
    var tocVisible by remember { mutableStateOf(false) }
    var readerView by remember { mutableStateOf<ReaderSurfaceView?>(null) }

    val activity = LocalContext.current.findActivity()
    val view = LocalView.current
    val insetsController = remember(activity, view) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    DisposableEffect(insetsController) {
        insetsController?.hide(WindowInsetsCompat.Type.navigationBars())
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.navigationBars())
            insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        insetsController?.hide(WindowInsetsCompat.Type.navigationBars())
    }

    LaunchedEffect(pluginTheme) {
        if (pluginTheme != null && theme.id == BuiltInThemes.Light.id) {
            theme = pluginTheme
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                ReaderSurfaceView(context).apply {
                    onPageChanged = { info ->
                        pageInfo = info
                        onProgress(info.charOffset)
                    }
                    onCenterTap = { controlsVisible = !controlsVisible }
                    setContent(chapter.text, theme, initialCharOffset)
                    readerView = this
                }
            },
            update = { surface -> surface.setTheme(theme) },
            modifier = Modifier.fillMaxSize(),
        )

        if (controlsVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(theme.background).copy(alpha = 0.93f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        onProgress(readerView?.currentCharOffset() ?: pageInfo.charOffset)
                        onBack()
                    },
                ) {
                    Text("返回", color = Color(theme.foreground))
                }
                Text(
                    text = book.metadata.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(theme.foreground),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${pageInfo.page + 1}/${pageInfo.pageCount}",
                    color = Color(theme.foreground),
                    style = MaterialTheme.typography.labelMedium,
                )
                if (book.toc.isNotEmpty()) {
                    Box {
                        TextButton(onClick = { tocVisible = true }) {
                            Text("目录", color = Color(theme.foreground))
                        }
                        DropdownMenu(
                            expanded = tocVisible,
                            onDismissRequest = { tocVisible = false },
                            modifier = Modifier.heightIn(max = 360.dp),
                        ) {
                            book.toc.forEach { entry ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = entry.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    onClick = {
                                        readerView?.goToCharOffset(entry.charOffset)
                                        tocVisible = false
                                    },
                                )
                            }
                        }
                    }
                }
                TextButton(
                    onClick = {
                        theme = when {
                            pluginTheme != null && theme.id == pluginTheme.id -> BuiltInThemes.Dark
                            theme.dark -> BuiltInThemes.Light
                            else -> BuiltInThemes.Dark
                        }
                    },
                ) {
                    Text(
                        text = if (theme.dark) "日间" else "夜间",
                        color = Color(theme.foreground),
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
