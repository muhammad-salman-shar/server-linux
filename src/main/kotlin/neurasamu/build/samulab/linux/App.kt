package neurasamu.build.samulab.linux

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.io.File
import java.nio.file.Files
import java.util.Locale

enum class MainTab { CHAT, SERVER }

enum class ShortcutAction {
    NEW_CHAT,
    OPEN_SETTINGS,
    EDIT_LAST_USER,
    REGENERATE_LAST,
    CLEAR_CHAT,
    CLOSE_MODALS
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

@Composable
fun App(shortcuts: SharedFlow<ShortcutAction>) {
    var chatPrefs by remember { mutableStateOf(ChatStore.loadPrefs()) }

    SaMuTheme(themeMode = chatPrefs.themeMode, fontScale = chatPrefs.fontScale) {
        val palette = LocalSaMuColors.current
        Surface(modifier = Modifier.fillMaxSize(), color = palette.background) {
            SaMuLabRoot(
                chatPrefs = chatPrefs,
                onUpdatePrefs = { updated ->
                    chatPrefs = updated
                    ChatStore.savePrefs(updated)
                },
                shortcuts = shortcuts
            )
        }
    }
}

@Composable
private fun SaMuLabRoot(
    chatPrefs: ChatPrefs,
    onUpdatePrefs: (ChatPrefs) -> Unit,
    shortcuts: SharedFlow<ShortcutAction>
) {
    val palette = LocalSaMuColors.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(MainTab.CHAT) }

