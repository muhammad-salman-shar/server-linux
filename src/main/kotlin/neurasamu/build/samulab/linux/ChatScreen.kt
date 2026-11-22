package neurasamu.build.samulab.linux

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
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
    contextSize: Int,
    supportsThinking: Boolean,
    thinkingEnabled: Boolean,
    thinkingEffort: ThinkingEffort,
    chatMode: ChatMode,
    onChatModeChange: (ChatMode) -> Unit,
    onSelectThinkingEffort: (ThinkingEffort) -> Unit,
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
    audioPlayer: InAppAudioPlayer,
    playingAudioPath: String?,
    onToggleAudio: (String) -> Unit,
    onDownloadMedia: (GeneratedMedia) -> Unit,
    onShareMedia: (GeneratedMedia) -> Unit,
    onRegenerateMedia: (GeneratedMedia) -> Unit,
    onSelectApi: (ApiConfig) -> Unit,
    onOpenAddApi: () -> Unit,
    onOpenEditApi: (ApiConfig) -> Unit,
    onRequestDeleteApi: (ApiConfig) -> Unit,
    onNewChat: () -> Unit,
    onSelectChat: (ChatSession) -> Unit,
    onRequestRenameChat: (ChatSession) -> Unit,
    onDownloadChat: (ChatSession) -> Unit,
    onRequestDeleteChat: (ChatSession) -> Unit,
    onRequestDeleteAllChats: () -> Unit,
    onOpenChatSettings: () -> Unit,
    onSendMessage: () -> Unit,
    onStopStreaming: () -> Unit,
    onEditMessage: (ChatMessage) -> Unit,
    onRegenerateMessage: (ChatMessage) -> Unit,
    onSelectTextMessage: (ChatMessage) -> Unit,
    onShareMessage: (ChatMessage) -> Unit,
    onToggleReadAloud: (ChatMessage) -> Unit,
    onOpenLightbox: (ChatAttachment) -> Unit,
    onCopyToast: (String) -> Unit,
    onSwitchToServerTab: () -> Unit
) {
    val palette = LocalSaMuColors.current
    val scale = LocalFontScale.current
    val scope = rememberCoroutineScope()
    var desktopSidebarOpen by remember { mutableStateOf(true) }
    var mobileDrawerOpen by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(palette.background)) {
        val isCompact = maxWidth < 768.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (!isCompact) {
                AnimatedVisibility(
                    visible = desktopSidebarOpen,
                    enter = expandHorizontally(tween(220)) + fadeIn(tween(180)),
                    exit = shrinkHorizontally(tween(220)) + fadeOut(tween(180))
                ) {
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
                        onDeleteChat = onRequestDeleteChat,
                        onDeleteAllChats = onRequestDeleteAllChats
                    )
                }
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Top Bar (56dp): [☰] [Thinking Dropdown on Top-Left] [Title] [Tokens] [Settings] [+ Chat]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(palette.surface)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (isCompact) mobileDrawerOpen = !mobileDrawerOpen
                            else desktopSidebarOpen = !desktopSidebarOpen
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = "Toggle Sidebar",
                            tint = palette.onSurface,
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    // Top-Left Collapsible Thinking Dropdown (auto-shown only when model supports thinking)
                    if (supportsThinking && chatMode == ChatMode.CHAT) {
                        ThinkingTopDropdown(
                            thinkingEnabled = thinkingEnabled,
                            thinkingEffort = thinkingEffort,
                            onSelect = onSelectThinkingEffort
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = EmojiSupport.format(activeChat.title),
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

                    val liveTokens = activeChat.estimatedTokens +
                        if (isStreaming && streamingText.isNotEmpty()) (streamingText.length / 4).coerceAtLeast(1) else 0
                    val ctxLabel = if (contextSize <= 0) "$liveTokens / Max" else "$liveTokens/$contextSize"

                    Box(
                        modifier = Modifier
                            .height(26.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(palette.primary.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ctxLabel,
                            fontSize = (11f * scale).sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.primary
                        )
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

                    IconButton(
                        onClick = onNewChat,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "New Chat",
                            tint = palette.onSurface,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))

                // Message List with rock-solid manual-scroll-friendly auto-scroll + Vertical Scrollbar
                val listState = rememberLazyListState()
                var autoScrollEnabled by remember(activeChat.id) { mutableStateOf(true) }
                var prevIdx by remember(activeChat.id) { mutableStateOf(0) }
                var prevOff by remember(activeChat.id) { mutableStateOf(0) }
                var programmaticScroll by remember(activeChat.id) { mutableStateOf(false) }

                // Re-enable auto-scroll whenever user sends a new message
                LaunchedEffect(activeChat.messages.lastOrNull()?.id) {
                    if (activeChat.messages.lastOrNull()?.role == "user") {
                        autoScrollEnabled = true
                    }
                }

                // Respect manual scrolling immediately without ever fighting the user
                LaunchedEffect(listState, activeChat.id) {
                    snapshotFlow {
                        Triple(
                            listState.isScrollInProgress,
                            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset,
                            listState.canScrollForward
                        )
                    }.collect { (scrolling, pos, canForward) ->
                        val (idx, off) = pos
                        if (scrolling && !programmaticScroll) {
                            val scrolledUp = idx < prevIdx || (idx == prevIdx && off < prevOff)
                            if (scrolledUp) {
                                autoScrollEnabled = false
                            } else if (!canForward) {
                                autoScrollEnabled = true
                            }
                        } else if (!scrolling && !canForward) {
                            autoScrollEnabled = true
                        }
                        prevIdx = idx
                        prevOff = off
                    }
                }

                // Target the 1dp bottom anchor item so tall streaming bubbles never jump to their top!
                val totalListItems = activeChat.messages.size + (if (isStreaming) 1 else 0) + 1
                LaunchedEffect(activeChat.id, activeChat.messages.size, streamingText.length, isStreaming, autoScrollEnabled) {
                    if (autoScrollEnabled && (activeChat.messages.isNotEmpty() || isStreaming)) {
                        programmaticScroll = true
                        try {
                            listState.scrollToItem((totalListItems - 1).coerceAtLeast(0))
                        } catch (_: Exception) {
                        } finally {
                            programmaticScroll = false
                        }
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
                                text = "Ready to create & chat",
                                fontSize = (16f * scale).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            val emptySub = when {
                                activeApi == null -> "Add an API using + API in the sidebar first."
                                chatMode == ChatMode.IMAGE -> "Describe an image (and optionally attach a reference image) to generate."
                                chatMode == ChatMode.AUDIO -> "Type text below to synthesize natural speech audio."
                                chatMode == ChatMode.VIDEO -> "Describe a video scene (and optionally attach a reference image) to generate."
                                activeApi.isBuiltInLocal && !localServerRunning ->
                                    "Your local SaMu Lab server is currently stopped. Start it in the Server tab or switch modes below."
                                else -> "Attach PDFs, Docs, Code, Images, Audio or just type below."
                            }
                            Text(
                                text = emptySub,
                                fontSize = (13f * scale).sp,
                                color = palette.textSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = (13f * scale * 1.5f).sp,
                                modifier = Modifier.widthIn(max = 360.dp)
                            )
                            if (chatMode == ChatMode.CHAT && activeApi?.isBuiltInLocal == true && !localServerRunning) {
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
                            modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(activeChat.messages, key = { it.id }) { msg ->
                                MessageBubbleItem(
                                    message = msg,
                                    isSpeaking = speakingMsgId == msg.id,
                                    audioPlayer = audioPlayer,
                                    playingAudioPath = playingAudioPath,
                                    onToggleAudio = onToggleAudio,
                                    onDownloadMedia = onDownloadMedia,
                                    onShareMedia = onShareMedia,
                                    onRegenerateMedia = onRegenerateMedia,
                                    onSelectText = { onSelectTextMessage(msg) },
                                    onCopy = {
                                        if (copyToClipboard(msg.content)) onCopyToast("Copied!")
                                    },
                                    onShare = { onShareMessage(msg) },
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

                            // 1dp anchor ensures scrollToItem always snaps to the true bottom of tall messages
                            item(key = "__bottom_anchor__") {
                                Spacer(Modifier.height(1.dp))
                            }
                        }

                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(listState),
                            style = samuScrollbarStyle(),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(end = 3.dp, top = 4.dp, bottom = 4.dp)
                        )

                        if (!autoScrollEnabled && (activeChat.messages.isNotEmpty() || isStreaming)) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 22.dp, bottom = 16.dp)
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(palette.primary)
                                    .clickable {
                                        autoScrollEnabled = true
                                        scope.launch {
                                            programmaticScroll = true
                                            try {
                                                listState.animateScrollToItem((totalListItems - 1).coerceAtLeast(0))
                                            } catch (_: Exception) {
                                            } finally {
                                                programmaticScroll = false
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

                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
                ChatInputBar(
                    text = inputText,
                    onTextChange = onInputChange,
                    attachments = attachments,
                    isStreaming = isStreaming,
                    chatMode = chatMode,
                    onChatModeChange = onChatModeChange,
                    onPickFile = {
                        openFilePicker("Attach Any File (PDF, Doc, MD, Code, Audio, Image)")?.let { f ->
                            val kind = FileAnalyzer.classify(f)
                            onAddAttachment(
                                ChatAttachment(
                                    path = f.absolutePath,
                                    name = f.name,
                                    isImage = kind == AttachmentKind.IMAGE,
                                    sizeBytes = f.length(),
                                    kind = kind.name.lowercase()
                                )
                            )
                        }
                    },
                    onPickImage = {
                        val title = when (chatMode) {
                            ChatMode.IMAGE -> "Attach Reference Image for Image Generation"
                            ChatMode.VIDEO -> "Attach Reference Image for Video Generation"
                            else -> "Attach Image"
                        }
                        openFilePicker(title, setOf(".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp"))?.let { f ->
                            onAddAttachment(ChatAttachment(f.absolutePath, f.name, true, f.length(), "image"))
                        }
                    },
                    onRemoveAttachment = onRemoveAttachment,
                    onOpenLightbox = onOpenLightbox,
                    onSend = onSendMessage,
                    onStop = onStopStreaming
                )
            }
        }

        if (isCompact) {
            AnimatedVisibility(
                visible = mobileDrawerOpen,
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
                            onClick = { mobileDrawerOpen = false }
                        )
                )
            }
            AnimatedVisibility(
                visible = mobileDrawerOpen,
                enter = slideInHorizontally(tween(250)) { -it },
                exit = slideOutHorizontally(tween(250)) { -it }
            ) {
                ChatSidebar(
                    allApis = allApis,
                    activeApiId = activeApi?.id,
                    localServerRunning = localServerRunning,
                    chats = chats,
                    activeChatId = activeChat.id,
                    onOpenAddApi = { mobileDrawerOpen = false; onOpenAddApi() },
                    onNewChat = { mobileDrawerOpen = false; onNewChat() },
                    onSelectApi = { onSelectApi(it); mobileDrawerOpen = false },
                    onEditApi = { mobileDrawerOpen = false; onOpenEditApi(it) },
                    onDeleteApi = { mobileDrawerOpen = false; onRequestDeleteApi(it) },
                    onSelectChat = { onSelectChat(it); mobileDrawerOpen = false },
                    onRenameChat = { mobileDrawerOpen = false; onRequestRenameChat(it) },
                    onDownloadChat = { mobileDrawerOpen = false; onDownloadChat(it) },
                    onDeleteChat = { mobileDrawerOpen = false; onRequestDeleteChat(it) },
                    onDeleteAllChats = { mobileDrawerOpen = false; onRequestDeleteAllChats() }
                )
            }
        }
    }
}

@Composable
private fun ThinkingTopDropdown(
    thinkingEnabled: Boolean,
    thinkingEffort: ThinkingEffort,
    onSelect: (ThinkingEffort) -> Unit
) {
    val palette = LocalSaMuColors.current
    var expanded by remember { mutableStateOf(false) }
    val activeEffort = if (thinkingEnabled) thinkingEffort else ThinkingEffort.OFF

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (activeEffort != ThinkingEffort.OFF) palette.primary.copy(alpha = 0.16f) else palette.background)
                .border(
                    1.dp,
                    if (activeEffort != ThinkingEffort.OFF) palette.primary else palette.divider,
                    RoundedCornerShape(16.dp)
                )
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Filled.Psychology,
                contentDescription = "Thinking Mode",
                tint = if (activeEffort != ThinkingEffort.OFF) palette.primary else palette.textSecondary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "Think: ${activeEffort.label}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (activeEffort != ThinkingEffort.OFF) palette.primary else palette.textSecondary
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = if (activeEffort != ThinkingEffort.OFF) palette.primary else palette.textSecondary,
                modifier = Modifier.size(14.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 165.dp)
                .background(palette.surface, RoundedCornerShape(10.dp))
                .border(1.dp, palette.divider, RoundedCornerShape(10.dp))
        ) {
            val options = listOf(
                Triple(ThinkingEffort.OFF, "Off (Skip)", "Direct fast answer"),
                Triple(ThinkingEffort.LOW, "Low", "Brief reasoning"),
                Triple(ThinkingEffort.HIGH, "High", "Balanced reasoning"),
                Triple(ThinkingEffort.MAX, "Max", "Deep full reasoning")
            )
            options.forEach { (eff, title, sub) ->
                val isSelected = activeEffort == eff
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                title,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) palette.primary else palette.onSurface
                            )
                            Text(sub, fontSize = 10.sp, color = palette.textTertiary)
                        }
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = palette.primary, modifier = Modifier.size(16.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(eff)
                    }
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
    onDeleteChat: (ChatSession) -> Unit,
    onDeleteAllChats: () -> Unit
) {
    val palette = LocalSaMuColors.current
    val scale = LocalFontScale.current
    val sidebarScroll = rememberScrollState()

    Row(modifier = Modifier.width(280.dp).fillMaxHeight()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(palette.surface)
        ) {
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

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(sidebarScroll)
                        .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 12.dp)
                ) {
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

                    // Chats Header + Delete All Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "CHATS (${chats.size})",
                            fontSize = (10f * scale).sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
                            color = palette.textSecondary
                        )
                        if (chats.size > 1 || chats.firstOrNull()?.messages?.isNotEmpty() == true) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(onClick = onDeleteAllChats)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete All Chats", tint = palette.error, modifier = Modifier.size(12.dp))
                                Text("Delete All", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = palette.error)
                            }
                        }
                    }

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
                                        text = EmojiSupport.format(chat.title),
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
                                        modifier = Modifier
                                            .widthIn(min = 165.dp)
                                            .background(palette.surface, RoundedCornerShape(8.dp))
                                            .border(1.dp, palette.divider, RoundedCornerShape(8.dp))
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

                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(sidebarScroll),
                    style = samuScrollbarStyle(),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 2.dp, top = 4.dp, bottom = 4.dp)
                )
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(palette.divider))
    }
}

