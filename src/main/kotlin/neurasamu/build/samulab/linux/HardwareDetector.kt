package neurasamu.build.samulab.linux

import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit

data class HardwareProfile(
    val cpuCores: Int,
    val totalRamGb: Int,
    val gpuName: String?,
    val gpuVramGb: Int,
    val hasCuda: Boolean,
    val hasVulkan: Boolean,
    val recommendedThreads: Int,
    val recommendedGpuLayers: Int,
    val recommendedContext: Int
) {
    val tierLabel: String
        get() = when {
            hasCuda && gpuVramGb >= 12 -> "Ultra (CUDA ${gpuVramGb}GB)"
            hasCuda && gpuVramGb >= 6 -> "High (CUDA ${gpuVramGb}GB)"
            hasCuda -> "GPU (CUDA)"
            hasVulkan && totalRamGb >= 16 -> "High (Vulkan + ${totalRamGb}GB RAM)"
            totalRamGb >= 16 -> "Mid-High (${cpuCores}T / ${totalRamGb}GB)"
            totalRamGb >= 8 -> "Standard (${cpuCores}T / ${totalRamGb}GB)"
            else -> "Compact (${cpuCores}T / ${totalRamGb}GB)"
        }
}

object HardwareDetector {
    @Volatile private var cached: HardwareProfile? = null

    fun detect(forceRefresh: Boolean = false): HardwareProfile {
        if (!forceRefresh) cached?.let { return it }

        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val ramGb = readTotalRamGb()
        val cudaInfo = detectNvidiaGpu()
        val hasCuda = cudaInfo != null
        val hasVulkan = hasCuda || detectVulkanGpu()
        val gpuName = cudaInfo?.first ?: if (hasVulkan) "Vulkan GPU" else null
        val gpuVramGb = cudaInfo?.second ?: 0

        val recThreads = when {
            cores >= 16 -> cores - 2
            cores >= 8 -> cores - 1
            cores >= 4 -> cores - 1
            else -> cores
        }.coerceAtLeast(1)

        val recGpuLayers = when {
            hasCuda && gpuVramGb >= 8 -> 99
            hasCuda && gpuVramGb >= 4 -> 35
            hasCuda -> 20
            hasVulkan -> 24
            else -> 0
        }

        val recCtx = when {
            hasCuda && gpuVramGb >= 12 -> 16384
            ramGb >= 16 -> 8192
            ramGb >= 8 -> 4096
            else -> 2048
        }

        return HardwareProfile(
            cpuCores = cores,
            totalRamGb = ramGb,
            gpuName = gpuName,
            gpuVramGb = gpuVramGb,
            hasCuda = hasCuda,
            hasVulkan = hasVulkan,
            recommendedThreads = recThreads,
            recommendedGpuLayers = recGpuLayers,
            recommendedContext = recCtx
        ).also { cached = it }
    }

    private fun readTotalRamGb(): Int {
        val beanRam = runCatching {
            val bean = ManagementFactory.getOperatingSystemMXBean()
            if (bean is com.sun.management.OperatingSystemMXBean) {
                val bytes = bean.totalMemorySize
                if (bytes > 0L) ((bytes / (1024.0 * 1024.0 * 1024.0)) + 0.4).toInt().coerceAtLeast(1) else 0
            } else 0
        }.getOrDefault(0)
        if (beanRam > 0) return beanRam

        if (Platform.isLinux) {
            return runCatching {
                val memInfo = File("/proc/meminfo")
                if (!memInfo.exists()) return 8
                val line = memInfo.useLines { seq -> seq.firstOrNull { it.startsWith("MemTotal:") } }
                    ?: return 8
                val kb = line.replace(Regex("[^0-9]"), "").toLongOrNull() ?: return 8
                ((kb / 1024.0 / 1024.0) + 0.5).toInt().coerceAtLeast(1)
            }.getOrDefault(8)
        }
        return 8
    }

    private fun detectNvidiaGpu(): Pair<String, Int>? {
        val candidates = if (Platform.isWindows) {
            listOf(
                "nvidia-smi",
                "C:\\Windows\\System32\\nvidia-smi.exe",
                "C:\\Program Files\\NVIDIA Corporation\\NVSMI\\nvidia-smi.exe"
            )
        } else {
            listOf("nvidia-smi")
        }
        for (bin in candidates) {
            val res = runCatching {
                val proc = ProcessBuilder(
                    bin,
                    "--query-gpu=name,memory.total",
                    "--format=csv,noheader,nounits"
                ).redirectErrorStream(true).start()
                val finished = proc.waitFor(2, TimeUnit.SECONDS)
                if (!finished || proc.exitValue() != 0) {
                    proc.destroyForcibly()
                    return@runCatching null
                }
                val line = proc.inputStream.bufferedReader().readLine()?.trim().orEmpty()
                if (line.isBlank()) return@runCatching null
                val parts = line.split(",")
                val name = parts.getOrNull(0)?.trim().orEmpty().ifBlank { "NVIDIA GPU" }
                val mb = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                val gb = ((mb / 1024.0) + 0.4).toInt().coerceAtLeast(0)
                name to gb
            }.getOrNull()
            if (res != null) return res
        }
        return null
    }

    private fun detectVulkanGpu(): Boolean {
        if (Platform.isWindows) {
            if (Platform.isLegacyWindows) return false
            val sysRoot = System.getenv("SystemRoot") ?: "C:\\Windows"
            return File(sysRoot, "System32\\vulkan-1.dll").isFile
        }
        val driRender = File("/dev/dri/renderD128").exists()
        if (!driRender) return false
        return runCatching {
            val proc = ProcessBuilder("sh", "-c", "command -v vulkaninfo >/dev/null 2>&1").start()
            proc.waitFor(2, TimeUnit.SECONDS) && proc.exitValue() == 0
        }.getOrDefault(false)
    }
}
