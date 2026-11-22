package neurasamu.build.samulab.linux

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

object Platform {
    val osName: String = System.getProperty("os.name").orEmpty()
    val osVersion: String = System.getProperty("os.version").orEmpty()
    val osArch: String = System.getProperty("os.arch").orEmpty().lowercase()

    val isWindows: Boolean = osName.contains("win", ignoreCase = true)
    val isMac: Boolean = osName.contains("mac", ignoreCase = true)
    val isLinux: Boolean = !isWindows && !isMac
    val isArm64: Boolean = osArch.contains("aarch64") || osArch.contains("arm64")

    // Windows 7 = 6.1, Windows 8 = 6.2, Windows 8.1 = 6.3
    val isLegacyWindows: Boolean = isWindows && (
        osVersion.startsWith("6.1") ||
            osVersion.startsWith("6.2") ||
            osVersion.startsWith("6.3") ||
            osName.contains("Windows 7", ignoreCase = true) ||
            osName.contains("Windows 8", ignoreCase = true)
        )

    fun exeName(base: String): String =
        if (isWindows && !base.endsWith(".exe", ignoreCase = true)) "$base.exe" else base

    fun appDataDir(): Path {
        return if (isWindows) {
            val localAppData = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
                ?: Path.of(System.getProperty("user.home"), "AppData", "Local").toString()
            Path.of(localAppData, "SaMuLab")
        } else if (isMac) {
            Path.of(System.getProperty("user.home"), "Library", "Application Support", "SaMuLab")
        } else {
            Path.of(System.getProperty("user.home"), ".local", "share", "samu-lab")
        }
    }

    fun isExecutableFile(f: File): Boolean =
        f.isFile && (isWindows || f.canExecute())

    fun makeExecutable(f: File) {
        if (isWindows || !f.exists()) return
        runCatching { f.setExecutable(true, false) }
        runCatching { ProcessBuilder("chmod", "+x", f.absolutePath).start().waitFor() }
    }

    fun extractArchive(archive: File, destDir: File) {
        destDir.mkdirs()
        val lower = archive.name.lowercase()
        if (lower.endsWith(".zip")) {
            val destCanonical = destDir.canonicalFile.toPath()
            ZipInputStream(archive.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val out = File(destDir, entry.name).canonicalFile
                    if (out.toPath().startsWith(destCanonical)) {
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            Files.copy(zis, out.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        } else {
            val proc = ProcessBuilder("tar", "-xzf", archive.absolutePath, "-C", destDir.absolutePath)
                .redirectErrorStream(true)
                .start()
            val code = proc.waitFor()
            if (code != 0) {
                val out = runCatching { proc.inputStream.bufferedReader().readText() }.getOrDefault("")
                throw Exception("Archive extraction failed (exit $code): ${out.take(200)}")
            }
        }
    }
}