    // --- Shared Live Server State ---
    var serverSettings by remember { mutableStateOf(SettingsStore.load()) }
    val engine = remember { EngineManager() }
    val monitor = remember { ServerMonitor() }
    val telemetry by monitor.state.collectAsState()
    var apiServer by remember { mutableStateOf<ApiServer?>(null) }
    var serverRunning by remember { mutableStateOf(false) }
    var isStarting by remember { mutableStateOf(false) }
    var localIp by remember { mutableStateOf(getLocalIp()) }
    var models by remember { mutableStateOf(scanModels(serverSettings.modelPaths)) }
    var loadedModelPath by remember { mutableStateOf<String?>(models.firstOrNull()?.absolutePath) }
    var serverStatusMsg by remember { mutableStateOf<String?>(null) }
    var serverStatusErr by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            val latest = withContext(Dispatchers.IO) { getLocalIp() }
            if (latest != localIp) localIp = latest
        }
    }

    // --- Shared Live Chat State ---
    val chatClient = remember { ChatClient() }
    val ttsPlayer = remember { LinuxTtsPlayer() }
    var speakingMsgId by remember { mutableStateOf<String?>(null) }

    var customApis by remember { mutableStateOf(ChatStore.loadApis()) }
    val builtInLocalApi = remember(serverSettings.port, serverSettings.modelApiName, serverSettings.requireAuth, serverSettings.apiKey) {
        ApiConfig(
            id = LOCAL_SAMU_API_ID,
            label = "SaMu Lab (Local)",
            baseUrl = "[http://127.0.0.1](http://127.0.0.1):${serverSettings.port}/v1",
            modelName = serverSettings.modelApiName,
            apiKey = if (serverSettings.requireAuth) serverSettings.apiKey else "",
            isBuiltInLocal = true
        )
    }
    val allApis = remember(builtInLocalApi, customApis) { listOf(builtInLocalApi) + customApis }
    val activeApi = remember(allApis, chatPrefs.activeApiId) {
        allApis.firstOrNull { it.id == chatPrefs.activeApiId } ?: allApis.firstOrNull()
    }

    var chats by remember {
        mutableStateOf(
            ChatStore.loadChats().ifEmpty { listOf(ChatSession(title = "New Chat")) }
        )
    }
    var activeChatId by remember {
        mutableStateOf(
            chats.firstOrNull { it.id == chatPrefs.activeChatId }?.id ?: chats.first().id
        )
    }
    val activeChat = chats.firstOrNull { it.id == activeChatId } ?: chats.first()

    var inputText by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<ChatAttachment>>(emptyList()) }
    var isStreaming by remember { mutableStateOf(false) }
    var streamingText by remember { mutableStateOf("") }
    var streamingChatId by remember { mutableStateOf<String?>(null) }

    // Modals & Toast
    var showApiModal by remember { mutableStateOf(false) }
    var editingApi by remember { mutableStateOf<ApiConfig?>(null) }
    var showChatSettingsModal by remember { mutableStateOf(false) }
    var lightboxAttachment by remember { mutableStateOf<ChatAttachment?>(null) }
    var selectingText by remember { mutableStateOf<String?>(null) }
    var renamingChat by remember { mutableStateOf<ChatSession?>(null) }
    var deletingChat by remember { mutableStateOf<ChatSession?>(null) }
    var deletingApi by remember { mutableStateOf<ApiConfig?>(null) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    fun showToast(msg: String) {
        toastMsg = msg
        scope.launch {
            delay(2000)
            if (toastMsg == msg) toastMsg = null
        }
    }

    fun persistChats(updated: List<ChatSession>) {
        chats = updated
        ChatStore.saveChats(updated)
    }

    fun updateChatById(chatId: String, transform: (ChatSession) -> ChatSession) {
        val updated = chats.map { if (it.id == chatId) transform(it) else it }
        persistChats(updated)
    }

    fun setServerStatus(msg: String?, error: Boolean = false) {
        serverStatusMsg = msg
        serverStatusErr = error
    }

    fun stopServer(clearModel: Boolean = false) {
        runCatching { apiServer?.stop() }
        apiServer = null
        engine.stop()
        serverRunning = false
        isStarting = false
        monitor.resetSession()
        monitor.log("INFO", "Server stopped")
        if (clearModel) loadedModelPath = null
    }

    fun startServerWithModel(modelFile: File) {
        if (isStarting) return
        if (!modelFile.exists()) {
            setServerStatus("Model file not found: ${modelFile.name}", error = true)
            return
        }
        localIp = getLocalIp()
        loadedModelPath = modelFile.absolutePath
        isStarting = true
        setServerStatus("Preparing ${modelFile.name}...", error = false)
        monitor.log("INFO", "Starting model ${modelFile.name}")

        engine.ensureEngineAndStart(
            modelPath = modelFile.toPath(),
            settings = serverSettings,
            onProgress = { msg ->
                scope.launch(Dispatchers.Main) {
                    setServerStatus(msg, error = false)
                }
            },
            onReady = { ok, err ->
                scope.launch(Dispatchers.Main) {
                    isStarting = false
                    if (ok) {
                        try {
                            runCatching { apiServer?.stop() }
                            localIp = getLocalIp()
                            apiServer = ApiServer({ serverSettings }, engine, monitor).also { it.start() }
                            serverRunning = true
                            monitor.markReady()
                            val host = if (serverSettings.lanAccess) localIp else "127.0.0.1"
                            setServerStatus("Running on http://$host:${serverSettings.port}/v1", error = false)
                        } catch (e: Exception) {
                            engine.stop()
                            apiServer = null
                            serverRunning = false
                            monitor.log("ERR", "API server bind failed: ${e.message}")
                            setServerStatus("API server failed: ${e.message}", error = true)
                        }
                    } else {
                        serverRunning = false
                        monitor.log("ERR", err ?: "Engine failed")
                        setServerStatus(err ?: "Engine failed to start", error = true)
                    }
                }
            },
            onUnexpectedExit = { code ->
                scope.launch(Dispatchers.Main) {
                    runCatching { apiServer?.stop() }
                    apiServer = null
                    serverRunning = false
                    isStarting = false
                    monitor.resetSession()
                    monitor.log("ERR", "Engine crashed (exit $code)")
                    setServerStatus("Engine exited unexpectedly (code $code). Check engine.log", error = true)
                }
            }
        )
    }

    fun runAssistantForChat(chatId: String, history: List<ChatMessage>) {
        val api = activeApi
        if (api == null) {
            showToast("Please select or add an API first.")
            return
        }
        if (api.isBuiltInLocal && !serverRunning) {
            updateChatById(chatId) { c ->
                c.copy(
                    messages = c.messages + ChatMessage(
                        role = "assistant",
                        content = "Local SaMu Lab server is stopped. Switch to the Server tab and click Start Server.",
                        isError = true
                    )
                )
            }
            return
        }

        isStreaming = true
        streamingText = ""
        streamingChatId = chatId

        val globalInstr = if (chatPrefs.customInstructionsEnabled) chatPrefs.customInstructions else null
        scope.launch(Dispatchers.IO) {
            chatClient.streamChat(
                api = api,
                history = history,
                globalInstructions = globalInstr,
                onDelta = { partial ->
                    scope.launch(Dispatchers.Main) {
                        streamingText = partial
                    }
                },
                onComplete = { fullText, _, err ->
                    scope.launch(Dispatchers.Main) {
                        isStreaming = false
                        streamingText = ""
                        streamingChatId = null
                        val replyMsg = if (err != null && fullText.isBlank()) {
                            ChatMessage(role = "assistant", content = err, isError = true)
                        } else if (fullText.isNotBlank()) {
                            ChatMessage(role = "assistant", content = fullText)
                        } else {
                            ChatMessage(role = "assistant", content = "Empty response from model.", isError = true)
                        }
                        updateChatById(chatId) { c ->
                            c.copy(messages = c.messages + replyMsg, updatedAt = System.currentTimeMillis())
                        }
                    }
                }
            )
        }
    }

    fun sendCurrentMessage() {
        val trimmed = inputText.trim()
        if ((trimmed.isEmpty() && attachments.isEmpty()) || isStreaming) return
        val targetChat = activeChat
        val userMsg = ChatMessage(
            role = "user",
            content = trimmed,
            attachments = attachments
        )
        val isFirst = targetChat.messages.isEmpty()
        val autoTitle = if (isFirst && trimmed.isNotBlank()) {
            if (trimmed.length > 40) trimmed.take(40) + "..." else trimmed
        } else targetChat.title

        val newHistory = targetChat.messages + userMsg
        updateChatById(targetChat.id) { c ->
            c.copy(title = autoTitle, messages = newHistory, updatedAt = System.currentTimeMillis())
        }
        inputText = ""
        attachments = emptyList()
        runAssistantForChat(targetChat.id, newHistory)
    }

    fun createNewChat() {
        val fresh = ChatSession(title = "New Chat")
        persistChats(listOf(fresh) + chats)
        activeChatId = fresh.id
        onUpdatePrefs(chatPrefs.copy(activeChatId = fresh.id))
        activeTab = MainTab.CHAT
    }

    fun editMessage(msg: ChatMessage) {
        if (isStreaming) chatClient.abort()
        val idx = activeChat.messages.indexOfFirst { it.id == msg.id }
        if (idx < 0) return
        inputText = msg.content
        attachments = msg.attachments
        updateChatById(activeChat.id) { c -> c.copy(messages = c.messages.take(idx)) }
    }

    fun regenerateFrom(msg: ChatMessage) {
        if (isStreaming) return
        val idx = activeChat.messages.indexOfFirst { it.id == msg.id }
        if (idx < 0) return
        val trimmedHistory = activeChat.messages.take(idx)
        updateChatById(activeChat.id) { c -> c.copy(messages = trimmedHistory) }
        if (trimmedHistory.isNotEmpty()) {
            runAssistantForChat(activeChat.id, trimmedHistory)
        }
    }

    val latestActiveChat by rememberUpdatedState(activeChat)
    val latestNewChat by rememberUpdatedState(::createNewChat)
    val latestEditMsg by rememberUpdatedState(::editMessage)
    val latestRegenMsg by rememberUpdatedState(::regenerateFrom)

    LaunchedEffect(shortcuts) {
        shortcuts.collect { action ->
            when (action) {
                ShortcutAction.NEW_CHAT -> latestNewChat()
                ShortcutAction.OPEN_SETTINGS -> showChatSettingsModal = true
                ShortcutAction.EDIT_LAST_USER -> {
                    latestActiveChat.messages.lastOrNull { it.role == "user" }?.let { latestEditMsg(it) }
                }
                ShortcutAction.REGENERATE_LAST -> {
                    latestActiveChat.messages.lastOrNull { it.role == "assistant" }?.let { latestRegenMsg(it) }
                }
                ShortcutAction.CLEAR_CHAT -> {
                    if (isStreaming) chatClient.abort()
                    updateChatById(latestActiveChat.id) { it.copy(messages = emptyList(), title = "New Chat") }
                    showToast("Chat cleared")
                }
                ShortcutAction.CLOSE_MODALS -> {
                    showApiModal = false
                    showChatSettingsModal = false
                    lightboxAttachment = null
                    selectingText = null
                    renamingChat = null
                    deletingChat = null
                    deletingApi = null
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ttsPlayer.stop()
            chatClient.shutdown()
            runCatching { apiServer?.stop() }
            engine.shutdown()
        }
    }

    val appIconBmp = remember {
        runCatching {
            Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")?.use {
                SkiaImage.makeFromEncoded(it.readBytes()).toComposeImageBitmap()
            }
        }.getOrNull()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Global Top Header with Centered [ Chat | Server ] Switcher
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(palette.surface)
                    .padding(horizontal = 14.dp)
            ) {
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (appIconBmp != null) {
                        Image(
                            bitmap = appIconBmp,
                            contentDescription = "SaMu Lab",
                            modifier = Modifier.size(26.dp).clip(RoundedCornerShape(6.dp))
                        )
                    }
                    Text(
                        "SaMu Lab",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(22.dp))
                        .background(palette.background)
                        .border(1.dp, palette.divider, RoundedCornerShape(22.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TopTabPill(
                        label = "Chat",
                        icon = Icons.Filled.Forum,
                        selected = activeTab == MainTab.CHAT,
                        dotColor = if (isStreaming) palette.primary else null,
                        onClick = { activeTab = MainTab.CHAT }
                    )
                    TopTabPill(
                        label = "Server",
                        icon = Icons.Filled.Dns,
                        selected = activeTab == MainTab.SERVER,
                        dotColor = when {
                            serverRunning && telemetry.activeRequests > 0 -> palette.primary
                            serverRunning -> palette.success
                            isStarting -> palette.warning
                            else -> null
                        },
                        onClick = { activeTab = MainTab.SERVER }
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(palette.surfaceVariant)
                            .clickable { activeTab = MainTab.SERVER }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(if (serverRunning) palette.success else palette.textTertiary, CircleShape)
                            )
                            Text(
                                text = if (serverRunning) {
                                    if (telemetry.activeRequests > 0) "Busy · ${String.format(Locale.US, "%.1f", telemetry.liveTokensPerSec)} t/s"
                                    else "Online :${serverSettings.port}"
                                } else "Offline",
                                fontSize = 11.sp,
                                color = palette.textSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            val next = if (palette.isDark) ThemeMode.LIGHT else ThemeMode.DARK
                            onUpdatePrefs(chatPrefs.copy(themeMode = next))
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (palette.isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (activeTab) {
                    MainTab.CHAT -> ChatScreen(
                        allApis = allApis,
                        activeApi = activeApi,
                        localServerRunning = serverRunning,
                        contextSize = serverSettings.contextSize,
                        chats = chats,
                        activeChat = activeChat,
                        inputText = inputText,
                        onInputChange = { inputText = it },
                        attachments = attachments,
                        onAddAttachment = { attachments = attachments + it },
                        onRemoveAttachment = { att -> attachments = attachments.filterNot { it == att } },
                        isStreaming = isStreaming && streamingChatId == activeChat.id,
                        streamingText = if (streamingChatId == activeChat.id) streamingText else "",
                        speakingMsgId = speakingMsgId,
                        onSelectApi = { api -> onUpdatePrefs(chatPrefs.copy(activeApiId = api.id)) },
                        onOpenAddApi = { editingApi = null; showApiModal = true },
                        onOpenEditApi = { api -> editingApi = api; showApiModal = true },
                        onRequestDeleteApi = { deletingApi = it },
                        onNewChat = ::createNewChat,
                        onSelectChat = { chat ->
                            activeChatId = chat.id
                            onUpdatePrefs(chatPrefs.copy(activeChatId = chat.id))
                        },
                        onRequestRenameChat = { renamingChat = it },
                        onDownloadChat = { chat ->
                            val transcript = buildString {
                                appendLine("Chat: ${chat.title}")
                                appendLine("=".repeat(40))
                                appendLine()
                                chat.messages.forEach { m ->
                                    appendLine("[${m.role.uppercase()}]")
                                    appendLine(m.content)
                                    appendLine()
                                }
                            }
                            val saved = saveTextFileDialog("${chat.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.txt", transcript)
                            if (saved != null) showToast("Saved to ${saved.name}")
                        },
                        onRequestDeleteChat = { deletingChat = it },
                        onOpenChatSettings = { showChatSettingsModal = true },
                        onSendMessage = ::sendCurrentMessage,
                        onStopStreaming = { chatClient.abort() },
                        onEditMessage = ::editMessage,
                        onRegenerateMessage = ::regenerateFrom,
                        onSelectTextMessage = { msg -> selectingText = msg.content },
                        onShareMessage = { msg ->
                            if (copyToClipboard(msg.content)) showToast("Copied to share!")
                        },
                        onToggleReadAloud = { msg ->
                            val err = ttsPlayer.toggle(msg.id, msg.content) { activeId ->
                                scope.launch(Dispatchers.Main) { speakingMsgId = activeId }
                            }
                            if (err != null) showToast(err)
                        },
                        onOpenLightbox = { lightboxAttachment = it },
                        onCopyToast = ::showToast,
                        onSwitchToServerTab = { activeTab = MainTab.SERVER }
                    )

                    MainTab.SERVER -> ServerScreen(
                        settings = serverSettings,
                        localIp = localIp,
                        serverRunning = serverRunning,
                        isStarting = isStarting,
                        models = models,
                        loadedModelPath = loadedModelPath,
                        statusMessage = serverStatusMsg,
                        isStatusError = serverStatusErr,
                        telemetry = telemetry,
                        onRefreshIp = {
                            localIp = getLocalIp()
                            showToast("Network IP refreshed: $localIp")
                        },
                        onModelApiNameChange = { name ->
                            serverSettings = serverSettings.copy(modelApiName = name)
                            SettingsStore.save(serverSettings)
                        },
                        onStartOrStop = {
                            if (serverRunning || isStarting) {
                                stopServer(clearModel = false)
                                setServerStatus("Server stopped.", error = false)
                            } else {
                                val selected = models.find { it.absolutePath == loadedModelPath } ?: models.firstOrNull()
                                if (selected == null) {
                                    setServerStatus("Please add a .gguf model first using '+ Add Model'.", error = true)
                                } else {
                                    startServerWithModel(selected)
                                }
                            }
                        },
                        onSelectModel = { file ->
                            if (serverRunning) {
                                stopServer(clearModel = false)
                                startServerWithModel(file)
                            } else {
                                loadedModelPath = file.absolutePath
                                setServerStatus("Loaded ${file.name}. Click Start Server.", error = false)
                            }
                        },
                        onRemoveModel = { file ->
                            if (loadedModelPath == file.absolutePath) stopServer(clearModel = true)
                            val updatedPaths = serverSettings.modelPaths.filterNot {
                                File(it).absoluteFile.normalize().path == file.absoluteFile.normalize().path
                            }
                            serverSettings = serverSettings.copy(modelPaths = updatedPaths)
                            SettingsStore.save(serverSettings)
                            scope.launch(Dispatchers.IO) {
                                val modelsDirPath = SettingsStore.modelsDir().toAbsolutePath().normalize()
                                val filePath = file.toPath().toAbsolutePath().normalize()
                                if (filePath.startsWith(modelsDirPath)) {
                                    runCatching { Files.deleteIfExists(filePath) }
                                }
                                val fresh = scanModels(updatedPaths)
                                withContext(Dispatchers.Main) {
                                    models = fresh
                                    if (loadedModelPath == null) loadedModelPath = fresh.firstOrNull()?.absolutePath
                                    setServerStatus("Removed ${file.name}.", error = false)
                                }
                            }
                        },
                        onAddModelFile = { pickedFile ->
                            val updatedPaths = (serverSettings.modelPaths + pickedFile.path).distinct()
                            serverSettings = serverSettings.copy(modelPaths = updatedPaths)
                            SettingsStore.save(serverSettings)
                            models = scanModels(updatedPaths)
                            loadedModelPath = pickedFile.path
                            setServerStatus("Loaded ${pickedFile.name}. Click Start Server.", error = false)
                        },
                        onSaveServerSettings = { updated ->
                            val safe = updated.validated()
                            serverSettings = safe
                            SettingsStore.save(safe)
                            localIp = getLocalIp()
                            setServerStatus(
                                if (serverRunning) "Settings saved. Restart server to apply changes." else "Settings saved.",
                                error = false
                            )
                        },
                        onCreateShortcut = {
                            val (_, msg) = createLinuxDesktopShortcut()
                            showToast(msg)
                        },
                        onCopyToast = ::showToast
                    )
                }
            }
        }

        // Modals
        if (showApiModal) {
            ApiEditModal(
                initial = editingApi,
                onDismiss = { showApiModal = false },
                onSave = { saved ->
                    val exists = customApis.any { it.id == saved.id }
                    val next = if (exists) customApis.map { if (it.id == saved.id) saved else it } else customApis + saved
                    customApis = next
                    ChatStore.saveApis(next)
                    onUpdatePrefs(chatPrefs.copy(activeApiId = saved.id))
                    showApiModal = false
                    showToast("API saved!")
                }
            )
        }

        if (showChatSettingsModal) {
            ChatSettingsModal(
                prefs = chatPrefs,
                onDismiss = { showChatSettingsModal = false },
                onSave = { updated ->
                    onUpdatePrefs(updated)
                    showChatSettingsModal = false
                    showToast("Settings saved!")
                }
            )
        }

        selectingText?.let { txt ->
            SelectTextModal(
                text = txt,
                onDismiss = { selectingText = null },
                onCopyAll = {
                    if (copyToClipboard(txt)) showToast("Copied!")
                }
            )
        }

        lightboxAttachment?.let { att ->
            ImageLightboxModal(attachment = att, onDismiss = { lightboxAttachment = null })
        }

        renamingChat?.let { chat ->
            RenameChatModal(
                currentTitle = chat.title,
                onDismiss = { renamingChat = null },
                onSave = { newTitle ->
                    updateChatById(chat.id) { it.copy(title = newTitle) }
                    renamingChat = null
                }
            )
        }

        deletingChat?.let { chat ->
            ConfirmDeleteModal(
                title = "Delete Chat",
                message = "Delete \"${chat.title}\"? This cannot be undone.",
                onDismiss = { deletingChat = null },
                onConfirm = {
                    val remaining = chats.filterNot { it.id == chat.id }.ifEmpty { listOf(ChatSession(title = "New Chat")) }
                    persistChats(remaining)
                    if (activeChatId == chat.id) {
                        activeChatId = remaining.first().id
                        onUpdatePrefs(chatPrefs.copy(activeChatId = activeChatId))
                    }
                    deletingChat = null
                }
            )
        }

        deletingApi?.let { api ->
            ConfirmDeleteModal(
                title = "Delete API",
                message = "Remove \"${api.label}\" from saved APIs?",
                onDismiss = { deletingApi = null },
                onConfirm = {
                    val next = customApis.filterNot { it.id == api.id }
                    customApis = next
                    ChatStore.saveApis(next)
                    if (chatPrefs.activeApiId == api.id) {
                        onUpdatePrefs(chatPrefs.copy(activeApiId = LOCAL_SAMU_API_ID))
                    }
                    deletingApi = null
                }
            )
        }

        AnimatedVisibility(
            visible = toastMsg != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(palette.surfaceVariant)
                    .border(1.dp, palette.primary.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(toastMsg.orEmpty(), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = palette.onSurface)
            }
        }
    }
}

@Composable
private fun TopTabPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    dotColor: Color?,
    onClick: () -> Unit
) {
    val palette = LocalSaMuColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) palette.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) palette.onPrimary else palette.textSecondary,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) palette.onPrimary else palette.textSecondary
        )
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(if (selected) Color.White else dotColor, CircleShape)
            )
        }
    }
}
