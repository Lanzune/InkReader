package com.inkreader.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Layout
import android.text.style.AlignmentSpan
import android.text.style.ImageSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.inkreader.formats.epub.EpubFormatProvider
import com.inkreader.formats.htmlmd.HtmlMarkdownFormatProvider
import com.inkreader.formats.pdf.PdfFormatProvider
import com.inkreader.formats.txt.TxtFormatProvider
import com.inkreader.kernel.api.BookDocument
import com.inkreader.kernel.api.BuiltInThemes
import com.inkreader.kernel.api.Chapter
import com.inkreader.kernel.api.ChapterImage
import com.inkreader.kernel.api.PageInfo
import com.inkreader.kernel.api.ReaderTheme
import com.inkreader.kernel.core.BookOpener
import com.inkreader.kernel.core.ContentBookSource
import com.inkreader.kernel.core.KernelEngine
import com.inkreader.kernel.core.ReadingStateStore
import com.inkreader.kernel.core.SavedReading
import com.inkreader.plugin.quickjs.PluginSnapshot
import com.inkreader.plugin.quickjs.QuickJsPluginHost
import com.inkreader.reader.engine.ReaderLinkSpan
import com.inkreader.reader.engine.ReaderSurfaceView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
        val persistProgress: Boolean = true,
    ) : Screen
}

