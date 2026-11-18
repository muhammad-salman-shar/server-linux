package neurasamu.build.samulab.linux

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Science
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C4DFF),
    onPrimary = Color.White,
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE6E6E6),
    background = Color(0xFF0D0D0D),
    onBackground = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFB0B0B0),
    error = Color(0xFFEF5350)
)

@Composable
fun App() {
    MaterialTheme(colorScheme = DarkColors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            DashboardScreen()
        }
    }
}

@Composable
private fun DashboardScreen() {
    var settings by remember { mutableStateOf(SettingsStore.load()) }
    val engine = remember { EngineManager() }
    var apiServer by remember { mutableStateOf<ApiServer?>(null) }
    var serverRunning by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf(scanModels()) }
    var loadedModel by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val localIp = remember { getLocalIp() }
    val lanUrl = "http://$localIp:${settings.port}"

    DisposableEffect(Unit) {
        onDispose {
            runCatching { apiServer?.stop() }
            engine.shutdown()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Science,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text("SaMu Lab", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(12.dp).background(
                            if (serverRunning) Color(0xFF4CAF50) else Color(0xFFF44336),
                            CircleShape
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (serverRunning) "Server Running" else "Server Stopped",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = settings.modelApiName,
                    onValueChange = {
                        settings = settings.copy(modelApiName = it)
                        SettingsStore.save(settings)
                    },
                    label = { Text("Model Name (API)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                InfoRow("LAN URL", lanUrl)
                InfoRow("API Key", settings.apiKey)

                statusMessage?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    
                    if (msg.contains("missing") || msg.contains("not executable")) {
                        Button(
                            onClick = {
                                isDownloading = true
                                downloadMsg = "Starting..."
                                engine.downloadEngine(
                                    onProgress = { p -> scope.launch(Dispatchers.Main) { downloadMsg = p } },
                                    onDone = { success, err ->
                                        scope.launch(Dispatchers.Main) {
                                            isDownloading = false
                                            if (success) {
                                                statusMessage = "Engine installed! Click Start Server."
                                            } else {
                                                statusMessage = "Download failed: $err"
                                            }
                                        }
                                    }
                                )
                            },
                            enabled = !isDownloading,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text(if (isDownloading) downloadMsg else "⬇ Auto-Download Engine Now")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { copyToClipboard(lanUrl) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Copy URL") }

                    OutlinedButton(
                        onClick = { copyToClipboard(settings.apiKey) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Copy Key") }

                    Button(
                        onClick = {
                            if (serverRunning) {
                                runCatching { apiServer?.stop() }
                                apiServer = null
                                engine.stop()
                                serverRunning = false
                                loadedModel = null
                                statusMessage = null
                            } else {
                                val model = loadedModel ?: models.firstOrNull()?.name
                                if (model == null) {
                                    statusMessage = "Pehle ek model add karo (+ button se)"
                                    return@Button
                                }
                                val path = SettingsStore.modelsDir().resolve(model)
                                loadedModel = model
                                statusMessage = null
                                engine.start(path, settings) { ok, err ->
                                    scope.launch(Dispatchers.Main) {
                                        if (ok) {
                                            try {
                                                apiServer = ApiServer(settings, engine).also { it.start() }
                                                serverRunning = true
                                            } catch (e: Exception) {
                                                statusMessage = "API server fail: ${e.message}"
                                                engine.stop()
                                                serverRunning = false
                                            }
                                        } else {
                                            statusMessage = err ?: "Engine failed"
                                            serverRunning = false
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (serverRunning) Color(0xFFD32F2F)
                            else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (serverRunning) "Stop Server" else "Start Server")
                    }
                }

                if (serverRunning && loadedModel != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val name = loadedModel ?: return@OutlinedButton
                                engine.stop()
                                val path = SettingsStore.modelsDir().resolve(name)
                                engine.start(path, settings) { _, err ->
                                    scope.launch(Dispatchers.Main) {
                                        if (err != null) statusMessage = err
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Reload Model") }

                        OutlinedButton(
                            onClick = {
                                engine.stop()
                                runCatching { apiServer?.stop() }
                                apiServer = null
                                serverRunning = false
                                loadedModel = null
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Unload Model") }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Models",
            modifier = Modifier.padding(horizontal = 20.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (models.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Koi model nahi hai. Neeche + button se .gguf add karo.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(models, key = { it.name }) { file ->
                        ModelRow(
                            file = file,
                            isLoaded = loadedModel == file.name,
                            onLoad = { loadedModel = file.name },
                            onDelete = {
                                runCatching { Files.deleteIfExists(file.toPath()) }
                                models = scanModels()
                                if (loadedModel == file.name) {
                                    loadedModel = null
                                    engine.stop()
                                    runCatching { apiServer?.stop() }
                                    apiServer = null
                                    serverRunning = false
                                }
                            }
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = {
                    val picked = runCatching { openGgufPicker() }.getOrNull() ?: return@FloatingActionButton
                    val dir = picked.first
                    val name = picked.second
                    if (dir != null && name != null) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val src = File(dir, name).toPath()
                                val dest = SettingsStore.modelsDir().resolve(name)
                                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING)
                                val fresh = scanModels()
                                withContext(Dispatchers.Main) {
                                    models = fresh
                                    statusMessage = null
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    statusMessage = "Copy fail: ${e.message}"
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Model")
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            settings = settings,
            onDismiss = { showSettings = false },
            onSave = {
                settings = it
                SettingsStore.save(it)
                showSettings = false
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$label: ",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ModelRow(
    file: File,
    isLoaded: Boolean,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    val mb = file.length() / (1024.0 * 1024.0)
    val sizeText = if (mb >= 1024) String.format(Locale.US, "%.2f GB", mb / 1024.0)
    else String.format(Locale.US, "%.0f MB", mb)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isLoaded)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    sizeText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            if (isLoaded) {
                Text(
                    "LOADED",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else {
                TextButton(onClick = onLoad) { Text("Load") }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFFEF5350),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsDialog(
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
        title = { Text("Settings") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text("Port") },
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
                    label = { Text("Context Size") },
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
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun scanModels(): List<File> {
    val dir = SettingsStore.modelsDir().toFile()
    return (dir.listFiles { f -> f.isFile && f.name.lowercase().endsWith(".gguf") } ?: emptyArray())
        .sortedBy { it.name.lowercase() }
}

private fun getLocalIp(): String {
    return runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull()?.hostAddress
    }.getOrNull() ?: "127.0.0.1"
}

private fun copyToClipboard(text: String) {
    val sel = java.awt.datatransfer.StringSelection(text)
    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
}

private fun openGgufPicker(): Pair<String?, String?> {
    val dialog = java.awt.FileDialog(
        null as java.awt.Frame?,
        "Select GGUF Model",
        java.awt.FileDialog.LOAD
    )
    dialog.setFilenameFilter { _, name -> name.lowercase().endsWith(".gguf") }
    dialog.isVisible = true
    return dialog.directory to dialog.file
}
