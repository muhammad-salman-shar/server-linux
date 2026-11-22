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
import androidx.compose.material.icons.filled.ContentCopy
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
import java.nio.file.Path
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
    var isStarting by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf(scanModels(settings.modelPaths)) }
    var loadedModelPath by remember { mutableStateOf<String?>(models.firstOrNull()?.absolutePath) }
    var showSettings by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val localIp = remember { getLocalIp() }
    val displayHost = if (settings.lanAccess) localIp else "127.0.0.1"
    val apiBaseUrl = "http://$displayHost:${settings.port}/v1"

    fun setStatus(msg: String?, error: Boolean = false) {
        statusMessage = msg
        isStatusError = error
    }

    fun stopAll(clearModel: Boolean = false) {
        runCatching { apiServer?.stop() }
        apiServer = null
        engine.stop()
        serverRunning = false
        isStarting = false
        if (clearModel) loadedModelPath = null
    }

    fun startWithModel(modelFile: File) {
        if (isStarting) return
        if (!modelFile.exists()) {
            setStatus("Model file not found on disk: ${modelFile.name}", error = true)
            return
        }
        loadedModelPath = modelFile.absolutePath
        isStarting = true
        setStatus("Preparing ${modelFile.name}...", error = false)

        engine.ensureEngineAndStart(
            modelPath = modelFile.toPath(),
            settings = settings,
            onProgress = { msg ->
                scope.launch(Dispatchers.Main) {
                    setStatus(msg, error = false)
                }
            },
            onReady = { ok, err ->
                scope.launch(Dispatchers.Main) {
                    isStarting = false
                    if (ok) {
                        try {
                            runCatching { apiServer?.stop() }
                            apiServer = ApiServer({ settings }, engine).also { it.start() }
                            serverRunning = true
                            setStatus("Running! Connect any device using $apiBaseUrl", error = false)
                        } catch (e: Exception) {
                            engine.stop()
                            apiServer = null
                            serverRunning = false
                            setStatus("API server failed to start: ${e.message}", error = true)
                        }
                    } else {
                        serverRunning = false
                        setStatus(err ?: "Engine failed to start", error = true)
                    }
                }
            },
            onUnexpectedExit = { code ->
                scope.launch(Dispatchers.Main) {
                    runCatching { apiServer?.stop() }
                    apiServer = null
                    serverRunning = false
                    isStarting = false
                    setStatus("Engine stopped unexpectedly (code $code). Check engine.log", error = true)
                }
            }
        )
    }

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
                    val dotColor = when {
                        serverRunning -> Color(0xFF4CAF50)
                        isStarting -> Color(0xFFFFB300)
                        else -> Color(0xFFF44336)
                    }
                    val statusLabel = when {
                        serverRunning -> "Server Running"
                        isStarting -> "Starting..."
                        else -> "Server Stopped"
                    }
                    Box(
                        modifier = Modifier.size(12.dp).background(dotColor, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(statusLabel, fontWeight = FontWeight.SemiBold)
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
                    trailingIcon = {
                        IconButton(onClick = {
                            copyToClipboard(settings.modelApiName)
                            setStatus("Model name copied!", error = false)
                        }) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "Copy Model Name",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                InfoRow(if (settings.lanAccess) "API URL" else "Local URL", apiBaseUrl)
                InfoRow("Model Name", settings.modelApiName)
                if (settings.requireAuth) {
                    InfoRow("API Key", settings.apiKey)
                }

                statusMessage?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        msg,
                        color = if (isStatusError) MaterialTheme.colorScheme.error else Color(0xFF81C784),
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            copyToClipboard(apiBaseUrl)
                            setStatus("API URL copied!", error = false)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Copy URL") }

                    OutlinedButton(
                        onClick = {
                            copyToClipboard(settings.modelApiName)
                            setStatus("Model name copied!", error = false)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Copy Model") }

                    OutlinedButton(
                        onClick = {
                            copyToClipboard(settings.apiKey)
                            setStatus("API key copied!", error = false)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Copy Key") }
                }

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (serverRunning || isStarting) {
                            stopAll(clearModel = false)
                            setStatus("Server stopped.", error = false)
                        } else {
                            val selected = models.find { it.absolutePath == loadedModelPath }
                                ?: models.firstOrNull()
                            if (selected == null) {
                                setStatus("Please add a .gguf model first using the + button.", error = true)
                                return@Button
                            }
                            startWithModel(selected)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (serverRunning || isStarting) Color(0xFFD32F2F)
                        else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        when {
                            isStarting -> "Stop / Cancel"
                            serverRunning -> "Stop Server"
                            else -> "Start Server"
                        }
                    )
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
                        "No models yet. Click + below to pick a .gguf file from storage.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(models, key = { it.absolutePath }) { file ->
                        ModelRow(
                            file = file,
                            isLoaded = loadedModelPath == file.absolutePath,
                            isRunning = serverRunning && loadedModelPath == file.absolutePath,
                            onLoad = {
                                if (serverRunning) {
                                    stopAll(clearModel = false)
                                    startWithModel(file)
                                } else {
                                    loadedModelPath = file.absolutePath
                                    setStatus("Loaded ${file.name}. Click Start Server.", error = false)
                                }
                            },
                            onDelete = {
                                if (loadedModelPath == file.absolutePath) {
                                    stopAll(clearModel = true)
                                }
                                val updatedPaths = settings.modelPaths.filterNot {
                                    File(it).absoluteFile.normalize().path == file.absoluteFile.normalize().path
                                }
                                settings = settings.copy(modelPaths = updatedPaths)
                                SettingsStore.save(settings)

                                scope.launch(Dispatchers.IO) {
                                    val modelsDirPath = SettingsStore.modelsDir().toAbsolutePath().normalize()
                                    val filePath = file.toPath().toAbsolutePath().normalize()
                                    if (filePath.startsWith(modelsDirPath)) {
                                        runCatching { Files.deleteIfExists(filePath) }
                                    }
                                    val fresh = scanModels(updatedPaths)
                                    withContext(Dispatchers.Main) {
                                        models = fresh
                                        if (loadedModelPath == null) {
                                            loadedModelPath = fresh.firstOrNull()?.absolutePath
                                        }
                                        setStatus("Removed ${file.name} from list.", error = false)
                                    }
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
                        val pickedFile = File(dir, name).absoluteFile.normalize()
                        if (!pickedFile.exists()) {
                            setStatus("Selected file not found.", error = true)
                            return@FloatingActionButton
                        }
                        val updatedPaths = (settings.modelPaths + pickedFile.path).distinct()
                        settings = settings.copy(modelPaths = updatedPaths)
                        SettingsStore.save(settings)
                        models = scanModels(updatedPaths)
                        loadedModelPath = pickedFile.path
                        setStatus("Loaded ${pickedFile.name}. Click Start Server.", error = false)
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
            onSave = { updated ->
                val safe = updated.validated()
                settings = safe
                SettingsStore.save(safe)
                showSettings = false
                if (serverRunning) {
                    setStatus("Settings saved. Restart server to apply port/LAN/context changes.", error = false)
                } else {
                    setStatus("Settings saved.", error = false)
                }
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
    isRunning: Boolean,
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
                    if (isRunning) "RUNNING" else "LOADED",
                    color = if (isRunning) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
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
                    contentDescription = "Remove",
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

private fun scanModels(savedPaths: List<String>): List<File> {
    val dir = SettingsStore.modelsDir().toFile()
    val localFiles = (dir.listFiles { f -> f.isFile && f.name.lowercase().endsWith(".gguf") } ?: emptyArray()).toList()
    val linkedFiles = savedPaths.map { File(it) }.filter { it.isFile && it.name.lowercase().endsWith(".gguf") }
    return (localFiles + linkedFiles)
        .map { it.absoluteFile.normalize() }
        .distinctBy { it.path }
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
    runCatching {
        val sel = java.awt.datatransfer.StringSelection(text)
        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
    }
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