private data class ParsedThinking(
    val thinking: String?,
    val isStillThinking: Boolean,
    val mainText: String
)

private fun extractThinking(raw: String): ParsedThinking {
    val startIdx = raw.indexOf("<think>")
    if (startIdx < 0) return ParsedThinking(null, false, raw)
    val endIdx = raw.indexOf("</think>", startIdx + 7)
    return if (endIdx >= 0) {
        val think = raw.substring(startIdx + 7, endIdx).trim()
        val main = (raw.substring(0, startIdx) + raw.substring(endIdx + 8)).trim()
        ParsedThinking(think, false, main)
    } else {
        val think = raw.substring(startIdx + 7).trim()
        val main = raw.substring(0, startIdx).trim()
        ParsedThinking(think, true, main)
    }
}

@Composable
private fun MessageBubbleItem(
    message: ChatMessage,
    isSpeaking: Boolean,
    audioPlayer: InAppAudioPlayer,
    playingAudioPath: String?,
    onToggleAudio: (String) -> Unit,
    onDownloadMedia: (GeneratedMedia) -> Unit,
    onShareMedia: (GeneratedMedia) -> Unit,
    onRegenerateMedia: (GeneratedMedia) -> Unit,
    onSelectText: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
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

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val bubbleMaxWidth = (maxWidth * 0.85f).coerceAtMost(720.dp)

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Column(
                modifier = Modifier.widthIn(max = bubbleMaxWidth),
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
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
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (message.content.isNotBlank()) {
                                AssistantMessageBody(
                                    content = message.content,
                                    isStreaming = false,
                                    onCopyCode = onCopyCode
                                )
                            }
                            message.media.forEach { med ->
                                GeneratedMediaCard(
                                    media = med,
                                    audioPlayer = audioPlayer,
                                    playingAudioPath = playingAudioPath,
                                    onToggleAudio = onToggleAudio,
                                    onOpenImageLightbox = onOpenLightbox,
                                    onDownload = onDownloadMedia,
                                    onShare = onShareMedia,
                                    onRegenerate = onRegenerateMedia
                                )
                            }
                        }
                    }
                }

                if (!message.isError) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.content.isNotBlank()) {
                            MessageActionIcon(
                                icon = Icons.Filled.SelectAll,
                                description = "Select Text",
                                tint = palette.textSecondary,
                                onClick = onSelectText
                            )
                            MessageActionIcon(
                                icon = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                description = if (copied) "Copied" else "Copy",
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
                            MessageActionIcon(
                                icon = Icons.Filled.Share,
                                description = "Share",
                                tint = palette.textSecondary,
                                onClick = onShare
                            )
                        }
                        if (isUser) {
                            MessageActionIcon(
                                icon = Icons.Filled.Edit,
                                description = "Edit",
                                tint = palette.textSecondary,
                                onClick = onEdit
                            )
                        } else {
                            MessageActionIcon(
                                icon = Icons.Filled.Refresh,
                                description = "Regenerate",
                                tint = palette.textSecondary,
                                onClick = onRegenerate
                            )
                            if (message.content.isNotBlank()) {
                                MessageActionIcon(
                                    icon = if (isSpeaking) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                                    description = if (isSpeaking) "Stop Reading" else "Read Aloud",
                                    tint = if (isSpeaking) palette.primary else palette.textSecondary,
                                    onClick = onReadAloud
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageActionIcon(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
    }
}

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
            val attScroll = rememberScrollState()
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(attScroll)
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
                            val icon = when (att.kind.lowercase()) {
                                "pdf", "document" -> Icons.Filled.Description
                                "audio" -> Icons.Filled.Audiotrack
                                "video" -> Icons.Filled.Movie
                                "code_text" -> Icons.Filled.Code
                                else -> Icons.Filled.AttachFile
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(palette.surfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(icon, contentDescription = null, tint = palette.primary, modifier = Modifier.size(14.dp))
                                Text(att.name, fontSize = 11.sp, color = palette.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (attScroll.maxValue > 0) {
                    HorizontalScrollbar(
                        adapter = rememberScrollbarAdapter(attScroll),
                        style = samuScrollbarStyle(),
                        modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
                    )
                }
            }
        }

        if (message.content.isNotBlank()) {
            Text(
                text = EmojiSupport.format(displayText),
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
    isStreaming: Boolean,
    onCopyCode: (String) -> Unit
) {
    val palette = LocalSaMuColors.current
    val scale = LocalFontScale.current
    val parsed = remember(content) { extractThinking(content) }
    val thinking = parsed.thinking
    val isStillThinking = parsed.isStillThinking
    val mainText = parsed.mainText

    var thinkExpanded by remember { mutableStateOf(isStreaming && isStillThinking) }
    var userToggledDuringThink by remember { mutableStateOf(false) }

    LaunchedEffect(isStreaming, isStillThinking) {
        if (isStreaming && isStillThinking && !userToggledDuringThink) {
            thinkExpanded = true
        } else if (!isStillThinking) {
            thinkExpanded = false
            userToggledDuringThink = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!thinking.isNullOrBlank()) {
            val thinkScroll = rememberScrollState()
            Column {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.surfaceVariant.copy(alpha = 0.45f))
                        .clickable {
                            thinkExpanded = !thinkExpanded
                            if (isStreaming && isStillThinking) userToggledDuringThink = true
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        Icons.Filled.Psychology,
                        contentDescription = null,
                        tint = palette.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = when {
                            isStreaming && isStillThinking -> if (thinkExpanded) "Thinking... (Hide)" else "Thinking... (Show)"
                            thinkExpanded -> "Hide Thinking"
                            else -> "Show Thinking"
                        },
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
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.surfaceVariant.copy(alpha = 0.35f))
                            .border(1.dp, palette.divider, RoundedCornerShape(8.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(thinkScroll)
                                .padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                        ) {
                            Text(
                                text = EmojiSupport.format(thinking),
                                fontSize = (10.5f * scale).sp,
                                lineHeight = (14.5f * scale).sp,
                                color = palette.textSecondary
                            )
                        }
                        if (thinkScroll.maxValue > 0) {
                            VerticalScrollbar(
                                adapter = rememberScrollbarAdapter(thinkScroll),
                                style = samuScrollbarStyle(),
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(2.dp)
                            )
                        }
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

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val bubbleMaxWidth = (maxWidth * 0.85f).coerceAtMost(720.dp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Box(
                modifier = Modifier
                    .widthIn(max = bubbleMaxWidth)
                    .clip(shape)
                    .background(palette.assistantBubble)
                    .border(1.dp, palette.assistantBubbleBorder, shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (text.isBlank()) {
                    ThinkingDots()
                } else {
                    AssistantMessageBody(content = text, isStreaming = true, onCopyCode = onCopyCode)
                }
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
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    attachments: List<ChatAttachment>,
    isStreaming: Boolean,
    chatMode: ChatMode,
    onChatModeChange: (ChatMode) -> Unit,
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
    var modeMenuOpen by remember { mutableStateOf(false) }

    val modeMeta = when (chatMode) {
        ChatMode.CHAT -> Icons.Filled.Forum to "Chat"
        ChatMode.IMAGE -> Icons.Filled.Image to "Image"
        ChatMode.AUDIO -> Icons.Filled.Audiotrack to "Audio"
        ChatMode.VIDEO -> Icons.Filled.Movie to "Video"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (attachments.isNotEmpty()) {
            val attScroll = rememberScrollState()
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(attScroll)
                        .padding(bottom = 2.dp),
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
                                val icon = when (att.kind.lowercase()) {
                                    "pdf", "document" -> Icons.Filled.Description
                                    "audio" -> Icons.Filled.Audiotrack
                                    "video" -> Icons.Filled.Movie
                                    "code_text" -> Icons.Filled.Code
                                    else -> Icons.Filled.AttachFile
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(palette.surfaceVariant)
                                        .padding(4.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(icon, contentDescription = null, tint = palette.primary, modifier = Modifier.size(20.dp))
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
                if (attScroll.maxValue > 0) {
                    HorizontalScrollbar(
                        adapter = rememberScrollbarAdapter(attScroll),
                        style = samuScrollbarStyle(),
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Single Compact Mode Dropdown (Chat / Image / Audio / Video)
            Box {
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(palette.primary.copy(alpha = 0.14f))
                        .border(1.dp, palette.primary.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .clickable { modeMenuOpen = true }
                        .padding(horizontal = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(modeMeta.first, contentDescription = modeMeta.second, tint = palette.primary, modifier = Modifier.size(16.dp))
                    Text(modeMeta.second, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.primary)
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Switch Mode", tint = palette.primary, modifier = Modifier.size(15.dp))
                }

                DropdownMenu(
                    expanded = modeMenuOpen,
                    onDismissRequest = { modeMenuOpen = false },
                    modifier = Modifier
                        .widthIn(min = 180.dp)
                        .background(palette.surface, RoundedCornerShape(10.dp))
                        .border(1.dp, palette.divider, RoundedCornerShape(10.dp))
                ) {
                    val modes = listOf(
                        Triple(ChatMode.CHAT, Icons.Filled.Forum, "Text, Vision & Files"),
                        Triple(ChatMode.IMAGE, Icons.Filled.Image, "Generate Image (+ Ref)"),
                        Triple(ChatMode.AUDIO, Icons.Filled.Audiotrack, "Text to Speech Audio"),
                        Triple(ChatMode.VIDEO, Icons.Filled.Movie, "Generate Video (+ Ref)")
                    )
                    modes.forEach { (m, icon, sub) ->
                        val active = chatMode == m
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        m.label,
                                        fontSize = 13.sp,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (active) palette.primary else palette.onSurface
                                    )
                                    Text(sub, fontSize = 10.sp, color = palette.textTertiary)
                                }
                            },
                            leadingIcon = {
                                Icon(icon, contentDescription = null, tint = if (active) palette.primary else palette.textSecondary, modifier = Modifier.size(17.dp))
                            },
                            trailingIcon = {
                                if (active) Icon(Icons.Filled.Check, contentDescription = null, tint = palette.primary, modifier = Modifier.size(16.dp))
                            },
                            onClick = {
                                modeMenuOpen = false
                                onChatModeChange(m)
                            }
                        )
                    }
                }
            }

            // Attachment Icons: Chat -> File + Image | Image & Video -> Reference Image
            if (chatMode == ChatMode.CHAT) {
                IconButton(onClick = onPickFile, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Filled.AttachFile, contentDescription = "Attach any file", tint = palette.primary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onPickImage, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Filled.Image, contentDescription = "Attach image", tint = palette.primary, modifier = Modifier.size(20.dp))
                }
            } else if (chatMode == ChatMode.IMAGE || chatMode == ChatMode.VIDEO) {
                IconButton(onClick = onPickImage, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Filled.Image, contentDescription = "Attach reference image", tint = palette.primary, modifier = Modifier.size(20.dp))
                }
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
                    val hint = when (chatMode) {
                        ChatMode.CHAT -> "Ask anything or attach PDF, code, image, audio..."
                        ChatMode.IMAGE -> "Describe image (optional: attach ref image)..."
                        ChatMode.AUDIO -> "Type text to convert to speech audio..."
                        ChatMode.VIDEO -> "Describe video (optional: attach ref image)..."
                    }
                    Text(
                        text = hint,
                        fontSize = (14f * scale).sp,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
