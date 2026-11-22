package neurasamu.build.samulab.linux

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.skia.Image as SkiaImage
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

fun loadImageBitmapSafe(path: String): ImageBitmap? = runCatching {
    val file = File(path)
    if (!file.exists()) return null
    SkiaImage.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
}.getOrNull()

@Composable
internal fun ModalOverlay(
    onDismiss: () -> Unit,
    overlayAlpha: Float = 0.70f,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = overlayAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
        ) {
            content()
        }
    }
}

@Composable
internal fun ModalField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val palette = LocalSaMuColors.current
    Column {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
            color = palette.textSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder, color = palette.textTertiary, fontSize = 13.sp) },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 2,
            maxLines = if (singleLine) 1 else 4,
            trailingIcon = trailingIcon,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = palette.background,
                unfocusedContainerColor = palette.background,
                focusedBorderColor = palette.primary,
                unfocusedBorderColor = palette.divider,
                focusedTextColor = palette.onSurface,
                unfocusedTextColor = palette.onSurface
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ApiEditModal(
    initial: ApiConfig?,
    onDismiss: () -> Unit,
    onSave: (ApiConfig) -> Unit
) {
    val palette = LocalSaMuColors.current
    var label by remember(initial) { mutableStateOf(initial?.label ?: "") }
    var baseUrl by remember(initial) { mutableStateOf(initial?.baseUrl ?: "") }
    var modelName by remember(initial) { mutableStateOf(initial?.modelName ?: "") }
    var apiKey by remember(initial) { mutableStateOf(initial?.apiKey ?: "") }
    var systemPrompt by remember(initial) { mutableStateOf(initial?.systemPrompt ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    ModalOverlay(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 340.dp, max = 460.dp)
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
                Text(
                    text = if (initial == null) "Add API" else "Edit API",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onSurface
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = palette.textTertiary)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModalField("LABEL", label, { label = it; errorMsg = null }, "e.g. My SaMu Lab")
                ModalField("BASE URL", baseUrl, { baseUrl = it; errorMsg = null }, "http://192.168.1.5:8080/v1")
                ModalField("MODEL NAME", modelName, { modelName = it; errorMsg = null }, "samu")
                ModalField("API KEY (OPTIONAL)", apiKey, { apiKey = it }, "Leave empty if auth is off")
                ModalField(
                    "SYSTEM PROMPT (OPTIONAL)",
                    systemPrompt,
                    { systemPrompt = it },
                    "You are a helpful assistant.",
                    singleLine = false
                )
                errorMsg?.let {
                    Text(it, color = palette.error, fontSize = 12.sp)
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
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
                        val cleanedUrl = sanitizeApiUrl(baseUrl)
                        if (label.isBlank() || cleanedUrl.isBlank() || modelName.isBlank()) {
                            errorMsg = "Label, Base URL, and Model Name are required."
                            return@Button
                        }
                        if (!cleanedUrl.startsWith("http://") && !cleanedUrl.startsWith("https://")) {
                            errorMsg = "Base URL must start with http:// or https://"
                            return@Button
                        }
                        val updated = (initial ?: ApiConfig(label = "", baseUrl = "", modelName = "")).copy(
                            label = label.trim(),
                            baseUrl = cleanedUrl,
                            modelName = modelName.trim(),
                            apiKey = apiKey.trim(),
                            systemPrompt = systemPrompt.trim(),
                            isBuiltInLocal = false
                        )
                        onSave(updated)
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
                ) {
                    Text("Save API", color = palette.onPrimary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun SelectTextModal(
    text: String,
    onDismiss: () -> Unit,
    onCopyAll: () -> Unit
) {
    val palette = LocalSaMuColors.current

    ModalOverlay(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 620.dp)
                .fillMaxWidth(0.9f)
                .heightIn(max = 560.dp)
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
                Text("Select Text", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = palette.textTertiary)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(18.dp)
            ) {
                Text(
                    "Highlight any part of the text below and press Ctrl+C to copy.",
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = {},
                    readOnly = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.background,
                        unfocusedContainerColor = palette.background,
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.divider,
                        focusedTextColor = palette.onSurface,
                        unfocusedTextColor = palette.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 380.dp)
                )
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, palette.divider)
                ) {
                    Text("Close", color = palette.onSurface, fontSize = 13.sp)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onCopyAll()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = palette.onPrimary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy All", color = palette.onPrimary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun ChatSettingsModal(
    prefs: ChatPrefs,
    onDismiss: () -> Unit,
    onSave: (ChatPrefs) -> Unit
) {
    val palette = LocalSaMuColors.current
    var theme by remember(prefs) { mutableStateOf(prefs.themeMode) }
    var fontScale by remember(prefs) { mutableStateOf(prefs.fontScale) }
    var instrEnabled by remember(prefs) { mutableStateOf(prefs.customInstructionsEnabled) }
    var instructions by remember(prefs) { mutableStateOf(prefs.customInstructions) }

    ModalOverlay(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 340.dp, max = 480.dp)
                .fillMaxWidth(0.92f)
                .heightIn(max = 640.dp)
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
                Text("Chat Settings", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = palette.textTertiary)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "THEME",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        color = palette.textSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { theme = mode }
                                    .padding(end = 8.dp)
                            ) {
                                RadioButton(
                                    selected = theme == mode,
                                    onClick = { theme = mode },
                                    colors = RadioButtonDefaults.colors(selectedColor = palette.primary)
                                )
                                Text(mode.label, fontSize = 13.sp, color = palette.onSurface)
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "FONT SIZE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        color = palette.textSecondary
                    )
                    Text(
                        text = "Text size: ${String.format(Locale.US, "%.1f", fontScale)}x",
                        fontSize = (14f * fontScale).sp,
                        color = palette.onSurface
                    )
                    Slider(
                        value = fontScale,
                        onValueChange = { raw ->
                            fontScale = ((raw * 10f).roundToInt() / 10f).coerceIn(0.8f, 1.4f)
                        },
                        valueRange = 0.8f..1.4f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = palette.primary,
                            activeTrackColor = palette.primary
                        )
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "CUSTOM INSTRUCTIONS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
                            color = palette.textSecondary
                        )
                        Switch(checked = instrEnabled, onCheckedChange = { instrEnabled = it })
                    }
                    OutlinedTextField(
                        value = instructions,
                        onValueChange = { instructions = it },
                        placeholder = {
                            Text(
                                "e.g. You are a helpful assistant. Reply in Urdu.",
                                color = palette.textTertiary,
                                fontSize = 13.sp
                            )
                        },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = palette.background,
                            unfocusedContainerColor = palette.background,
                            focusedBorderColor = palette.primary,
                            unfocusedBorderColor = palette.divider,
                            focusedTextColor = palette.onSurface,
                            unfocusedTextColor = palette.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Applied to all APIs. Sent as system prompt before every request.",
                        fontSize = 11.sp,
                        color = palette.textTertiary
                    )
                }
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
                            prefs.copy(
                                themeMode = theme,
                                fontScale = fontScale,
                                customInstructionsEnabled = instrEnabled,
                                customInstructions = instructions.trim()
                            )
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
                ) {
                    Text("Save", color = palette.onPrimary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun ImageLightboxModal(
    attachment: ChatAttachment,
    onDismiss: () -> Unit
) {
    val bmp = remember(attachment.path) { loadImageBitmapSafe(attachment.path) }
    val sizeKb = (attachment.sizeBytes / 1024L).coerceAtLeast(1L)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = attachment.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize(0.88f)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Text("Image unavailable", color = Color.White, fontSize = 14.sp)
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
        }

        Text(
            text = "${attachment.name} (${sizeKb} KB)",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun RenameChatModal(
    currentTitle: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val palette = LocalSaMuColors.current
    var text by remember(currentTitle) { mutableStateOf(currentTitle) }

    ModalOverlay(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 300.dp, max = 400.dp)
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surface)
                .border(1.dp, palette.divider, RoundedCornerShape(14.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Rename Chat", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = palette.background,
                    unfocusedContainerColor = palette.background,
                    focusedBorderColor = palette.primary,
                    unfocusedBorderColor = palette.divider,
                    focusedTextColor = palette.onSurface,
                    unfocusedTextColor = palette.onSurface
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, palette.primary)
                ) {
                    Text("Cancel", color = palette.primary)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { if (text.isNotBlank()) onSave(text.trim()) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
                ) {
                    Text("Save", color = palette.onPrimary)
                }
            }
        }
    }
}

@Composable
fun ConfirmDeleteModal(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val palette = LocalSaMuColors.current
    ModalOverlay(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 380.dp)
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surface)
                .border(1.dp, palette.divider, RoundedCornerShape(14.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
            Text(message, fontSize = 13.sp, color = palette.textSecondary)
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, palette.divider)
                ) {
                    Text("Cancel", color = palette.onSurface)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.error)
                ) {
                    Text("Delete", color = Color.White)
                }
            }
        }
    }
}
