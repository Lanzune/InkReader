package com.inkreader.app

import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.inkreader.formats.txt.TxtFormatProvider
import com.inkreader.kernel.api.BookDocument
import com.inkreader.kernel.api.BuiltInThemes
import com.inkreader.kernel.api.PageInfo
import com.inkreader.kernel.api.ReaderTheme
import com.inkreader.kernel.core.BookOpener
import com.inkreader.kernel.core.ContentBookSource
import com.inkreader.kernel.core.KernelEngine
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
    data class Reading(val book: BookDocument) : Screen
}

@Composable
private fun InkReaderApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    val openDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
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
                        BookOpener(listOf(TxtFormatProvider())).open(source)
                    } finally {
                        source.close()
                    }
                }
            }
            result.onSuccess { book ->
                screen = Screen.Reading(book)
            }.onFailure { failure ->
                error = failure.message ?: "打开文件失败"
                screen = Screen.Home
            }
        }
    }

    when (val current = screen) {
        Screen.Home -> HomeScreen(
            error = error,
            pluginSnapshot = pluginSnapshot,
            pluginError = pluginError,
            onOpen = { openDocument.launch(arrayOf("*/*")) },
        )

        Screen.Loading -> LoadingScreen()

        is Screen.Reading -> ReaderScreen(
            book = current.book,
            pluginTheme = pluginSnapshot?.theme,
            onBack = { screen = Screen.Home },
        )
    }
}

@Composable
private fun HomeScreen(
    error: String?,
    pluginSnapshot: PluginSnapshot?,
    pluginError: String?,
    onOpen: () -> Unit,
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
            Text(text = "M1 · 本地 TXT 阅读 + QuickJS 示例插件", style = MaterialTheme.typography.titleMedium)
            Text(
                text = KernelEngine().versionLabel(),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onOpen) {
                Text("打开本地 TXT")
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
                text = "当前只支持 UTF-8 TXT；编码探测、EPUB、PDF 和完整插件包在后续阶段。",
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
    onBack: () -> Unit,
) {
    val chapter = book.chapters.first()
    var pageInfo by remember { mutableStateOf(PageInfo(0, 1)) }
    var theme by remember { mutableStateOf(pluginTheme ?: BuiltInThemes.Light) }
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(pluginTheme) {
        if (pluginTheme != null && theme.id == BuiltInThemes.Light.id) {
            theme = pluginTheme
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (controlsVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(theme.background).copy(alpha = 0.93f))
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) {
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

            AndroidView(
                factory = { context ->
                    ReaderSurfaceView(context).apply {
                        setContent(chapter.text, theme)
                        onPageChanged = { pageInfo = it }
                        onCenterTap = { controlsVisible = !controlsVisible }
                    }
                },
                update = { view -> view.setTheme(theme) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}
