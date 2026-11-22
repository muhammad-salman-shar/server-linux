package neurasamu.build.samulab.linux

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@Composable
fun QuickGuideModal(onDismiss: () -> Unit) {
    val palette = LocalSaMuColors.current
    val scrollState = rememberScrollState()

    val guideItems = remember {
        listOf(
            "Chat / Server Tabs (Top Center)" to "Switch between Chat and Server anytime — both stay alive in parallel.",
            "Mode Dropdown (Chat / Image / Audio / Video)" to "Pick one mode inside the input bar; attach reference images in Image or Video mode too.",
            "Thinking Dropdown (Top-Left of Chat)" to "Appears automatically on thinking models — choose Off (Skip), Low, High, or Max.",
            "Universal File Attach (Clip & Image Icons)" to "Attach PDF, DOCX, XLSX, PPTX, MD, Code, Images, or Audio for instant analysis.",
            "Model Settings Gear (Tune Icon per Model)" to "Set model type (LLM, Image, Audio, Video), attach mmproj/VAE/CLIP/T5/LoRA, and GPU layers.",
            "Auto Context Window" to "Reads each GGUF model's exact max context automatically on load so it never crashes; lower it anytime.",
            "This Device vs Other Device URL" to "Use localhost for apps on this PC, or switch to Other Device for phone Wi-Fi, USB, or Tunnel guides.",
            "Custom API Key (Server Settings)" to "Protect your server with your own key (min 8 chars) or generate a random one.",
            "Real-Time Monitor & Scrollbars" to "Track live tok/s, active requests, and drag vertical/horizontal sliders easily on laptop touchpads.",
            "Desktop Shortcuts" to "Ctrl+N New Chat · Ctrl+, Settings · Ctrl+E Edit Last · Ctrl+R Regenerate · Ctrl+Shift+K Clear."
        )
    }

    ModalOverlay(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 380.dp, max = 560.dp)
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
                Column {
                    Text("SaMu Lab Quick Guide", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                    Text("Every feature explained in one line", fontSize = 11.sp, color = palette.primary)
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
                        .padding(start = 18.dp, end = 24.dp, top = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    guideItems.forEach { (title, desc) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.background)
                                .border(1.dp, palette.divider, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.primary)
                            Text(desc, fontSize = 12.sp, color = palette.onSurface, lineHeight = 17.sp)
                        }
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    style = samuScrollbarStyle(),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp, top = 4.dp, bottom = 4.dp)
                )
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
                ) {
                    Text("Got it", color = palette.onPrimary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun ConnectOtherDeviceModal(
    localIp: String,
    port: Int,
    lanEnabled: Boolean,
    requireAuth: Boolean,
    onDismiss: () -> Unit,
    onCopyToast: (String) -> Unit
) {
    val palette = LocalSaMuColors.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var selectedTab by remember { mutableStateOf(0) } // 0 = Wi-Fi, 1 = USB, 2 = Remote Tunnel
    var adbStatus by remember { mutableStateOf<String?>(null) }

    val lanUrl = "http" + "://$localIp:$port/v1"
    val localUrl = "http" + "://127.0.0.1:$port/v1"
    val adbCmd = "adb reverse tcp:$port tcp:$port"
    val cfCmd = "cloudflared tunnel --url " + "http" + "://127.0.0.1:$port"
    val sshCmd = "ssh -R 80:localhost:$port nokey@localhost.run"

    ModalOverlay(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 380.dp, max = 560.dp)
                .fillMaxWidth(0.92f)
                .heightIn(max = 650.dp)
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
                Column {
                    Text("Connect Other Device (Phone / PC)", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                    Text("3 ways to use SaMu Lab from another device", fontSize = 11.sp, color = palette.primary)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = palette.textTertiary)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))

            // 3 Method Tabs
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MethodTabChip("Wi-Fi / Hotspot", Icons.Filled.Wifi, selectedTab == 0, Modifier.weight(1f)) { selectedTab = 0 }
                MethodTabChip("USB Cable", Icons.Filled.Usb, selectedTab == 1, Modifier.weight(1f)) { selectedTab = 1 }
                MethodTabChip("No Wi-Fi / USB", Icons.Filled.Public, selectedTab == 2, Modifier.weight(1f)) { selectedTab = 2 }
            }

            Box(modifier = Modifier.weight(1f, fill = false).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(start = 18.dp, end = 24.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            Text("Method 1: Same Wi-Fi or Phone Hotspot (Easiest)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                            if (!lanEnabled) {
                                Text(
                                    "Warning: LAN Access is currently OFF in Server Settings. Turn it ON and restart server first.",
                                    fontSize = 12.sp,
                                    color = palette.error
                                )
                            }
                            if (localIp == "127.0.0.1") {
                                Text(
                                    "No Wi-Fi/LAN IP detected right now. Connect to Wi-Fi or turn on Phone Hotspot, then click Refresh IP.",
                                    fontSize = 12.sp,
                                    color = palette.warning
                                )
                            }
                            GuideStepCard("1. Connect both devices to the same Wi-Fi (or turn on your phone's Hotspot and connect this laptop to it).")
                            GuideStepCard("2. Keep 'LAN Access' enabled in Server Settings and start the server.")
                            GuideStepCard("3. Paste this Base URL into your phone's AI chat app (along with Model Name & API Key if enabled):")
                            CopyCodeSnippet(lanUrl, onCopyToast)
                        }

                        1 -> {
                            Text("Method 2: USB Debugging Bridge (Zero Wi-Fi, Super Fast)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                            GuideStepCard("1. Enable 'USB Debugging' on your Android phone (Settings > Developer Options) and plug it in via USB cable.")
                            GuideStepCard("2. Run this command on your laptop (or click the button below) to tunnel port $port over USB:")
                            CopyCodeSnippet(adbCmd, onCopyToast)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        adbStatus = "Running adb reverse..."
                                        scope.launch(Dispatchers.IO) {
                                            val msg = runCatching {
                                                val p = ProcessBuilder("adb", "reverse", "tcp:$port", "tcp:$port")
                                                    .redirectErrorStream(true).start()
                                                val out = p.inputStream.bufferedReader().readText().trim()
                                                val ok = p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0
                                                if (ok) "USB bridge active! Use $localUrl on your phone."
                                                else "ADB failed: ${out.ifBlank { "install android-tools-adb & allow USB debugging on phone" }}"
                                            }.getOrElse { "adb not installed (run: sudo apt install adb)" }
                                            withContext(Dispatchers.Main) { adbStatus = msg }
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.Usb, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Run ADB Bridge Now", fontSize = 12.sp, color = Color.White)
                                }
                            }
                            adbStatus?.let {
                                Text(
                                    it,
                                    fontSize = 12.sp,
                                    color = if (it.startsWith("USB bridge active")) palette.success else palette.error
                                )
                            }
                            GuideStepCard("3. Now open your phone app and enter this Local URL (it goes straight over the USB cable!):")
                            CopyCodeSnippet(localUrl, onCopyToast)
                        }

                        else -> {
                            Text("Method 3: Without Same Wi-Fi or USB (Anywhere via Internet)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                            if (!requireAuth) {
                                Text(
                                    "Tip: Turn ON 'Require API Key' in Server Settings before sharing over a public internet link!",
                                    fontSize = 12.sp,
                                    color = palette.warning
                                )
                            }
                            GuideStepCard("Option A — Zero-Install Instant HTTPS Tunnel (works on mobile data):\nRun this in Linux terminal and paste the generated https://.../v1 URL on your phone:")
                            CopyCodeSnippet(sshCmd, onCopyToast)
                            GuideStepCard("Option B — Cloudflare Quick Tunnel (fast & stable):\nIf cloudflared is installed, run this for an instant https://*.trycloudflare.com/v1 URL:")
                            CopyCodeSnippet(cfCmd, onCopyToast)
                            GuideStepCard("Option C — Private Mesh VPN (Tailscale / ZeroTier):\nInstall Tailscale on laptop & phone, sign into the same account, and use your laptop's private 100.x.x.x:$port/v1 IP from anywhere — no port forwarding needed!")
                        }
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    style = samuScrollbarStyle(),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp, top = 4.dp, bottom = 4.dp)
                )
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, palette.primary)
                ) {
                    Text("Close", color = palette.primary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun MethodTabChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalSaMuColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) palette.primary else palette.background)
            .border(1.dp, if (selected) palette.primary else palette.divider, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Color.White else palette.textSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else palette.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun GuideStepCard(text: String) {
    val palette = LocalSaMuColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(palette.background)
            .border(1.dp, palette.divider, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text(text, fontSize = 12.sp, color = palette.onSurface, lineHeight = 18.sp)
    }
}

@Composable
private fun CopyCodeSnippet(code: String, onCopyToast: (String) -> Unit) {
    val palette = LocalSaMuColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(palette.codeBg)
            .border(1.dp, palette.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = code,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = palette.codeText,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { if (copyToClipboard(code)) onCopyToast("Copied!") },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = palette.primary, modifier = Modifier.size(15.dp))
        }
    }
}
