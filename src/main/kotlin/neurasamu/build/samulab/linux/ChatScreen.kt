package neurasamu.build.samulab.linux

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    allApis: List<ApiConfig>,
    activeApi: ApiConfig?,
    localServerRunning: Boolean,
    chats: List<ChatSession>,
    activeChat: ChatSession,
    inputText: String,
    onInputChange: (String) -> Unit,
    attachments: List<ChatAttachment>,
    onAddAttachment: (ChatAttachment) -> Unit,
    onRemoveAttachment: (ChatAttachment) -> Unit,
    isStreaming: Boolean,
    streamingText: String,
    speakingMsgId: String?,
    onSelectApi: (ApiConfig) -> Unit,
    onOpenAddApi: () -> Unit,
    onOpenEditApi: (ApiConfig) -> Unit,
    onRequestDeleteApi: (ApiConfig) -> Unit,
    onNewChat: () -> Unit,
    onSelectChat: (ChatSession) -> Unit,
    onRequestRenameChat: (ChatSession) -> Unit,
    onDownloadChat: (ChatSession) -> Unit,
    onRequestDeleteChat: (ChatSession) -> Unit,
    onOpenChatSettings: () -> Unit,
    onSendMessage: () -> Unit,
    onStopStreaming: () -> Unit,
    onEditMessage: (ChatMessage) -> Unit,
    onRegenerateMessage: (ChatMessage) -> Unit,
    onToggleReadAloud: (ChatMessage) -> Unit,
    onOpenLightbox: (ChatAttachment) -> Unit,
    onCopyToast: (String) -> Unit,
    onSwitchToServerTab: () -> Unit
) {
    val palette = LocalSaMuColors.current
    val scale = LocalFontScale.current
    val scope = rememberCoroutineScope()
    var drawerOpen by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(palette.background)) {
        val isCompact = maxWidth < 768.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (!isCompact) {
                ChatSidebar(
                    allApis = allApis,
                    activeApiId = activeApi?.id,
                    localServerRunning = localServerRunning,
                    chats = chats,
                    activeChatId = activeChat.id,
                    onOpenAddApi = onOpenAddApi,
                    onNewChat = onNewChat,
                    onSelectApi = onSelectApi,
                    onEditApi = onOpenEditApi,
                    onDeleteApi = onRequestDeleteApi,
                    onSelectChat = onSelectChat,
                    onRenameChat = onRequestRenameChat,
                    onDownloadChat = onDownloadChat,
                    onDeleteChat = onRequestDeleteChat
                )
            }

            // Main Chat Area
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Top Bar (56dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(palette.surface)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isCompact) {
                        IconButton(
                            onClick = { drawerOpen = !drawerOpen },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = "Toggle Sidebar", tint = palette.onSurface)
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeChat.title,
                            fontSize = (15f * scale).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = if (activeApi != null) {
                            "${activeApi.label} · ${activeApi.modelName}"
                        } else {
                            "No API selected"
                        }
                        Text(
                            text = subtitle,
                            fontSize = (11f * scale).sp,
                            color = if (activeApi != null) palette.primary else palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!isCompact) {
                        Box(
                            modifier = Modifier
                                .height(24.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(palette.surfaceVariant)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${activeChat.estimatedTokens} tokens",
                                fontSize = (11f * scale).sp,
                                color = palette.textSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenChatSettings,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Chat Settings",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))

                // Message List
                val listState = rememberLazyListState()
                val isNearBottom by remember {
                    derivedStateOf {
                        val info = listState.layoutInfo
                        val total = info.totalItemsCount
                        if (total == 0) true
                        else {
                            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                            lastVisible >= total - 2
                        }
                    }
                }

                LaunchedEffect(activeChat.id, activeChat.messages.size, streamingText.length, isStreaming) {
                    val count = activeChat.messages.size + if (isStreaming) 1 else 0
                    if (count > 0 && isNearBottom) {
                        runCatching { listState.animateScrollToItem(count - 1) }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (activeChat.messages.isEmpty() && !isStreaming) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(40.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Ready to chat",
                                fontSize = (16f * scale).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            val emptySub = when {
                                activeApi == null -> "Add an API using + API in the sidebar first."
                                activeApi.isBuiltInLocal && !localServerRunning ->
                                    "Your local SaMu Lab server is currently stopped. Start it in the Server tab or pick another API."
                                else -> "Type below to send your first message."
                            }
                            Text(
                                text = emptySub,
                                fontSize = (13f * scale).sp,
                                color = palette.textSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = (13f * scale * 1.5f).sp,
                                modifier = Modifier.widthIn(max = 320.dp)
                            )
                            if (activeApi?.isBuiltInLocal == true && !localServerRunning) {
                                Spacer(Modifier.height(14.dp))
                                Button(
                                    onClick = onSwitchToServerTab,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
                                ) {
                                    Text("Open Server Tab", color = palette.onPrimary, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(activeChat.messages, key = { it.id }) { msg ->
                                MessageBubbleItem(
                                    message = msg,
                                    isSpeaking = speakingMsgId == msg.id,
                                    onCopy = {
                                        if (copyToClipboard(msg.content)) onCopyToast("Copied!")
                                    },
                                    onCopyCode = { code ->
                                        if (copyToClipboard(code)) onCopyToast("Code copied!")
                                    },
                                    onEdit = { onEditMessage(msg) },
                                    onRegenerate = { onRegenerateMessage(msg) },
                                    onReadAloud = { onToggleReadAloud(msg) },
                                    onOpenLightbox = onOpenLightbox
                                )
                            }

                            if (isStreaming) {
                                item(key = "__streaming__") {
                                    StreamingBubbleItem(
                                        text = streamingText,
                                        onCopyCode = { code ->
                                            if (copyToClipboard(code)) onCopyToast("Code copied!")
                                        }
                                    )
                                }
                            }
                        }

                        // Floating "Jump to latest" button
                        if (!isNearBottom && (activeChat.messages.isNotEmpty() || isStreaming)) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(palette.primary)
                                    .clickable {
                                        val count = activeChat.messages.size + if (isStreaming) 1 else 0
                                        if (count > 0) {
                                            scope.launch {
                                                runCatching { listState.animateScrollToItem(count - 1) }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.ArrowDownward,
                                    contentDescription = "Jump to latest",
                                    tint = palette.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Input Area
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
                ChatInputBar(
                    text = inputText,
                    onTextChange = onInputChange,
                    attachments = attachments,
                    isStreaming = isStreaming,
                    onPickFile = {
                        openFilePicker("Attach File")?.let { f ->
                            val isImg = f.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif")
                            onAddAttachment(ChatAttachment(f.absolutePath, f.name, isImg, f.length()))
                        }
                    },
                    onPickImage = {
                        openFilePicker("Attach Image", setOf(".png", ".jpg", ".jpeg", ".webp", ".gif"))?.let { f ->
                            onAddAttachment(ChatAttachment(f.absolutePath, f.name, true, f.length()))
                        }
                    },
                    onRemoveAttachment = onRemoveAttachment,
                    onOpenLightbox = onOpenLightbox,
                    onSend = onSendMessage,
                    onStop = onStopStreaming
                )
            }
        }

        // Mobile Drawer Overlay
        if (isCompact) {
            AnimatedVisibility(
                visible = drawerOpen,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(220))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { drawerOpen = false }
                        )
                )
            }
            AnimatedVisibility(
                visible = drawerOpen,
                enter = slideInHorizontally(tween(250)) { -it },
                exit = slideOutHorizontally(tween(250)) { -it }
            ) {
                ChatSidebar(
                    allApis = allApis,
                    activeApiId = activeApi?.id,
                    localServerRunning = localServerRunning,
                    chats = chats,
                    activeChatId = activeChat.id,
                    onOpenAddApi = { drawerOpen = false; onOpenAddApi() },
                    onNewChat = { drawerOpen = false; onNewChat() },
                    onSelectApi = { onSelectApi(it); drawerOpen = false },
                    onEditApi = { drawerOpen = false; onOpenEditApi(it) },
                    onDeleteApi = { drawerOpen = false; onRequestDeleteApi(it) },
                    onSelectChat = { onSelectChat(it); drawerOpen = false },
                    onRenameChat = { drawerOpen = false; onRequestRenameChat(it) },
                    onDownloadChat = { drawerOpen = false; onDownloadChat(it) },
                    onDeleteChat = { drawerOpen = false; onRequestDeleteChat(it) }
                )
            }
        }
    }
}

@Composable
private fun ChatSidebar(
    allApis: List<ApiConfig>,
    activeApiId: String?,
    localServerRunning: Boolean,
    chats: List<ChatSession>,
    activeChatId: String,
    onOpenAddApi: () -> Unit,
    onNewChat: () -> Unit,
    onSelectApi: (ApiConfig) -> Unit,
    onEditApi: (ApiConfig) -> Unit,
    onDeleteApi: (ApiConfig) -> Unit,
    onSelectChat: (ChatSession) -> Unit,
    onRenameChat: (ChatSession) -> Unit,
    onDownloadChat: (ChatSession) -> Unit,
    onDeleteChat: (ChatSession) -> Unit
) {
    val palette = LocalSaMuColors.current
    val scale = LocalFontScale.current

    Row(modifier = Modifier.width(280.dp).fillMaxHeight()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(palette.surface)
        ) {
            // [A] Header
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
                Text(
                    text = "SaMu Chat",
                    fontSize = (18f * scale).sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.onBackground
                )
                Text(
                    text = "NeuraSamu",
                    fontSize = (11f * scale).sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onOpenAddApi,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Text("+ API", fontSize = (12f * scale).sp, fontWeight = FontWeight.Medium, color = palette.onPrimary)
                    }
                    OutlinedButton(
                        onClick = onNewChat,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, palette.primary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Text("+ Chat", fontSize = (12f * scale).sp, fontWeight = FontWeight.Medium, color = palette.primary)
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))

            // Scrollable APIs & Chats sections
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                // [B] APIs Section
                Text(
                    text = "APIS",
                    fontSize = (10f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(start = 6.dp, bottom = 8.dp, top = 4.dp)
                )

                if (allApis.isEmpty()) {
                    Text(
                        text = "No APIs yet. Tap + API to add one.",
                        fontSize = (12f * scale).sp,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    allApis.forEach { api ->
                        val active = api.id == activeApiId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) palette.surfaceVariant else Color.Transparent)
                                .clickable { onSelectApi(api) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (active) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(44.dp)
                                        .background(palette.primary)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (api.isBuiltInLocal) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .background(
                                                        if (localServerRunning) palette.success else palette.textTertiary,
                                                        CircleShape
                                                    )
                                            )
                                        }
                                        Text(
                                            text = api.label,
                                            fontSize = (13f * scale).sp,
                                            fontWeight = FontWeight.Medium,
                                            color = palette.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = if (api.isBuiltInLocal) {
                                            "${api.modelName} · ${if (localServerRunning) "Online" else "Stopped"}"
                                        } else api.modelName,
                                        fontSize = (11f * scale).sp,
                                        color = palette.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }

                                if (!api.isBuiltInLocal) {
                                    IconButton(onClick = { onEditApi(api) }, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit API", tint = palette.textTertiary, modifier = Modifier.size(15.dp))
                                    }
                                    IconButton(onClick = { onDeleteApi(api) }, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Filled.Close, contentDescription = "Delete API", tint = palette.textTertiary, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // [C] Chats Section
                Text(
                    text = "CHATS",
                    fontSize = (10f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
                )

                chats.forEach { chat ->
                    val active = chat.id == activeChatId
                    var menuOpen by remember(chat.id) { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) palette.surfaceVariant else Color.Transparent)
                            .clickable { onSelectChat(chat) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (active) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(44.dp)
                                    .background(palette.primary)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = chat.title,
                                    fontSize = (13f * scale).sp,
                                    fontWeight = FontWeight.Medium,
                                    color = palette.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${chat.messages.size} messages",
                                    fontSize = (11f * scale).sp,
                                    color = palette.textSecondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { menuOpen = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = "Chat Options",
                                        tint = palette.textTertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false },
                                    modifier = Modifier.background(palette.surface).widthIn(min = 160.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Rename", fontSize = 13.sp, color = palette.onSurface) },
                                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(16.dp)) },
                                        onClick = { menuOpen = false; onRenameChat(chat) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Download as TXT", fontSize = 13.sp, color = palette.onSurface) },
                                        leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(16.dp)) },
                                        onClick = { menuOpen = false; onDownloadChat(chat) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", fontSize = 13.sp, color = palette.error) },
                                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = palette.error, modifier = Modifier.size(16.dp)) },
                                        onClick = { menuOpen = false; onDeleteChat(chat) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // Right 1dp divider
        Box(Modifier.width(1.dp).fillMaxHeight().background(palette.divider))
    }
}

private fun extractThinking(raw: String): Pair<String?, String> {
    val startIdx = raw.indexOf("<think>")
    if (startIdx < 0) return null to raw
    val endIdx = raw.indexOf("</think>", startIdx + 7)
    return if (endIdx >= 0) {
        val think = raw.substring(startIdx + 7, endIdx).trim()
        val main = (raw.substring(0, startIdx) + raw.substring(endIdx + 8)).trim()
        think to main
    } else {
        // Streaming inside <think> before closing tag
        val think = raw.substring(startIdx + 7).trim()
        val main = raw.substring(0, startIdx).trim()
        think to main
    }
}

@Composable
private fun MessageBubbleItem(
    message: ChatMessage,
    isSpeaking: Boolean,
    onCopy: () -> Unit,
    onCopyCode: (String) -> Unit,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onReadAloud: () -> Unit,
    onOpenLightbox: (ChatAttachment) -> Unit
) {
    val palette = LocalSaMuColors.current
    val scale = LocalFontScale.current
    val scope = rememberCoroutineScope()
    val isUser = message.role == "user"
    var copied by remember { mutableStateOf(false) }

    val bubbleShape = RoundedCornerShape(
        topStart = 14.dp,
        topEnd = 14.dp,
        bottomEnd = if (isUser) 4.dp else 14.dp,
        bottomStart = if (isUser) 14.dp else 4.dp
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth(0.85f)
                .wrapContentWidthAlign(isUser)
        ) {
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(
                        when {
                            message.isError -> palette.errorBg
                            isUser -> palette.userBubble
                            else -> palette.assistantBubble
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = when {
                            message.isError -> palette.error.copy(alpha = 0.6f)
                            isUser -> palette.userBubbleBorder
                            else -> palette.assistantBubbleBorder
                        },
                        shape = bubbleShape
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (isUser) {
                    UserMessageBody(message = message, onOpenLightbox = onOpenLightbox)
                } else if (message.isError) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Error: ${message.content}",
                            fontSize = (14f * scale).sp,
                            color = palette.error
                        )
                        Button(
                            onClick = onRegenerate,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Retry", fontSize = 12.sp, color = palette.onPrimary)
                        }
                    }
                } else {
                    AssistantMessageBody(content = message.content, onCopyCode = onCopyCode)
                }
            }

            // Action Bar
            if (!message.isError) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MessageActionPill(
                        icon = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        label = if (copied) "Copied" else "Copy",
                        tint = if (copied) palette.success else palette.textSecondary,
                        onClick = {
                            onCopy()
                            copied = true
                            scope.launch {
                                delay(1500)
                                copied = false
                            }
                        }
                    )
                    if (isUser) {
                        MessageActionPill(
                            icon = Icons.Filled.Edit,
                            label = "Edit",
                            tint = palette.textSecondary,
                            onClick = onEdit
                        )
                    } else {
                        MessageActionPill(
                            icon = Icons.Filled.Refresh,
                            label = "Regenerate",
                            tint = palette.textSecondary,
                            onClick = onRegenerate
                        )
                        MessageActionPill(
                            icon = if (isSpeaking) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                            label = if (isSpeaking) "Stop" else "Read Aloud",
                            tint = if (isSpeaking) palette.primary else palette.textSecondary,
                            onClick = onReadAloud
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.wrapContentWidthAlign(isUser: Boolean): Modifier = this

@Composable
private fun UserMessageBody(
    message: ChatMessage,
    onOpenLightbox: (ChatAttachment) -> Unit
) {
    val palette = LocalSaMuColors.current
    val scale = LocalFontScale.current
    var expanded by remember(message.id) { mutableStateOf(false) }

    val words = remember(message.content) { message.content.trim().split(Regex("\\s+")).filter { it.isNotEmpty() } }
    val needsTruncation = words.size > 10
    val displayText = if (needsTruncation && !expanded) {
        words.take(10).joinToString(" ") + "..."
    } else message.content

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (message.attachments.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                message.attachments.forEach { att ->
                    if (att.isImage) {
                        val bmp = remember(att.path) { loadImageBitmapSafe(att.path) }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = att.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpenLightbox(att) }
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.AttachFile, contentDescription = null, tint = palette.primary, modifier = Modifier.size(14.dp))
                            Text(att.name, fontSize = 11.sp, color = palette.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        if (message.content.isNotBlank()) {
            Text(
                text = displayText,
                fontSize = (14f * scale).sp,
                lineHeight = (14f * scale * 1.5f).sp,
                color = palette.onSurface
            )
            if (needsTruncation) {
                Text(
                    text = if (expanded) "Show less" else "Show more",
                    fontSize = (12f * scale).sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.primary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable { expanded = !expanded }
                )
            }
        }
    }
}

@Composable
private fun AssistantMessageBody(
    content: String,
    onCopyCode: (String) -> Unit
) {
    val palette = LocalSaMuColors.current
    val scale = LocalFontScale.current
    val (thinking, mainText) = remember(content) { extractThinking(content) }
    var thinkExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!thinking.isNullOrBlank()) {
            Column {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { thinkExpanded = !thinkExpanded }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Thinking",
                        fontSize = (11f * scale).sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.primary
                    )
                    Icon(
                        imageVector = if (thinkExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                        contentDescription = "Toggle Thinking",
                        tint = palette.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (thinkExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.surfaceVariant.copy(alpha = 0.4f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = thinking,
                            fontSize = (9f * scale).sp,
                            lineHeight = (12f * scale).sp,
                            color = palette.textSecondary
                        )
                    }
                }
            }
        }

        if (mainText.isNotBlank()) {
            MarkdownText(markdown = mainText, onCopyCode = onCopyCode)
        }
    }
}

@Composable
private fun StreamingBubbleItem(
    text: String,
    onCopyCode: (String) -> Unit
) {
    val palette = LocalSaMuColors.current
    val shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 4.dp)

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .clip(shape)
                .background(palette.assistantBubble)
                .border(1.dp, palette.assistantBubbleBorder, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (text.isBlank()) {
                ThinkingDots()
            } else {
                AssistantMessageBody(content = text, onCopyCode = onCopyCode)
            }
        }
    }
}

@Composable
private fun ThinkingDots() {
    val palette = LocalSaMuColors.current
    val transition = rememberInfiniteTransition()
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        for (i in 0..2) {
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 200)
                )
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .alpha(alpha)
                    .background(palette.primary, CircleShape)
            )
        }
    }
}

@Composable
private fun MessageActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    val scale = LocalFontScale.current
    Row(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(13.dp))
        Text(label, fontSize = (11f * scale).sp, color = tint)
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    attachments: List<ChatAttachment>,
    isStreaming: Boolean,
    onPickFile: () -> Unit,
    onPickImage: () -> Unit,
    onRemoveAttachment: (ChatAttachment) -> Unit,
    onOpenLightbox: (ChatAttachment) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val palette = LocalSaMuColors.current
    val scale = LocalFontScale.current
    val canSend = (text.isNotBlank() || attachments.isNotEmpty()) && !isStreaming

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                attachments.forEach { att ->
                    Box(modifier = Modifier.size(56.dp)) {
                        if (att.isImage) {
                            val bmp = remember(att.path) { loadImageBitmapSafe(att.path) }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = att.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onOpenLightbox(att) }
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.surfaceVariant)
                                    .padding(4.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Filled.AttachFile, contentDescription = null, tint = palette.primary, modifier = Modifier.size(20.dp))
                                Text(
                                    att.name,
                                    fontSize = 9.sp,
                                    color = palette.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935))
                                .clickable { onRemoveAttachment(att) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(10.dp))
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(onClick = onPickFile, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.AttachFile, contentDescription = "Attach file", tint = palette.primary, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onPickImage, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Image, contentDescription = "Attach image", tint = palette.primary, modifier = Modifier.size(20.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 120.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(palette.background)
                    .border(1.dp, palette.surfaceVariant, RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "Ask anything...",
                        fontSize = (14f * scale).sp,
                        color = palette.textSecondary
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(
                        fontSize = (14f * scale).sp,
                        lineHeight = (14f * scale * 1.4f).sp,
                        color = palette.onSurface
                    ),
                    cursorBrush = SolidColor(palette.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                (event.key == Key.Enter || event.key == Key.NumPadEnter) &&
                                !event.isShiftPressed
                            ) {
                                if (canSend) onSend()
                                true
                            } else false
                        }
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isStreaming -> palette.error
                            canSend -> palette.primary
                            else -> palette.primary.copy(alpha = 0.4f)
                        }
                    )
                    .clickable(enabled = isStreaming || canSend) {
                        if (isStreaming) onStop() else onSend()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isStreaming) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (isStreaming) "Stop generation" else "Send message",
                    tint = Color.White,
                    modifier = Modifier.size(if (isStreaming) 18.dp else 20.dp)
                )
            }
        }
    }
}
