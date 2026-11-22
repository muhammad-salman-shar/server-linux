package neurasamu.build.samulab.linux

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent

fun getLocalIp(): String {
    return runCatching {
        val ifaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        val candidates = ifaces
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { iface ->
                val label = (iface.name + " " + (iface.displayName ?: "")).lowercase()
                iface.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .map { label to it.hostAddress }
            }
        val preferred = candidates.firstOrNull { (label, ip) ->
            !ip.startsWith("127.") &&
                !ip.startsWith("169.254.") &&
                !label.contains("docker") &&
                !label.contains("br-") &&
                !label.contains("veth") &&
                !label.contains("virtual") &&
                !label.contains("vmware") &&
                !label.contains("vbox") &&
                (label.contains("wl") || label.contains("wi-fi") || label.contains("wifi") ||
                    label.contains("wireless") || label.contains("en") || label.contains("eth") || label.contains("usb"))
        } ?: candidates.firstOrNull { (_, ip) -> !ip.startsWith("127.") && !ip.startsWith("169.254.") }
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
            if (Platform.isWindows) {
                dialog.file = extensions.joinToString(";") { "*$it" }
            }
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

fun saveBinaryFileDialog(title: String, defaultFilename: String, sourceFile: File): File? {
    if (!sourceFile.exists()) return null
    return runCatching {
        val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
        dialog.file = defaultFilename
        val downloads = File(System.getProperty("user.home"), "Downloads")
        if (downloads.exists()) dialog.directory = downloads.absolutePath
        dialog.isVisible = true
        val dir = dialog.directory
        val file = dialog.file
        if (dir != null && file != null) {
            val out = File(dir, file)
            Files.copy(sourceFile.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING)
            out
        } else null
    }.getOrNull()
}

fun openFileExternally(file: File): Boolean {
    if (!file.exists()) return false
    if (Platform.isWindows) {
        val desktopOk = runCatching {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file)
                true
            } else false
        }.getOrDefault(false)
        if (desktopOk) return true
        return runCatching {
            ProcessBuilder("cmd", "/c", "start", "\"\"", file.absolutePath).start()
            true
        }.getOrDefault(false)
    }
    val launched = runCatching {
        ProcessBuilder("xdg-open", file.absolutePath).start()
        true
    }.getOrDefault(false)
    if (launched) return true
    return runCatching {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(file)
            true
        } else false
    }.getOrDefault(false)
}

fun createLinuxDesktopShortcut(): Pair<Boolean, String> = createDesktopShortcut()

fun createDesktopShortcut(): Pair<Boolean, String> {
    if (Platform.isWindows) {
        return createWindowsDesktopShortcut()
    }
    return runCatching {
        val home = System.getProperty("user.home")
        val desktopDir = Path.of(home, "Desktop").toFile()
        if (!desktopDir.exists()) desktopDir.mkdirs()

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
            Comment=Universal Local AI Studio (Chat, Vision, Image, Audio & Video)
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
        runCatching {
            ProcessBuilder("gio", "set", shortcut.absolutePath, "metadata::trusted", "true")
                .start().waitFor()
        }
        true to "Desktop shortcut created at ${shortcut.absolutePath}"
    }.getOrElse { e ->
        false to "Could not create shortcut: ${e.message}"
    }
}

