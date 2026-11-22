package neurasamu.build.samulab.linux

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.Locale

@Composable
fun ServerScreen(
    settings: AppSettings,
    localIp: String,
    serverRunning: Boolean,
    isStarting: Boolean,
    models: List<File>,
    loadedModelPath: String?,
    statusMessage: String?,
    isStatusError: Boolean,
    telemetry: ServerTelemetry,
    onRefreshIp: () -> Unit,
    onModelApiNameChange: (String) -> Unit,
    onStartOrStop: () -> Unit,
    onSelectModel: (File) -> Unit,
    onRemoveModel: (File) -> Unit,
    onAddModelFile: (File) -> Unit,
    onSaveServerSettings: (AppSettings) -> Unit,
    onCreateShortcut: () -> Unit,
    onCopyToast: (String) -> Unit
) {
    val palette = LocalSaMuColors.current
    var showServerSettings by remember { mutableStateOf(false) }

    val displayHost = if (settings.lanAccess) localIp else "127.0.0.1"
    val apiBaseUrl = "http://$displayHost:${settings.port}/v1"

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Column: Server Controls + Models
        Box(modifier = Modifier.weight(1.25f).fillMaxHeight()) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = palette.surface),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, palette.divider)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val dotColor = when {
                                serverRunning && telemetry.activeRequests > 0 -> palette.primary
                                serverRunning -> palette.success
                                isStarting -> palette.warning
                                else -> palette.error
                            }
                            val statusLabel = when {
                                serverRunning && telemetry.activeRequests > 0 -> "Processing (${telemetry.activeRequests} active)"
                                serverRunning -> "Server Running"
                                isStarting -> "Starting Engine..."
                                else -> "Server Stopped"
                            }
                            Box(modifier = Modifier.size(11.dp).background(dotColor, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(statusLabel, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = palette.onSurface)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = onCreateShortcut, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.DesktopWindows, contentDescription = "Add Desktop Shortcut", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onRefreshIp, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh Network IP", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { showServerSettings = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Settings, contentDescription = "Server Settings", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = settings.modelApiName,
                            onValueChange = onModelApiNameChange,
                            label = { Text("Model Name (API)") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = palette.background,
                                unfocusedContainerColor = palette.background,
                                focusedBorderColor = palette.primary,
                                unfocusedBorderColor = palette.surfaceVariant,
                                focusedTextColor = palette.onSurface,
                                unfocusedTextColor = palette.onSurface
                            ),
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (copyToClipboard(settings.modelApiName)) onCopyToast("Model name copied!")
                                }) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Model Name", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(10.dp))

                        ServerInfoRow(if (settings.lanAccess) "API URL" else "Local URL", apiBaseUrl)
                        ServerInfoRow("Model Name", settings.modelApiName)
                        if (settings.requireAuth) {
                            ServerInfoRow("API Key", settings.apiKey)
                        }

                        statusMessage?.let { msg ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                msg,
                                color = if (isStatusError) palette.error else palette.success,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { if (copyToClipboard(apiBaseUrl)) onCopyToast("API URL copied!") },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, palette.divider),
                                modifier = Modifier.weight(1f)
                            ) { Text("Copy URL", color = palette.onSurface, fontSize = 12.sp) }

                            OutlinedButton(
                                onClick = { if (copyToClipboard(settings.modelApiName)) onCopyToast("Model name copied!") },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, palette.divider),
                                modifier = Modifier.weight(1f)
                            ) { Text("Copy Model", color = palette.onSurface, fontSize = 12.sp) }

                            OutlinedButton(
                                onClick = { if (copyToClipboard(settings.apiKey)) onCopyToast("API key copied!") },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, palette.divider),
                                modifier = Modifier.weight(1f)
                            ) { Text("Copy Key", color = palette.onSurface, fontSize = 12.sp) }
                        }

                        Spacer(Modifier.height(10.dp))

                        Button(
                            onClick = onStartOrStop,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (serverRunning || isStarting) palette.error else palette.primary
                            )
                        ) {
                            Text(
                                text = when {
                                    isStarting -> "Stop / Cancel"
                                    serverRunning -> "Stop Server"
                                    else -> "Start Server"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Text(
                    "Models",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (models.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.surface)
                                .border(1.dp, palette.divider, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No models yet. Click + to pick a .gguf file from storage.",
                                color = palette.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(models, key = { it.absolutePath }) { file ->
                                ServerModelRow(
                                    file = file,
                                    isLoaded = loadedModelPath == file.absolutePath,
                                    isRunning = serverRunning && loadedModelPath == file.absolutePath,
                                    onLoad = { onSelectModel(file) },
                                    onDelete = { onRemoveModel(file) }
                                )
                            }
                        }
                    }

                    FloatingActionButton(
                        onClick = {
                            openFilePicker("Select GGUF Model", setOf(".gguf"))?.let(onAddModelFile)
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        containerColor = palette.primary,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Model")
                    }
                }
            }
        }

        // Right Column: Real-Time Processing & Live Activity Feed
        Card(
            modifier = Modifier.weight(0.9f).fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, palette.divider)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Real-Time Monitor",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                if (telemetry.activeRequests > 0) palette.primary.copy(alpha = 0.2f)
                                else palette.surfaceVariant
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (serverRunning) telemetry.currentStatus else "Offline",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (telemetry.activeRequests > 0) palette.primary else palette.textSecondary
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Active", telemetry.activeRequests.toString(), Modifier.weight(1f))
                    StatTile("Requests", telemetry.totalRequests.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Live Speed", String.format(Locale.US, "%.1f t/s", telemetry.liveTokensPerSec), Modifier.weight(1f))
                    StatTile("Tokens", telemetry.totalTokens.toString(), Modifier.weight(1f))
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "LIVE ACTIVITY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    color = palette.textSecondary
                )
                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.background)
                        .border(1.dp, palette.divider, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    if (telemetry.logs.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Waiting for server activity...",
                                fontSize = 12.sp,
                                color = palette.textTertiary
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(telemetry.logs, key = { it.id }) { entry ->
                                val badgeColor = when (entry.level) {
                                    "ACTIVE" -> palette.primary
                                    "OK" -> palette.success
                                    "ERR" -> palette.error
                                    else -> palette.textSecondary
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        entry.time,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = palette.textTertiary
                                    )
                                    Text(
                                        entry.level,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = badgeColor
                                    )
                                    Text(
                                        entry.message,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = palette.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showServerSettings) {
        ServerSettingsDialog(
            settings = settings,
            onDismiss = { showServerSettings = false },
            onSave = { updated ->
                showServerSettings = false
                onSaveServerSettings(updated)
            }
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = LocalSaMuColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.background)
            .border(1.dp, palette.divider, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(label, fontSize = 11.sp, color = palette.textSecondary)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = palette.onSurface, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ServerInfoRow(label: String, value: String) {
    val palette = LocalSaMuColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label: ", color = palette.textSecondary, fontSize = 12.sp)
        Text(value, color = palette.onSurface, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ServerModelRow(
    file: File,
    isLoaded: Boolean,
    isRunning: Boolean,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalSaMuColors.current
    val mb = file.length() / (1024.0 * 1024.0)
    val sizeText = if (mb >= 1024) String.format(Locale.US, "%.2f GB", mb / 1024.0)
    else String.format(Locale.US, "%.0f MB", mb)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isLoaded) palette.primary.copy(alpha = 0.14f) else palette.surface
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isLoaded) palette.primary.copy(alpha = 0.45f) else palette.divider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Storage, contentDescription = null, tint = palette.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = palette.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sizeText, color = palette.textSecondary, fontSize = 11.sp)
            }
            if (isLoaded) {
                Text(
                    if (isRunning) "RUNNING" else "LOADED",
                    color = if (isRunning) palette.success else palette.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else {
                TextButton(onClick = onLoad) { Text("Load", color = palette.primary) }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = palette.error, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ServerSettingsDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    var port by remember { mutableStateOf(settings.port.toString()) }
    var lan by remember { mutableStateOf(settings.lanAccess) }
    var threads by remember { mutableStateOf(settings.threadsOverride.toString()) }
    var ctx by remember { mutableStateOf(settings.contextSize.toString()) }
    var auth by remember { mutableStateOf(settings.requireAuth) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text("Port (1024-65535)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("LAN Access", modifier = Modifier.weight(1f))
                    Switch(checked = lan, onCheckedChange = { lan = it })
                }
                OutlinedTextField(
                    value = threads,
                    onValueChange = { threads = it.filter(Char::isDigit) },
                    label = { Text("Threads (0 = Auto)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ctx,
                    onValueChange = { ctx = it.filter(Char::isDigit) },
                    label = { Text("Context Size (512-131072)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Require API Key", modifier = Modifier.weight(1f))
                    Switch(checked = auth, onCheckedChange = { auth = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    settings.copy(
                        port = port.toIntOrNull() ?: 8080,
                        lanAccess = lan,
                        threadsOverride = threads.toIntOrNull() ?: 0,
                        contextSize = ctx.toIntOrNull() ?: 4096,
                        requireAuth = auth
                    ).validated()
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
