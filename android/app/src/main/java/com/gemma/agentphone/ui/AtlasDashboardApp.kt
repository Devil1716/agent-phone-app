package com.gemma.agentphone.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gemma.agentphone.agent.AgentLogEntry
import com.gemma.agentphone.agent.AgentStatus
import com.gemma.agentphone.model.ModelDownloadState
import kotlin.math.roundToInt

private val AtlasInk = Color(0xFF14212B)
private val AtlasTeal = Color(0xFF0D5C63)
private val AtlasAmber = Color(0xFFEE8D63)
private val AtlasMist = Color(0xFFF6F1E7)
private val AtlasSky = Color(0xFFD8ECEF)
private val AtlasCard = Color(0xD9FCFCF7)
private val AtlasCardBorder = Color(0x80FFFFFF)
private val AtlasMuted = Color(0xFF4F5F6C)
private val AtlasLogSurface = Color(0xCCFFFFFF)
private val AtlasTint = Color(0x1A0D5C63)

@Composable
fun AtlasDashboardApp(
    command: String,
    onCommandChange: (String) -> Unit,
    status: AgentStatus,
    logs: List<AgentLogEntry>,
    downloadState: ModelDownloadState,
    accessibilityEnabled: Boolean,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onVoiceInput: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onDownloadModel: () -> Unit,
    onImportModel: () -> Unit,
    onQuickImport: () -> Unit,
    onOpenBrowserDownload: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    val colors = lightColorScheme(
        primary = AtlasTeal,
        secondary = AtlasAmber,
        tertiary = Color(0xFFF9ED69),
        background = AtlasMist,
        surface = AtlasCard,
        onSurface = AtlasInk,
        onBackground = AtlasInk
    )

    MaterialTheme(colorScheme = colors) {
        val busy = status is AgentStatus.Planning ||
            status is AgentStatus.Executing ||
            status is AgentStatus.Downloading
        val latestTrace = remember(logs) {
            logs.lastOrNull()?.detail ?: "Atlas is ready for a goal, a local model, and a live screen."
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            AtlasMist,
                            AtlasSky,
                            Color(0xFFF4E5D5)
                        )
                    )
                )
        ) {
            BackgroundOrbs()

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    HeroCard(
                        status = status,
                        latestTrace = latestTrace,
                        onOpenSettings = onOpenSettings,
                        onOpenHistory = onOpenHistory
                    )
                }

                if (!accessibilityEnabled) {
                    item {
                        AccessibilityWarningCard(onOpenAccessibilitySettings = onOpenAccessibilitySettings)
                    }
                }

                item {
                    CommandCard(
                        command = command,
                        onCommandChange = onCommandChange,
                        onRun = onRun,
                        onStop = onStop,
                        onVoiceInput = onVoiceInput,
                        busy = busy
                    )
                }

                item {
                    ModelCard(
                        downloadState = downloadState,
                        onDownloadModel = onDownloadModel,
                        onImportModel = onImportModel,
                        onQuickImport = onQuickImport,
                        onOpenBrowserDownload = onOpenBrowserDownload
                    )
                }

                item {
                    LogCard(logs = logs, latestTrace = latestTrace)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.BackgroundOrbs() {
    Box(
        modifier = Modifier
            .size(260.dp)
            .scale(1.15f)
            .blur(110.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x55EE8D63), Color.Transparent)
                ),
                shape = CircleShape
            )
            .align(Alignment.TopStart)
    )
    Box(
        modifier = Modifier
            .size(320.dp)
            .blur(120.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x553AA6C7), Color.Transparent)
                ),
                shape = CircleShape
            )
            .align(Alignment.BottomEnd)
    )
}

@Composable
private fun HeroCard(
    status: AgentStatus,
    latestTrace: String,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit
) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Atlas Agent",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "A local Gemma-powered Android operator that reads the accessibility tree, plans one safe action at a time, and verifies each UI transition.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AtlasMuted
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onOpenHistory,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(AtlasTint)
                            .testTag("openHistoryButton")
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = "History")
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(AtlasTint)
                            .testTag("openSettingsButton")
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                }
            }

            StatusPill(status = status)

            Text(
                text = latestTrace,
                modifier = Modifier.testTag("traceText"),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = AtlasMuted
            )
        }
    }
}

@Composable
private fun AccessibilityWarningCard(
    onOpenAccessibilitySettings: () -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0x33EE8D63)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Accessibility, contentDescription = null)
                }
                Column {
                    Text("Accessibility control is off", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Enable the service so Atlas can read the live UI tree and dispatch taps, swipes, and text input.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AtlasMuted
                    )
                }
            }

            Button(onClick = onOpenAccessibilitySettings) {
                Text("Enable")
            }
        }
    }
}

