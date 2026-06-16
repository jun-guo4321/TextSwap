package com.textswap.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.textswap.app.model.BatchStatus
import com.textswap.app.viewmodel.MainViewModel

@Composable
fun ProgressScreen(
    viewModel: MainViewModel,
    onDone: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val progress = if (uiState.progressTotal > 0)
        uiState.progress.toFloat() / uiState.progressTotal
    else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        label = "progress_anim"
    )

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (uiState.batchStatus) {
                BatchStatus.REPLACING -> {
                    // Processing
                    CircularProgressIndicator(
                        modifier = Modifier.size(72.dp),
                        strokeWidth = 6.dp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "正在处理...",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "第 ${uiState.progress} / ${uiState.progressTotal} 张",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Estimated time
                    if (uiState.progress > 0 && uiState.progressTotal > 0) {
                        val remaining = uiState.progressTotal - uiState.progress
                        val estSeconds = remaining * 2 // rough: 2s per image
                        Text(
                            text = "预计剩余: 约${estSeconds}秒",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }

                BatchStatus.DONE -> {
                    // Done
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "完成",
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "处理完成",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "共处理 ${uiState.progressTotal} 张图片",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    // Error summary
                    uiState.error?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = it,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Export button
                    Button(
                        onClick = { viewModel.exportToGallery() },
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(52.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("保存到相册", fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = onDone) {
                        Text("返回首页")
                    }

                    // Export done hint
                    if (uiState.exportDone) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "已保存到相册",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                else -> {
                    // Fallback
                    Text(
                        text = "准备中...",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}