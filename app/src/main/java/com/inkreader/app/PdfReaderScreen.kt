package com.inkreader.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.inkreader.kernel.api.BookDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PdfReaderScreen(
    book: BookDocument,
    uri: Uri,
    initialPage: Int,
    onProgress: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val view = LocalView.current
    var controller by remember { mutableStateOf<PdfDocumentController?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var pageIndex by remember { mutableIntStateOf(initialPage.coerceAtLeast(0)) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var night by remember { mutableStateOf(false) }
    var rendering by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

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

    LaunchedEffect(uri) {
        val result = withContext(Dispatchers.IO) {
            runCatching { PdfDocumentController(context, uri) }
        }
        result.onSuccess { opened ->
            controller = opened
            pageCount = opened.pageCount
            pageIndex = pageIndex.coerceIn(0, (opened.pageCount - 1).coerceAtLeast(0))
        }.onFailure { failure ->
            error = failure.message ?: "无法打开 PDF"
        }
    }

    DisposableEffect(controller) {
        val opened = controller
        onDispose { opened?.close() }
    }

    val pageText = if (pageCount > 0) "${pageIndex + 1}/$pageCount" else "--"
    val background = if (night) Color(0xFF101010) else Color(0xFFE9E1D2)
    val foreground = if (night) Color(0xFFE8E2D6) else Color(0xFF332D26)

    fun goToPage(next: Int) {
        if (pageCount == 0) return
        pageIndex = next.coerceIn(0, pageCount - 1)
        scale = 1f
        offset = Offset.Zero
        onProgress(pageIndex)
    }

    BackHandler {
        onProgress(pageIndex)
        onBack()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .clipToBounds(),
    ) {
        val widthPx = constraints.maxWidth
        LaunchedEffect(controller, pageIndex, widthPx) {
            val opened = controller ?: return@LaunchedEffect
            if (widthPx <= 0) return@LaunchedEffect
            rendering = true
            val next = withContext(Dispatchers.IO) {
                runCatching { opened.renderPage(pageIndex, widthPx) }
            }
            next.onSuccess { bitmap = it }
                .onFailure { error = it.message ?: "PDF 页面渲染失败" }
            rendering = false
        }

        val imageBitmap = bitmap?.asImageBitmap()
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "PDF 第 ${pageIndex + 1} 页",
                contentScale = ContentScale.Fit,
                colorFilter = if (night) invertColorFilter() else null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            offset = if (scale <= 1.01f) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.01f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2f
                                }
                            },
                        )
                    },
            )
        }

        if (error != null) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(error.orEmpty(), color = foreground)
                TextButton(onClick = onBack) { Text("返回", color = foreground) }
            }
        } else if (bitmap == null || rendering) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(background.copy(alpha = 0.94f))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                onProgress(pageIndex)
                onBack()
            }) { Text("返回", color = foreground) }
            Text(
                text = book.metadata.title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                color = foreground,
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            )
            Text(pageText, color = foreground)
            TextButton(onClick = { night = !night }) {
                Text(if (night) "日间" else "夜间", color = foreground)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(background.copy(alpha = 0.94f))
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                enabled = pageIndex > 0,
                onClick = { goToPage(pageIndex - 1) },
            ) { Text("上一页", color = foreground) }
            Text("$pageText  ·  ${"%.1f".format(scale)}×", color = foreground)
            TextButton(
                enabled = pageCount > 0 && pageIndex < pageCount - 1,
                onClick = { goToPage(pageIndex + 1) },
            ) { Text("下一页", color = foreground) }
        }
    }
}

private fun invertColorFilter(): ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
