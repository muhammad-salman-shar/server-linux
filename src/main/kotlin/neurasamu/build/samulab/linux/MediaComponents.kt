package neurasamu.build.samulab.linux

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun samuScrollbarStyle(): ScrollbarStyle {
    val palette = LocalSaMuColors.current
    return defaultScrollbarStyle().copy(
        minimalHeight = 36.dp,
        thickness = 8.dp,
        shape = RoundedCornerShape(4.dp),
        unhoverColor = palette.textTertiary.copy(alpha = 0.45f),
        hoverColor = palette.primary.copy(alpha = 0.85f)
    )
}

@Composable
fun GeneratedMediaCard(
    media: GeneratedMedia,
    audioPlayer: InAppAudioPlayer,
    playingAudioPath: String?,
    onToggleAudio: (String) -> Unit,
    onOpenImageLightbox: (ChatAttachment) -> Unit,
    onDownload: (GeneratedMedia) -> Unit,
    onShare: (GeneratedMedia) -> Unit,
    onRegenerate: (GeneratedMedia) -> Unit
) {
    when (media.kind.lowercase()) {
        "image" -> GeneratedImageCard(
            media = media,
            onPreview = {
                val f = File(media.localPath)
                onOpenImageLightbox(
                    ChatAttachment(
                        path = media.localPath,
                        name = f.name,
                        isImage = true,
                        sizeBytes = if (f.exists()) f.length() else 0L,
                        kind = "image"
                    )
                )
            },
            onDownload = { onDownload(media) },
            onShare = { onShare(media) },
            onRegenerate = { onRegenerate(media) }
        )

        "audio" -> GeneratedAudioPlayerCard(
            media = media,
            audioPlayer = audioPlayer,
            isPlaying = playingAudioPath == media.localPath,
            onTogglePlay = { onToggleAudio(media.localPath) },
            onDownload = { onDownload(media) },
            onShare = { onShare(media) },
            onRegenerate = { onRegenerate(media) }
        )

        "video" -> GeneratedVideoCard(
            media = media,
            onPlay = { openFileExternally(File(media.localPath)) },
            onDownload = { onDownload(media) },
            onShare = { onShare(media) },
            onRegenerate = { onRegenerate(media) }
        )
    }
}

@Composable
private fun GeneratedImageCard(
    media: GeneratedMedia,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit
) {
    val palette = LocalSaMuColors.current
    val bmp = remember(media.localPath) { loadImageBitmapSafe(media.localPath) }

    Column(
        modifier = Modifier
            .widthIn(min = 240.dp, max = 380.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.background)
            .border(1.dp, palette.divider, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = media.prompt.ifBlank { "Generated Image" },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 320.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onPreview)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("Image file missing", color = palette.textSecondary, fontSize = 12.sp)
            }
        }
        MediaToolbar(
            label = File(media.localPath).name,
            onDownload = onDownload,
            onShare = onShare,
            onRegenerate = onRegenerate
        )
    }
}

@Composable
private fun GeneratedAudioPlayerCard(
    media: GeneratedMedia,
    audioPlayer: InAppAudioPlayer,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit
) {
    val palette = LocalSaMuColors.current
    var progress by remember(media.localPath) { mutableStateOf(0f) }
    var posSec by remember(media.localPath) { mutableStateOf(0.0) }
    var durSec by remember(media.localPath) { mutableStateOf(0.0) }

    LaunchedEffect(isPlaying, media.localPath) {
        while (isPlaying) {
            progress = audioPlayer.progressFraction()
            posSec = audioPlayer.positionSeconds()
            durSec = audioPlayer.durationSeconds()
            delay(100)
        }
        if (!isPlaying) {
            progress = 0f
            posSec = 0.0
        }
    }

    fun fmt(sec: Double): String {
        val total = sec.toInt().coerceAtLeast(0)
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
    }

    Column(
        modifier = Modifier
            .widthIn(min = 260.dp, max = 380.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.background)
            .border(1.dp, palette.divider, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(palette.primary)
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause Audio" else "Play Audio",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Audiotrack, contentDescription = null, tint = palette.primary, modifier = Modifier.size(14.dp))
                    Text(
                        text = File(media.localPath).name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Slider(
                    value = progress,
                    onValueChange = { frac ->
                        progress = frac
                        if (isPlaying) audioPlayer.seek(frac)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = palette.primary,
                        activeTrackColor = palette.primary
                    ),
                    modifier = Modifier.fillMaxWidth().height(26.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt(posSec), fontSize = 10.sp, color = palette.textTertiary)
                    Text(if (durSec > 0) fmt(durSec) else "Audio", fontSize = 10.sp, color = palette.textTertiary)
                }
            }
        }

        MediaToolbar(
            label = if (isPlaying) "Playing..." else "Ready",
            onDownload = onDownload,
            onShare = onShare,
            onRegenerate = onRegenerate
        )
    }
}