private fun createWindowsDesktopShortcut(): Pair<Boolean, String> {
    return runCatching {
        SettingsStore.ensureDirs()
        val iconDest = SettingsStore.appDir.resolve("icon.ico").toFile()
        Thread.currentThread().contextClassLoader.getResourceAsStream("icon.ico")?.use { input ->
            iconDest.outputStream().use { output -> input.copyTo(output) }
        }

        val exePath = ProcessHandle.current().info().command().orElse("").let { cmd ->
            if (cmd.lowercase().endsWith("samu-lab.exe") || cmd.lowercase().endsWith("samu lab.exe")) cmd
            else {
                val candidates = listOf(
                    File(System.getProperty("user.dir"), "SaMu Lab.exe"),
                    File(System.getProperty("user.dir"), "samu-lab.exe"),
                    File("C:\\Program Files\\SaMu Lab\\SaMu Lab.exe"),
                    File("C:\\Program Files\\samu-lab\\samu-lab.exe")
                )
                candidates.firstOrNull { it.isFile }?.absolutePath ?: cmd
            }
        }
        if (exePath.isBlank()) return false to "Could not locate SaMu Lab executable."

        val shortcutPath = File(File(System.getProperty("user.home"), "Desktop"), "SaMu Lab.lnk").absolutePath
        val safeLnk = shortcutPath.replace("'", "''")
        val safeExe = exePath.replace("'", "''")
        val safeIcon = (if (iconDest.exists()) iconDest.absolutePath else exePath).replace("'", "''")

        val ps = "\$ws = New-Object -ComObject WScript.Shell; " +
            "\$s = \$ws.CreateShortcut('$safeLnk'); " +
            "\$s.TargetPath = '$safeExe'; " +
            "\$s.IconLocation = '$safeIcon'; " +
            "\$s.Save()"

        val proc = ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps)
            .redirectErrorStream(true).start()
        val code = proc.waitFor()
        if (code == 0) true to "Desktop shortcut created at $shortcutPath"
        else false to "Shortcut creation failed (exit $code)"
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

        if (Platform.isWindows) {
            val b64 = Base64.getEncoder().encodeToString(clean.toByteArray(Charsets.UTF_8))
            val ps = "Add-Type -AssemblyName System.Speech; " +
                "\$t = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String('$b64')); " +
                "\$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                "\$s.Speak(\$t)"
            val started = runCatching {
                val p = ProcessBuilder("powershell", "-NoProfile", "-Command", ps).start()
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
            return if (started) null else "Windows Speech failed to start."
        }

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

class InAppAudioPlayer {
    @Volatile private var clip: Clip? = null
    @Volatile private var extProc: Process? = null
    @Volatile var playingPath: String? = null
        private set

    fun stop() {
        playingPath = null
        runCatching { clip?.stop() }
        runCatching { clip?.close() }
        clip = null
        runCatching { extProc?.destroyForcibly() }
        extProc = null
    }

    fun progressFraction(): Float {
        val c = clip ?: return 0f
        val len = c.microsecondLength
        if (len <= 0L) return 0f
        return (c.microsecondPosition.toFloat() / len.toFloat()).coerceIn(0f, 1f)
    }

    fun durationSeconds(): Double {
        val c = clip ?: return 0.0
        return (c.microsecondLength / 1_000_000.0).coerceAtLeast(0.0)
    }

    fun positionSeconds(): Double {
        val c = clip ?: return 0.0
        return (c.microsecondPosition / 1_000_000.0).coerceAtLeast(0.0)
    }

    fun seek(fraction: Float) {
        val c = clip ?: return
        val len = c.microsecondLength
        if (len > 0L) {
            c.microsecondPosition = (len * fraction.coerceIn(0f, 1f)).toLong()
        }
    }

    fun toggle(path: String, onFinished: () -> Unit): String? {
        if (playingPath == path) {
            stop()
            onFinished()
            return null
        }
        stop()
        val file = File(path)
        if (!file.exists()) return "Audio file not found."

        val nativeOk = runCatching {
            val ais = AudioSystem.getAudioInputStream(file)
            val c = AudioSystem.getClip()
            c.open(ais)
            c.addLineListener { ev ->
                if (ev.type == LineEvent.Type.STOP) {
                    if (playingPath == path && c.microsecondPosition >= c.microsecondLength - 50_000) {
                        stop()
                        onFinished()
                    }
                }
            }
            clip = c
            playingPath = path
            c.start()
            true
        }.getOrDefault(false)
        if (nativeOk) return null

        val cmds = if (Platform.isWindows) {
            listOf(listOf("ffplay", "-nodisp", "-autoexit", "-loglevel", "quiet", file.absolutePath))
        } else {
            listOf(
                listOf("ffplay", "-nodisp", "-autoexit", "-loglevel", "quiet", file.absolutePath),
                listOf("paplay", file.absolutePath),
                listOf("aplay", file.absolutePath)
            )
        }
        for (cmd in cmds) {
            val started = runCatching {
                val p = ProcessBuilder(cmd).start()
                extProc = p
                playingPath = path
                Thread {
                    runCatching { p.waitFor() }
                    if (extProc === p) {
                        extProc = null
                        playingPath = null
                        onFinished()
                    }
                }.apply { isDaemon = true }.start()
                true
            }.getOrDefault(false)
            if (started) return null
        }
        if (Platform.isWindows && openFileExternally(file)) return null
        return "Could not play this audio format directly."
    }
}