@Composable
private fun InkReaderApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val readingState = remember(context) { ReadingStateStore(context) }
    val settingsStore = remember(context) { ReaderSettingsStore(context) }
    var saved: SavedReading? by remember { mutableStateOf(readingState.load()) }
    var settings: ReaderSettings by remember { mutableStateOf(settingsStore.load()) }
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
                        BookOpener(
                            listOf(
                                TxtFormatProvider(),
                                EpubFormatProvider(),
                                PdfFormatProvider(),
                                HtmlMarkdownFormatProvider(),
                            ),
                        ).open(source, encoding)
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

    val debuggable = remember(context) {
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    fun openSampleEpub() {
        screen = Screen.Loading
        error = null
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val source = AssetBookSource(context.assets, "sample.epub", "示例 EPUB")
                    try {
                        BookOpener(listOf(TxtFormatProvider(), EpubFormatProvider(), PdfFormatProvider())).open(source)
                    } finally {
                        source.close()
                    }
                }
            }
            result.onSuccess { book ->
                screen = Screen.Reading(
                    book = book,
                    uri = Uri.parse("asset:///sample.epub"),
                    encoding = null,
                    initialOffset = 0,
                    persistProgress = false,
                )
            }.onFailure { failure ->
                val cause = failure.cause?.let { "${it.javaClass.simpleName}: ${it.message}" }
                error = "示例 EPUB 打开失败：${failure.javaClass.simpleName}: ${failure.message ?: cause ?: ""}"
                screen = Screen.Home
            }
        }
    }

    fun openSamplePdf() {
        screen = Screen.Loading
        error = null
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val source = AssetBookSource(
                        context.assets,
                        "sample.pdf",
                        "示例 PDF",
                        "application/pdf",
                    )
                    try {
                        BookOpener(listOf(PdfFormatProvider())).open(source)
                    } finally {
                        source.close()
                    }
                }
            }
            result.onSuccess { book ->
                screen = Screen.Reading(
                    book = book,
                    uri = Uri.parse("asset:///sample.pdf"),
                    encoding = null,
                    initialOffset = 0,
                    persistProgress = false,
                )
            }.onFailure { failure ->
                error = "示例 PDF 打开失败：${failure.message ?: failure.javaClass.simpleName}"
                screen = Screen.Home
            }
        }
    }

    fun openSampleMarkdown() {
        screen = Screen.Loading
        error = null
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val source = AssetBookSource(
                        context.assets,
                        "sample.md",
                        "示例 Markdown",
                        "text/markdown",
                    )
                    try {
                        BookOpener(listOf(HtmlMarkdownFormatProvider())).open(source)
                    } finally {
                        source.close()
                    }
                }
            }
            result.onSuccess { book ->
                screen = Screen.Reading(
                    book = book,
                    uri = Uri.parse("asset:///sample.md"),
                    encoding = null,
                    initialOffset = 0,
                    persistProgress = false,
                )
            }.onFailure { failure ->
                error = "示例 Markdown 打开失败：${failure.message ?: failure.javaClass.simpleName}"
                screen = Screen.Home
            }
        }
    }

    when (val current = screen) {
        Screen.Home -> HomeScreen(
            saved = saved,
            error = error,
            pluginSnapshot = pluginSnapshot,
            pluginError = pluginError,
            showSample = debuggable,
            onOpen = { openDocument.launch(arrayOf("*/*")) },
            onOpenSample = { openSampleEpub() },
            onOpenPdfSample = { openSamplePdf() },
            onOpenMarkdownSample = { openSampleMarkdown() },
            onContinue = {
                saved?.let { record ->
                    openUri(Uri.parse(record.uri), record.encoding, record.charOffset)
                }
            },
        )

        Screen.Loading -> LoadingScreen()

        is Screen.Reading -> {
            if (current.book.formatId == "pdf") {
                PdfReaderScreen(
                    book = current.book,
                    uri = current.uri,
                    initialPage = current.initialOffset,
                    onProgress = { page ->
                        if (current.persistProgress) {
                            val record = SavedReading(
                                uri = current.uri.toString(),
                                title = current.book.metadata.title,
                                charOffset = page,
                                encoding = null,
                            )
                            readingState.save(
                                record.uri,
                                record.title,
                                record.charOffset,
                                record.encoding,
                            )
                            saved = record
                        }
                    },
                    onBack = { screen = Screen.Home },
                )
            } else {
                ReaderScreen(
                    book = current.book,
                    encoding = current.encoding,
                    pluginTheme = pluginSnapshot?.theme,
                    settings = settings,
                    initialCharOffset = current.initialOffset,
                    onSettingsChange = { updated ->
                        settings = updated
                        settingsStore.save(updated)
                    },
                    onEncodingChange = { encoding, offset ->
                        openUri(current.uri, encoding, offset)
                    },
                    onProgress = { offset ->
                        if (current.persistProgress) {
                            val record = SavedReading(
                                uri = current.uri.toString(),
                                title = current.book.metadata.title,
                                charOffset = offset,
                                encoding = current.encoding,
                            )
                            readingState.save(
                                record.uri,
                                record.title,
                                record.charOffset,
                                record.encoding,
                            )
                            saved = record
                        }
                    },
                    onBack = { screen = Screen.Home },
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    saved: SavedReading?,
    error: String?,
    pluginSnapshot: PluginSnapshot?,
    pluginError: String?,
    showSample: Boolean,
    onOpen: () -> Unit,
    onOpenSample: () -> Unit,
    onOpenPdfSample: () -> Unit,
    onOpenMarkdownSample: () -> Unit,
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
            Text(text = "M4 · TXT / EPUB / PDF / HTML / MD", style = MaterialTheme.typography.titleMedium)
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
                Text(if (saved == null) "打开本地书籍" else "打开另一本")
            }
            if (showSample) {
                TextButton(onClick = onOpenSample) {
                    Text("打开内置示例 EPUB（调试）")
                }
                TextButton(onClick = onOpenPdfSample) {
                    Text("打开内置示例 PDF（调试）")
                }
                TextButton(onClick = onOpenMarkdownSample) {
                    Text("打开内置示例 Markdown（调试）")
                }
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
                text = "支持 TXT/EPUB/PDF/HTML/Markdown；最近阅读和位置会保存。",
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
    encoding: String?,
    pluginTheme: ReaderTheme?,
    settings: ReaderSettings,
    initialCharOffset: Int,
    onSettingsChange: (ReaderSettings) -> Unit,
    onEncodingChange: (String?, Int) -> Unit,
    onProgress: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val chapter = book.chapters.first()
    val context = LocalContext.current
    val readerText = remember(chapter, book.resources) {
        buildReaderText(context, chapter, book.resources)
    }
    var pageInfo by remember { mutableStateOf(PageInfo(0, 1)) }
    var controlsVisible by remember { mutableStateOf(true) }
    var tocVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var readerView by remember { mutableStateOf<ReaderSurfaceView?>(null) }
    var linkHistory by remember { mutableStateOf<List<Int>>(emptyList()) }

    val systemDark = isSystemInDarkTheme()
    val theme = when (settings.themeMode) {
        ReaderThemeMode.SYSTEM -> if (systemDark) BuiltInThemes.Dark else pluginTheme ?: BuiltInThemes.Light
        ReaderThemeMode.LIGHT -> pluginTheme ?: BuiltInThemes.Light
        ReaderThemeMode.DARK -> BuiltInThemes.Dark
    }
    val style = settings.toStyle()

    val activity = LocalContext.current.findActivity()
    val view = LocalView.current
    val insetsController = remember(activity, view) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
    val originalBrightness = remember(activity) {
        activity?.window?.attributes?.screenBrightness ?: -1f
    }

    DisposableEffect(insetsController) {
        insetsController?.hide(WindowInsetsCompat.Type.navigationBars())
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.navigationBars())
            insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    }

    DisposableEffect(activity) {
        onDispose {
            val params = activity?.window?.attributes ?: return@onDispose
            params.screenBrightness = originalBrightness
            activity.window.attributes = params
        }
    }

    LaunchedEffect(activity, settings.brightness) {
        applyWindowBrightness(activity, settings.brightness)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        insetsController?.hide(WindowInsetsCompat.Type.navigationBars())
        applyWindowBrightness(activity, settings.brightness)
    }

    fun leaveReader() {
        if (linkHistory.isNotEmpty()) {
            val target = linkHistory.last()
            linkHistory = linkHistory.dropLast(1)
            readerView?.goToCharOffset(target)
            return
        }
        onProgress(readerView?.currentCharOffset() ?: pageInfo.charOffset)
        applyWindowBrightness(activity, originalBrightness)
        insetsController?.show(WindowInsetsCompat.Type.navigationBars())
        onBack()
    }

    BackHandler(enabled = !settingsVisible && !tocVisible) {
        leaveReader()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                ReaderSurfaceView(context).apply {
                    applyStyle(style)
                    onPageChanged = { info ->
                        pageInfo = info
                        onProgress(info.charOffset)
                    }
                    onCenterTap = { controlsVisible = !controlsVisible }
                    onLinkTap = { target ->
                        linkHistory = linkHistory + currentCharOffset()
                        goToCharOffset(target)
                    }
                    setContent(readerText, theme, initialCharOffset)
                    readerView = this
                }
            },
            update = { surface ->
                surface.setTheme(theme)
                surface.applyStyle(style)
            },
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
                    onClick = { leaveReader() },
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
                TextButton(onClick = { settingsVisible = true }) {
                    Text("设置", color = Color(theme.foreground))
                }
            }
        }
    }

    if (settingsVisible) {
        ReaderSettingsDialog(
            settings = settings,
            encoding = encoding,
            showEncoding = book.formatId == "txt",
            onDismiss = { settingsVisible = false },
            onSettingsChange = onSettingsChange,
            onEncodingChange = { newEncoding ->
                val offset = readerView?.currentCharOffset() ?: pageInfo.charOffset
                onEncodingChange(newEncoding, offset)
            },
        )
    }
}

