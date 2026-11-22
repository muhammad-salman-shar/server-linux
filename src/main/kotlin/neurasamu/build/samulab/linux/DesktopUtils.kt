package neurasamu.build.samulab.linux

import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.file.Files
import java.nio.file.Path

fun getLocalIp(): String {
    return runCatching {
        val ifaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        val candidates = ifaces
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { iface ->
                iface.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .map { iface.name.lowercase() to it.hostAddress }
            }
        val preferred = candidates.firstOrNull { (name, ip) ->
            !ip.startsWith("127.") &&
                !name.startsWith("docker") &&
                !name.startsWith("br-") &&
                !name.startsWith("veth") &&
                (name.startsWith("wl") || name.startsWith("en") || name.startsWith("eth") || name.startsWith("usb"))
        } ?: candidates.firstOrNull { (_, ip) -> !ip.startsWith("127.") }
        preferred?.second
    }.getOrNull() ?: "127.0.0.1"
}

fun copyToClipboard(text: String): Boolean = runCatching {
    val sel = StringSelection(text)
    Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
    true
}.getOrDefault(false)

fun openFilePicker(title: String, extensions: Set<String>? = null): File? {
    return runCatching {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        if (!extensions.isNullOrEmpty()) {
            dialog.setFilenameFilter { _, name ->
                val lower = name.lowercase()
                extensions.any { lower.endsWith(it) }
            }
        }
        dialog.isVisible = true
        val dir = dialog.directory
        val file = dialog.file
        if (dir != null && file != null) File(dir, file).absoluteFile.normalize() else null
    }.getOrNull()
}

fun saveTextFileDialog(defaultFilename: String, content: String): File? {
    return runCatching {
        val dialog = FileDialog(null as Frame?, "Export Chat as TXT", FileDialog.SAVE)
        dialog.file = defaultFilename
        val downloads = File(System.getProperty("user.home"), "Downloads")
        if (downloads.exists()) dialog.directory = downloads.absolutePath
        dialog.isVisible = true
        val dir = dialog.directory
        val file = dialog.file
        if (dir != null && file != null) {
            val out = File(dir, if (file.lowercase().endsWith(".txt")) file else "$file.txt")
            out.writeText(content)
            out
        } else null
    }.getOrNull()
}

fun createLinuxDesktopShortcut(): Pair<Boolean, String> {
    return runCatching {
        val home = System.getProperty("user.home")
        val desktopDir = Path.of(home, "Desktop").toFile()
        if (!desktopDir.exists()) desktopDir.mkdirs()

        // Save icon in ~/.local/share/samu-lab/icon.png so shortcut always has the exact icon
        SettingsStore.ensureDirs()
        val iconDest = SettingsStore.appDir.resolve("icon.png").toFile()
        Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")?.use { input ->
            iconDest.outputStream().use { output -> input.copyTo(output) }
        }

        val execCandidates = listOf(
            "/opt/samu-lab/bin/samu-lab",
            "/usr/bin/samu-lab"
        )
        val execPath = execCandidates.firstOrNull { File(it).exists() } ?: "/opt/samu-lab/bin/samu-lab"
        val iconPath = when {
            iconDest.exists() -> iconDest.absolutePath
            File("/opt/samu-lab/lib/samu-lab.png").exists() -> "/opt/samu-lab/lib/samu-lab.png"
            else -> "samu-lab"
        }

        val desktopContent = """
            [Desktop Entry]
            Version=1.0
            Type=Application
            Name=SaMu Lab
            Comment=Local AI Chat & OpenAI-Compatible Server
            Exec=$execPath
            Icon=$iconPath
            Terminal=false
            Categories=Development;Utility;
            StartupNotify=true
        """.trimIndent() + "\n"

        val shortcut = File(desktopDir, "SaMu-Lab.desktop")
        shortcut.writeText(desktopContent)
        shortcut.setExecutable(true, false)
        runCatching { ProcessBuilder("chmod", "+x", shortcut.absolutePath).start().waitFor() }
        // Mark trusted on GNOME/XFCE desktops if gio is available
        runCatching {
            ProcessBuilder("gio", "set", shortcut.absolutePath, "metadata::trusted", "true")
                .start().waitFor()
        }
        true to "Desktop shortcut created at ${shortcut.absolutePath}"
    }.getOrElse { e ->
        false to "Could not create shortcut: ${e.message}"
    }
}

class LinuxTtsPlayer {
    @Volatile private var proc: Process? = null
    @Volatile var speakingMessageId: String? = null
        private set

    fun stop() {
        val p = proc
        proc = null
        speakingMessageId = null
        runCatching { p?.destroyForcibly() }
    }

    fun toggle(messageId: String, text: String, onStateChange: (String?) -> Unit): String? {
        if (speakingMessageId == messageId) {
            stop()
            onStateChange(null)
            return null
        }
        stop()
        val clean = text
            .replace(Regex("<think>[\\s\\S]*?</think>"), " ")
            .replace(Regex("```[\\s\\S]*?```"), " code block ")
            .replace(Regex("[#*_`>]"), " ")
            .trim()
        if (clean.isEmpty()) return "Nothing to read aloud."

        val commands = listOf(
            listOf("spd-say", "-w", clean),
            listOf("espeak-ng", clean),
            listOf("espeak", clean)
        )
        for (cmd in commands) {
            val started = runCatching {
                val p = ProcessBuilder(cmd).start()
                proc = p
                speakingMessageId = messageId
                onStateChange(messageId)
                Thread {
                    runCatching { p.waitFor() }
                    if (proc === p) {
                        proc = null
                        speakingMessageId = null
                        onStateChange(null)
                    }
                }.apply { isDaemon = true }.start()
                true
            }.getOrDefault(false)
            if (started) return null
        }
        return "No TTS engine found (install espeak-ng or speech-dispatcher)."
    }
}