@Composable
private fun GeneratedVideoCard(
    media: GeneratedMedia,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit
) {
    val palette = LocalSaMuColors.current
    val file = remember(media.localPath) { File(media.localPath) }
    var posterBmp by remember(media.localPath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(media.localPath) {
        posterBmp = withContext(Dispatchers.IO) {
            extractVideoPoster(file)
        }
    }

    Column(
        modifier = Modifier
            .widthIn(min = 260.dp, max = 380.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.background)
            .border(1.dp, palette.divider, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0A0C10))
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            if (posterBmp != null) {
                Image(
                    bitmap = posterBmp!!,
                    contentDescription = "Video preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Movie, contentDescription = null, tint = palette.textTertiary, modifier = Modifier.size(36.dp))
                    Text(file.name, fontSize = 11.sp, color = palette.textSecondary)
                }
            }
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(palette.primary.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play Video", tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }

        val sizeMb = if (file.exists()) String.format(Locale.US, "%.1f MB", file.length() / (1024.0 * 1024.0)) else ""
        MediaToolbar(
            label = "${file.name}$sizeMb".trim(),
            onDownload = onDownload,
            onShare = onShare,
            onRegenerate = onRegenerate
        )
    }
}

private fun extractVideoPoster(videoFile: File): ImageBitmap? {
    if (!videoFile.isFile) return null
    val thumb = File(videoFile.parentFile, videoFile.nameWithoutExtension + "_thumb.jpg")
    if (!thumb.exists()) {
        runCatching {
            val proc = ProcessBuilder(
                "ffmpeg", "-y", "-i", videoFile.absolutePath,
                "-frames:v", "1", "-q:v", "3", thumb.absolutePath
            ).redirectErrorStream(true).start()
            proc.waitFor()
        }
    }
    return if (thumb.isFile) loadImageBitmapSafe(thumb.absolutePath) else null
}

@Composable
private fun MediaToolbar(
    label: String,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit
) {
    val palette = LocalSaMuColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = palette.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 6.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onDownload, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Download, contentDescription = "Download", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onShare, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Share, contentDescription = "Share", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onRegenerate, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = "Regenerate", tint = palette.primary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun ModelSettingsModal(
    modelFile: File,
    initialProfile: ModelProfile,
    onDismiss: () -> Unit,
    onSave: (ModelProfile) -> Unit
) {
    val palette = LocalSaMuColors.current
    val hw = remember { HardwareDetector.detect() }
    val scrollState = rememberScrollState()

    var kind by remember(initialProfile) { mutableStateOf(initialProfile.kind) }
    var mmprojPath by remember(initialProfile) { mutableStateOf(initialProfile.mmprojPath) }
    var extraFiles by remember(initialProfile) { mutableStateOf(initialProfile.extraFiles) }
    var selectedRole by remember { mutableStateOf("vae") }
    var gpuLayers by remember(initialProfile) { mutableStateOf(initialProfile.gpuLayers.toString()) }
    var thinkingMode by remember(initialProfile) { mutableStateOf(initialProfile.thinkingMode) }
    var customArgs by remember(initialProfile) { mutableStateOf(initialProfile.customArgs) }

    val extraRoles = listOf("vae", "clip", "t5", "lora", "voice_config", "vocoder", "other")

    ModalOverlay(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 380.dp, max = 560.dp)
                .fillMaxWidth(0.92f)
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surface)
                .border(1.dp, palette.divider, RoundedCornerShape(14.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Model Settings & Extra Files", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                    Text(modelFile.name, fontSize = 11.sp, color = palette.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = palette.textTertiary)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))

            Box(modifier = Modifier.weight(1f, fill = false).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(start = 20.dp, end = 26.dp, top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Hardware info pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.background)
                            .border(1.dp, palette.divider, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Memory, contentDescription = null, tint = palette.primary, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Device: ${hw.tierLabel} · Rec. GPU layers: ${hw.recommendedGpuLayers}",
                            fontSize = 12.sp,
                            color = palette.textSecondary
                        )
                    }

                    // Model Kind Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("MODEL TYPE", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ModelKind.entries.forEach { mk ->
                                val active = kind == mk
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) palette.primary else palette.background)
                                        .border(1.dp, if (active) palette.primary else palette.divider, RoundedCornerShape(8.dp))
                                        .clickable { kind = mk }
                                        .padding(horizontal = 10.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        mk.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (active) Color.White else palette.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // MMPROJ Picker (for Text/Vision models)
                    if (kind == ModelKind.TEXT_VISION) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("VISION / MULTIMODAL PROJECTOR (MMPROJ)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.background)
                                    .border(1.dp, palette.divider, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (mmprojPath.isBlank()) "None attached (add .gguf mmproj for image/vision analysis)" else File(mmprojPath).name,
                                    fontSize = 12.sp,
                                    color = if (mmprojPath.isBlank()) palette.textTertiary else palette.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (mmprojPath.isNotBlank()) {
                                    IconButton(onClick = { mmprojPath = "" }, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear mmproj", tint = palette.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        openFilePicker("Select mmproj GGUF File", setOf(".gguf"))?.let { f ->
                                            mmprojPath = f.absolutePath
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, palette.primary),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = palette.primary, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Pick", fontSize = 11.sp, color = palette.primary)
                                }
                            }
                        }

                        // Thinking Detection Override
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("THINKING / REASONING SUPPORT", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ThinkingSupportMode.entries.forEach { tm ->
                                    val active = thinkingMode == tm
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (active) palette.primary else palette.background)
                                            .border(1.dp, if (active) palette.primary else palette.divider, RoundedCornerShape(8.dp))
                                            .clickable { thinkingMode = tm }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            tm.label,
                                            fontSize = 12.sp,
                                            color = if (active) Color.White else palette.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        ModalField(
                            label = "GPU OFFLOAD LAYERS (-1 = AUTO, 0 = CPU ONLY, 99 = FULL GPU)",
                            value = gpuLayers,
                            onChange = { raw -> gpuLayers = raw.filter { it.isDigit() || it == '-' } },
                            placeholder = "-1"
                        )
                    }

                    // Extra Model Files (VAE, CLIP, T5, LoRA, Voice Config, Vocoder, etc.)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "EXTRA FILES (VAE / CLIP / T5 / LORA / VOICE CONFIG)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            extraRoles.forEach { role ->
                                val active = selectedRole == role
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (active) palette.primary.copy(alpha = 0.2f) else palette.background)
                                        .border(1.dp, if (active) palette.primary else palette.divider, RoundedCornerShape(6.dp))
                                        .clickable { selectedRole = role }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        role.uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (active) palette.primary else palette.textSecondary
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    openFilePicker("Attach ${selectedRole.uppercase()} File")?.let { f ->
                                        val next = extraFiles.filterNot { it.role == selectedRole && it.path == f.absolutePath } +
                                            ExtraModelFile(selectedRole, f.absolutePath)
                                        extraFiles = next
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Attach", fontSize = 11.sp, color = Color.White)
                            }
                        }

                        if (extraFiles.isEmpty()) {
                            Text("No extra files attached yet.", fontSize = 11.sp, color = palette.textTertiary)
                        } else {
                            extraFiles.forEach { ef ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(palette.background)
                                        .border(1.dp, palette.divider, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(palette.primary.copy(alpha = 0.16f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(ef.role.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.primary)
                                    }
                                    Text(
                                        File(ef.path).name,
                                        fontSize = 12.sp,
                                        color = palette.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { extraFiles = extraFiles.filterNot { it == ef } },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Remove file", tint = palette.error, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }

                    ModalField(
                        label = "CUSTOM ENGINE FLAGS (OPTIONAL)",
                        value = customArgs,
                        onChange = { customArgs = it },
                        placeholder = "e.g. --flash-attn --cache-type-k q8_0"
                    )
                }

                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    style = samuScrollbarStyle(),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp, top = 4.dp, bottom = 4.dp)
                )
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, palette.primary)
                ) {
                    Text("Cancel", color = palette.primary, fontSize = 13.sp)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(
                            initialProfile.copy(
                                modelPath = modelFile.absoluteFile.normalize().path,
                                kind = kind,
                                mmprojPath = mmprojPath.trim(),
                                extraFiles = extraFiles,
                                gpuLayers = gpuLayers.toIntOrNull() ?: -1,
                                thinkingMode = thinkingMode,
                                customArgs = customArgs.trim()
                            )
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
                ) {
                    Text("Save Model Config", color = palette.onPrimary, fontSize = 13.sp)
                }
            }
        }
    }
}