@Composable
private fun ReaderSettingsDialog(
    settings: ReaderSettings,
    encoding: String?,
    showEncoding: Boolean,
    onDismiss: () -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onEncodingChange: (String?) -> Unit,
) {
    var encodingMenuExpanded by remember { mutableStateOf(false) }
    var fontSize by remember(settings.fontSizeSp) { mutableStateOf(settings.fontSizeSp) }
    var lineSpacing by remember(settings.lineSpacing) { mutableStateOf(settings.lineSpacing) }
    var padding by remember(settings.horizontalPaddingDp) {
        mutableStateOf(settings.horizontalPaddingDp.toFloat())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("阅读设置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("主题", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onSettingsChange(settings.copy(themeMode = mode)) },
                            label = {
                                Text(
                                    when (mode) {
                                        ReaderThemeMode.SYSTEM -> "跟随系统"
                                        ReaderThemeMode.LIGHT -> "日间"
                                        ReaderThemeMode.DARK -> "夜间"
                                    },
                                )
                            },
                        )
                    }
                }

                Text("字号：${fontSize.roundToInt()} sp", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    onValueChangeFinished = {
                        onSettingsChange(settings.copy(fontSizeSp = fontSize))
                    },
                    valueRange = 14f..30f,
                )

                Text("行距：${"%.1f".format(lineSpacing)} 倍", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = lineSpacing,
                    onValueChange = { lineSpacing = it },
                    onValueChangeFinished = {
                        onSettingsChange(settings.copy(lineSpacing = lineSpacing))
                    },
                    valueRange = 1.2f..2.0f,
                )

                Text("左右边距：${padding.roundToInt()} dp", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = padding,
                    onValueChange = { padding = it },
                    onValueChangeFinished = {
                        onSettingsChange(settings.copy(horizontalPaddingDp = padding.roundToInt()))
                    },
                    valueRange = 8f..40f,
                )
                Text(
                    text = "拖动字号/行距/边距后松手生效，避免阅读页连续重排。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "亮度：${settings.brightness?.let { "${(it * 100).roundToInt()}%" } ?: "跟随系统"}",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = settings.brightness ?: 0.5f,
                    onValueChange = {
                        onSettingsChange(settings.copy(brightness = it.coerceAtLeast(0.05f)))
                    },
                    valueRange = 0.05f..1f,
                )
                TextButton(onClick = { onSettingsChange(settings.copy(brightness = null)) }) {
                    Text("亮度跟随系统")
                }

                if (showEncoding) {
                    Text("编码：${encoding ?: "自动"}", style = MaterialTheme.typography.labelLarge)
                    Box {
                        TextButton(onClick = { encodingMenuExpanded = true }) {
                            Text("切换编码")
                        }
                        DropdownMenu(
                            expanded = encodingMenuExpanded,
                            onDismissRequest = { encodingMenuExpanded = false },
                        ) {
                            listOf<String?>(null, "UTF-8", "GB18030", "Big5", "UTF-16LE", "UTF-16BE")
                                .forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option ?: "自动") },
                                        onClick = {
                                            encodingMenuExpanded = false
                                            onEncodingChange(option)
                                        },
                                    )
                                }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        },
    )
}