@Composable
private fun CommandCard(
    command: String,
    onCommandChange: (String) -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onVoiceInput: () -> Unit,
    busy: Boolean
) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Mission Control", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Describe the goal in plain language. Atlas will perceive, plan, execute, and reflect in a strict loop.",
                style = MaterialTheme.typography.bodyMedium,
                color = AtlasMuted
            )

            OutlinedTextField(
                value = command,
                onValueChange = onCommandChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("commandInput"),
                minLines = 3,
                maxLines = 6,
                label = { Text("Goal") },
                placeholder = { Text("Book a cab to the airport, open Wi-Fi settings, or search the web for Gemma.") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.72f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.5f),
                    focusedBorderColor = AtlasTeal.copy(alpha = 0.55f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.7f),
                    focusedTextColor = AtlasInk,
                    unfocusedTextColor = AtlasInk,
                    focusedLabelColor = AtlasTeal,
                    cursorColor = AtlasTeal
                ),
                shape = RoundedCornerShape(22.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onRun,
                    enabled = command.isNotBlank() && !busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("runCommandButton"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AtlasTeal,
                        disabledContainerColor = AtlasTeal.copy(alpha = 0.4f)
                    )
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (busy) "Running" else "Start Agent")
                }

                Button(
                    onClick = onStop,
                    enabled = busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("stopAgentButton"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AtlasAmber,
                        disabledContainerColor = AtlasAmber.copy(alpha = 0.35f)
                    )
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop")
                }

                IconButton(
                    onClick = onVoiceInput,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(AtlasTint)
                        .testTag("voiceInputButton")
                ) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Voice input")
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    downloadState: ModelDownloadState,
    onDownloadModel: () -> Unit,
    onImportModel: () -> Unit,
    onQuickImport: () -> Unit,
    onOpenBrowserDownload: () -> Unit
) {
    val normalizedProgress = when {
        downloadState.isReady -> 1f
        downloadState.totalBytes > 0L -> (downloadState.downloadedBytes.toFloat() / downloadState.totalBytes.toFloat()).coerceIn(0f, 1f)
        downloadState.progressPercent > 0 -> downloadState.progressPercent / 100f
        else -> 0f
    }

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("On-device Gemma runtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        downloadState.message.ifBlank {
                            "Import or download a compatible MediaPipe Gemma task bundle to enable local execution."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (downloadState.errorDetails != null) Color(0xFFD1495B) else AtlasMuted
                    )
                }

                if (downloadState.isDownloading || downloadState.isVerifying) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }
            }

            if (downloadState.errorDetails != null) {
                Text(
                    text = "Technical Detail: ${downloadState.errorDetails}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD1495B),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            LinearProgressIndicator(
                progress = { normalizedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = AtlasTeal,
                trackColor = Color.White.copy(alpha = 0.5f)
            )

            Text(
                text = "${(normalizedProgress * 100).roundToInt()}% | ${(downloadState.downloadedBytes / 1024f / 1024f).roundToInt()} MB / ${(downloadState.totalBytes / 1024f / 1024f).roundToInt()} MB",
                style = MaterialTheme.typography.bodySmall,
                color = AtlasMuted
            )

            if (downloadState.errorDetails != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onQuickImport,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AtlasTeal)
                        ) {
                            Text("Scan Downloads")
                        }
                        Button(
                            onClick = onOpenBrowserDownload,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.58f),
                                contentColor = AtlasInk
                            )
                        ) {
                            Text("Try Browser")
                        }
                    }
                    Text(
                        "Tip: If your network is blocking the site, download the model on your PC and ADB push it to /sdcard/Download/gemma4.task",
                        style = MaterialTheme.typography.bodySmall,
                        color = AtlasMuted
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onDownloadModel,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("downloadModelButton")
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Download")
                    }
                    Button(
                        onClick = onImportModel,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("importModelButton"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.58f),
                            contentColor = AtlasInk
                        )
                    ) {
                        Icon(Icons.Rounded.Upload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import")
                    }
                }
            }
        }
    }
}

@Composable
private fun LogCard(
    logs: List<AgentLogEntry>,
    latestTrace: String
) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Cognitive Loop", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                latestTrace,
                modifier = Modifier.testTag("statusText"),
                style = MaterialTheme.typography.bodyMedium,
                color = AtlasMuted
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.5f))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (logs.isEmpty()) {
                    item {
                        Text(
                            text = "Perception, planning, execution, and reflection logs will stream here once the agent starts moving.",
                            color = AtlasMuted
                        )
                    }
                } else {
                    itemsIndexed(logs.takeLast(24)) { index, entry ->
                        LogRow(index = index, entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(index: Int, entry: AgentLogEntry) {
    val accent = when (entry.success) {
        true -> AtlasTeal
        false -> Color(0xFFD1495B)
        null -> AtlasMuted.copy(alpha = 0.4f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AtlasLogSurface)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.75f)), RoundedCornerShape(20.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f))
                .border(BorderStroke(1.dp, accent), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = accent)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.title, fontWeight = FontWeight.SemiBold)
            Text(
                entry.detail,
                style = MaterialTheme.typography.bodySmall,
                color = AtlasMuted
            )
        }
    }
}

@Composable
private fun StatusPill(status: AgentStatus) {
    val label = when (status) {
        AgentStatus.Idle -> "Idle"
        is AgentStatus.Downloading -> status.progressLabel
        is AgentStatus.Planning -> "Planning"
        is AgentStatus.Executing -> "Executing step ${status.stepIndex}"
        is AgentStatus.Completed -> "Completed"
        is AgentStatus.Failed -> "Needs attention"
        is AgentStatus.Stopped -> "Stopped"
    }
    val color = when (status) {
        AgentStatus.Idle -> AtlasTeal
        is AgentStatus.Downloading -> AtlasTeal
        is AgentStatus.Planning -> Color(0xFF6C63FF)
        is AgentStatus.Executing -> AtlasAmber
        is AgentStatus.Completed -> AtlasTeal
        is AgentStatus.Failed -> Color(0xFFD1495B)
        is AgentStatus.Stopped -> Color(0xFF6D6875)
    }
    val scale = animateFloatAsState(
        targetValue = if (status is AgentStatus.Executing) 1.02f else 1f,
        label = "pill-scale"
    )

    Box(
        modifier = Modifier
            .scale(scale.value)
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.42f)), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        AnimatedContent(targetState = label, label = "status-pill") { value ->
            Text(
                text = value,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun GlassCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = AtlasCard,
        shadowElevation = 18.dp,
        border = BorderStroke(1.dp, AtlasCardBorder)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.64f),
                            Color(0xFFF7FBFC).copy(alpha = 0.46f)
                        )
                    )
                )
                .padding(20.dp),
            content = content
        )
    }
}
