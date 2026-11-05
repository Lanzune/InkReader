package com.inkreader.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inkreader.kernel.core.KernelEngine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                InkReaderM0Screen(KernelEngine().versionLabel())
            }
        }
    }
}

@Composable
private fun InkReaderM0Screen(kernelLabel: String) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(text = "InkReader", style = MaterialTheme.typography.headlineLarge)
            Text(text = "M0 项目骨架已建立", style = MaterialTheme.typography.titleMedium)
            Text(text = kernelLabel, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "下一阶段：打开本地 TXT、翻页、插件主题/命令。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