private fun applyWindowBrightness(activity: Activity?, value: Float?) {
    val window = activity?.window ?: return
    val params = window.attributes
    params.screenBrightness = value?.coerceIn(0.01f, 1f) ?: -1f
    window.attributes = params
}

private fun buildReaderText(
    context: Context,
    chapter: Chapter,
    resources: Map<String, ByteArray>,
): CharSequence {
    if (chapter.images.isEmpty() && chapter.styles.isEmpty() && chapter.links.isEmpty()) {
        return chapter.text
    }

    val builder = SpannableStringBuilder(chapter.text)
    val indentPx = (24 * context.resources.displayMetrics.density).toInt()
    for (style in chapter.styles) {
        val start = style.start.coerceIn(0, builder.length)
        val end = style.endExclusive.coerceIn(start, builder.length)
        if (start >= end) continue
        when (style.kind) {
            com.inkreader.kernel.api.InlineStyleKind.BOLD -> builder.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            com.inkreader.kernel.api.InlineStyleKind.ITALIC -> builder.setSpan(
                StyleSpan(Typeface.ITALIC),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            com.inkreader.kernel.api.InlineStyleKind.HEADING1 -> {
                builder.setSpan(
                    RelativeSizeSpan(1.45f),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            com.inkreader.kernel.api.InlineStyleKind.HEADING2 -> {
                builder.setSpan(
                    RelativeSizeSpan(1.28f),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            com.inkreader.kernel.api.InlineStyleKind.HEADING3 -> {
                builder.setSpan(
                    RelativeSizeSpan(1.15f),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            com.inkreader.kernel.api.InlineStyleKind.INDENT -> builder.setSpan(
                LeadingMarginSpan.Standard(indentPx),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            com.inkreader.kernel.api.InlineStyleKind.ALIGN_CENTER -> builder.setSpan(
                AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            com.inkreader.kernel.api.InlineStyleKind.ALIGN_RIGHT -> builder.setSpan(
                AlignmentSpan.Standard(Layout.Alignment.ALIGN_OPPOSITE),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            com.inkreader.kernel.api.InlineStyleKind.FIRST_LINE_INDENT -> builder.setSpan(
                LeadingMarginSpan.Standard(indentPx, 0),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
    for (link in chapter.links) {
        val start = link.start.coerceIn(0, builder.length)
        val end = link.endExclusive.coerceIn(start, builder.length)
        if (start >= end || link.targetOffset < 0) continue
        builder.setSpan(
            ReaderLinkSpan(link.targetOffset),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    if (chapter.images.isEmpty() || resources.isEmpty()) return builder
    val metrics = context.resources.displayMetrics
    val maxWidth = (metrics.widthPixels - 48 * metrics.density).toInt().coerceAtLeast(1)
    val maxHeight = (metrics.heightPixels * 0.55f).toInt().coerceAtLeast(1)

    chapter.images.sortedByDescending { it.charOffset }.forEach { image ->
        if (image.charOffset !in 0 until builder.length) return@forEach
        if (builder[image.charOffset] != '\uFFFC') return@forEach
        val bitmapBytes = resources[image.resource]
        val drawable = bitmapBytes?.let { LazyEpubDrawable(it, maxWidth, maxHeight) }
        if (drawable == null || !drawable.isValid) {
            builder.replace(
                image.charOffset,
                image.charOffset + 1,
                image.alt.ifBlank { "［图片］" },
            )
            return@forEach
        }
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        builder.setSpan(
            ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM),
            image.charOffset,
            image.charOffset + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        builder.setSpan(
            AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
            image.charOffset,
            image.charOffset + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    return builder
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
