package neurasamu.build.samulab.linux

import java.io.File
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
        return runCatching {
            val memInfo = File("/proc/meminfo")
            if (!memInfo.exists()) return 8
            val line = memInfo.useLines { seq -> seq.firstOrNull { it.startsWith("MemTotal:") } }
                ?: return 8
            val kb = line.replace(Regex("[^0-9]"), "").toLongOrNull() ?: return 8
            ((kb / 1024.0 / 1024.0) + 0.5).toInt().coerceAtLeast(1)
        }.getOrDefault(8)
    }

    private fun detectNvidiaGpu(): Pair<String, Int>? {
        return runCatching {
            val proc = ProcessBuilder(
                "nvidia-smi",
                "--query-gpu=name,memory.total",
                "--format=csv,noheader,nounits"
            ).redirectErrorStream(true).start()
            val finished = proc.waitFor(2, TimeUnit.SECONDS)
            if (!finished || proc.exitValue() != 0) {
                proc.destroyForcibly()
                return null
            }
            val line = proc.inputStream.bufferedReader().readLine()?.trim().orEmpty()
            if (line.isBlank()) return null
            val parts = line.split(",")
            val name = parts.getOrNull(0)?.trim().orEmpty().ifBlank { "NVIDIA GPU" }
            val mb = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
            val gb = ((mb / 1024.0) + 0.4).toInt().coerceAtLeast(0)
            name to gb
        }.getOrNull()
    }

    private fun detectVulkanGpu(): Boolean {
        val driRender = File("/dev/dri/renderD128").exists()
        if (!driRender) return false
        return runCatching {
            val proc = ProcessBuilder("sh", "-c", "command -v vulkaninfo >/dev/null 2>&1")
                .start()
            proc.waitFor(2, TimeUnit.SECONDS) && proc.exitValue() == 0
        }.getOrDefault(false)
    }
}
