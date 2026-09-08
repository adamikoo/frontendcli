package com.antigravity.pocketgravity.api

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream

object RuntimeManager {

    private lateinit var appContext: Context
    private val executor = Executors.newSingleThreadExecutor()

    val isInitialized: Boolean
        get() = ::appContext.isInitialized

    fun init(context: Context) {
        appContext = context.applicationContext
        ensureDirectories()
        executor.execute {
            extractEmbeddedRuntime()
        }
    }

    val filesDir: File
        get() = appContext.filesDir

    val runtimeDir: File
        get() = File(filesDir, "runtime")

    val glibcDir: File
        get() = File(runtimeDir, "glibc")

    val binDir: File
        get() = File(runtimeDir, "bin")

    val homeDir: File
        get() = File(filesDir, "home")

    val workspaceDir: File
        get() = File(filesDir, "workspace")

    val agyConfigDir: File
        get() = File(homeDir, ".gemini/antigravity-cli")

    val agyTokenFile: File
        get() {
            val candidates = listOf(
                File(agyConfigDir, "antigravity-oauth-token"),
                File("/data/data/com.termux/files/home/.gemini/antigravity-cli/antigravity-oauth-token"),
                File("/root/.gemini/antigravity-cli/antigravity-oauth-token")
            )
            return candidates.firstOrNull { it.exists() && it.length() > 0 } ?: candidates[0]
        }

    val agySettingsFile: File
        get() {
            val candidates = listOf(
                File(agyConfigDir, "settings.json"),
                File("/data/data/com.termux/files/home/.gemini/antigravity-cli/settings.json"),
                File("/root/.gemini/antigravity-cli/settings.json")
            )
            return candidates.firstOrNull { it.exists() } ?: candidates[0]
        }

    private fun ensureDirectories() {
        binDir.mkdirs()
        glibcDir.mkdirs()
        homeDir.mkdirs()
        workspaceDir.mkdirs()
        agyConfigDir.mkdirs()

        val etcDir = File(homeDir, "etc").apply { mkdirs() }
        val resolvConf = File(etcDir, "resolv.conf")
        if (!resolvConf.exists()) {
            try {
                resolvConf.writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
            } catch (_: Exception) {}
        }
        val hostsFile = File(etcDir, "hosts")
        if (!hostsFile.exists()) {
            try {
                hostsFile.writeText("127.0.0.1 localhost\n")
            } catch (_: Exception) {}
        }

        val readme = File(workspaceDir, "README.md")
        if (!readme.exists()) {
            try {
                readme.writeText("# Pocket Gravity Workspace\n\nWelcome to Pocket Gravity standalone environment.\nUse the Agent tab to chat with Antigravity, edit files in the Editor, or explore files in Explorer.\n")
            } catch (_: Exception) {}
        }
    }

    fun extractEmbeddedRuntime() {
        try {
            ensureDirectories()
            // 1. Extract glibc libraries if missing or updated
            val glibcStamp = File(glibcDir, ".stamp_v2")
            val ldLinux = File(glibcDir, "ld-linux-aarch64.so.1")
            if (!glibcStamp.exists() || !ldLinux.exists() || ldLinux.length() == 0L) {
                val list = appContext.assets.list("glibc_arm64") ?: emptyArray()
                for (name in list) {
                    val target = File(glibcDir, name)
                    extractAsset("glibc_arm64/$name", target)
                }
                try { glibcStamp.createNewFile() } catch (_: Exception) {}
            }
            ldLinux.setReadable(true, false)
            ldLinux.setExecutable(true, false)
            try {
                Runtime.getRuntime().exec(arrayOf("chmod", "755", ldLinux.absolutePath)).waitFor()
            } catch (_: Exception) {}

            // 2. Extract PRoot binaries and libraries for syscall interception
            val prootDir = File(runtimeDir, "proot")
            val prootStamp = File(prootDir, ".stamp_v1")
            val prootBin = File(prootDir, "proot")
            if (!prootStamp.exists() || !prootBin.exists()) {
                val list = appContext.assets.list("proot_arm64") ?: emptyArray()
                for (name in list) {
                    val target = File(prootDir, name)
                    extractAsset("proot_arm64/$name", target)
                }
                prootBin.setReadable(true, false)
                prootBin.setExecutable(true, false)
                try {
                    Runtime.getRuntime().exec(arrayOf("chmod", "755", prootBin.absolutePath)).waitFor()
                } catch (_: Exception) {}
                try { prootStamp.createNewFile() } catch (_: Exception) {}
            }

            // 3. Extract CA certificates if missing
            val caCert = File(runtimeDir, "ca-certificates.crt")
            if (!caCert.exists() || caCert.length() == 0L) {
                extractAsset("ca-certificates.crt", caCert)
            }

            // 4. Extract agy binary from agy_arm64.tar or agy_arm64.tar.gz if missing or incomplete
            val agyBin = File(binDir, "agy")
            if (!agyBin.exists() || agyBin.length() < 150000000L) {
                val binAssets = appContext.assets.list("bin") ?: emptyArray()
                val tarAsset = binAssets.firstOrNull { it.startsWith("agy_arm64") }
                if (tarAsset != null) {
                    extractAgyFromTarGz("bin/$tarAsset", agyBin)
                }
            }
            agyBin.setReadable(true, false)
            agyBin.setExecutable(true, false)
            try {
                Runtime.getRuntime().exec(arrayOf("chmod", "755", agyBin.absolutePath)).waitFor()
            } catch (_: Exception) {}
        } catch (e: Exception) {
            DebugLogger.e("RuntimeManager extract error", e)
        }
    }

    private fun extractAsset(assetPath: String, target: File) {
        try {
            target.parentFile?.mkdirs()
            appContext.assets.open(assetPath).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            target.setReadable(true, false)
            target.setExecutable(true, false)
            try {
                Runtime.getRuntime().exec(arrayOf("chmod", "755", target.absolutePath)).waitFor()
            } catch (_: Exception) {}
        } catch (e: Exception) {
            DebugLogger.e("Failed to extract asset $assetPath", e)
        }
    }

    private fun extractAgyFromTarGz(assetPath: String, destination: File) {
        val tempDest = File(destination.parentFile, "${destination.name}.tmp")
        try {
            destination.parentFile?.mkdirs()
            if (tempDest.exists()) tempDest.delete()

            DebugLogger.i("Extracting embedded Antigravity engine ($assetPath)...")
            appContext.assets.open(assetPath).use { raw ->
                val stream: InputStream = if (assetPath.endsWith(".gz")) GZIPInputStream(raw) else raw
                stream.use { tar ->
                    val header = ByteArray(512)
                    while (true) {
                        var read = 0
                        while (read < 512) {
                            val r = tar.read(header, read, 512 - read)
                            if (r == -1) break
                            read += r
                        }
                        if (read < 512) break
                        val name = String(header, 0, 100).trim { it <= ' ' || it == '\u0000' }
                        if (name.isEmpty()) break
                        val sizeStr = String(header, 124, 12).trim { it <= ' ' || it == '\u0000' }
                        val size = sizeStr.toLongOrNull(8) ?: 0L
                        if (name == "antigravity" || name.endsWith("/antigravity")) {
                            FileOutputStream(tempDest).use { out ->
                                val buffer = ByteArray(65536)
                                var remaining = size
                                while (remaining > 0) {
                                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                                    val r = tar.read(buffer, 0, toRead)
                                    if (r == -1) break
                                    out.write(buffer, 0, r)
                                    remaining -= r
                                }
                            }
                            tempDest.setReadable(true, false)
                            tempDest.setExecutable(true, false)
                            try {
                                Runtime.getRuntime().exec(arrayOf("chmod", "755", tempDest.absolutePath)).waitFor()
                            } catch (_: Exception) {}
                            if (destination.exists()) destination.delete()
                            tempDest.renameTo(destination)
                            destination.setReadable(true, false)
                            destination.setExecutable(true, false)
                            try {
                                Runtime.getRuntime().exec(arrayOf("chmod", "755", destination.absolutePath)).waitFor()
                            } catch (_: Exception) {}
                            DebugLogger.i("Antigravity engine extracted successfully (${destination.length()} bytes)")
                            return
                        } else {
                            var toSkip = if (size % 512L != 0L) size + (512L - (size % 512L)) else size
                            val skipBuf = ByteArray(32768)
                            while (toSkip > 0) {
                                val r = tar.read(skipBuf, 0, minOf(skipBuf.size.toLong(), toSkip).toInt())
                                if (r == -1) break
                                toSkip -= r
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogger.e("Failed to extract Antigravity binary", e)
            if (tempDest.exists()) tempDest.delete()
        }
    }

    fun findAgyBinary(): File? {
        val embeddedAgy = File(binDir, "agy")
        if (embeddedAgy.exists() && embeddedAgy.length() > 150000000L) return embeddedAgy

        val candidates = listOf(
            File(homeDir, ".local/bin/agy"),
            File("/data/data/com.termux/files/home/.local/bin/agy"),
            File("/data/data/com.termux/files/usr/bin/agy")
        )
        return candidates.firstOrNull { it.exists() && (it.canExecute() || it.length() > 1000000L) }
    }

    fun getLdLinux(): File? {
        val f = File(glibcDir, "ld-linux-aarch64.so.1")
        return if (f.exists()) f else null
    }

    fun isStandaloneRuntimeReady(): Boolean {
        val agy = findAgyBinary()
        val ld = getLdLinux()
        return agy != null && ld != null
    }

    fun waitForRuntimeReady(timeoutMs: Long = 15000L): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (isStandaloneRuntimeReady()) return true
            try {
                Thread.sleep(250)
            } catch (_: InterruptedException) {
                break
            }
        }
        return isStandaloneRuntimeReady()
    }

    @Volatile
    private var cachedAgyVersion: String? = null

    fun getAgyVersion(): String {
        cachedAgyVersion?.let { return it }
        val ver = try {
            val agy = findAgyBinary() ?: return "not found"
            val pb = buildProcess(listOf(agy.absolutePath, "--version"), workspaceDir)
            val p = pb.start()
            val text = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            text.ifEmpty { "1.1.27" }
        } catch (e: Exception) {
            "1.1.27"
        }
        if (ver != "not found") {
            cachedAgyVersion = ver
        }
        return ver
    }

    fun findProotBinary(): File? {
        val candidates = listOf(
            File(File(runtimeDir, "proot"), "proot"),
            File("/data/data/com.termux/files/usr/bin/proot")
        )
        return candidates.firstOrNull { it.exists() && (it.canExecute() || it.length() > 0) }
    }

    fun buildProcess(
        command: List<String>,
        cwd: File = workspaceDir,
        customEnv: Map<String, String> = emptyMap(),
        forceProot: Boolean = false
    ): ProcessBuilder {
        val agy = findAgyBinary()
        val ldLinux = getLdLinux()
        val proot = findProotBinary()
        val finalCmd = mutableListOf<String>()

        val isExecutingAgy = command.isNotEmpty() && (command[0].endsWith("agy") || command[0] == "agy" || (agy != null && command[0] == agy.absolutePath))

        if (isExecutingAgy && ldLinux != null && agy != null) {
            if (forceProot && proot != null) {
                finalCmd.add(proot.absolutePath)
                finalCmd.add("-0")
                finalCmd.add("-b")
                finalCmd.add("/system:/system")
                finalCmd.add("-b")
                finalCmd.add("/dev:/dev")
                finalCmd.add("-b")
                finalCmd.add("/proc:/proc")
                finalCmd.add("-b")
                finalCmd.add("${filesDir.absolutePath}:${filesDir.absolutePath}")
                finalCmd.add(ldLinux.absolutePath)
                finalCmd.add("--library-path")
                finalCmd.add(glibcDir.absolutePath)
                finalCmd.add(agy.absolutePath)
                finalCmd.addAll(command.drop(1))
            } else {
                finalCmd.add(ldLinux.absolutePath)
                finalCmd.add("--library-path")
                finalCmd.add(glibcDir.absolutePath)
                finalCmd.add(agy.absolutePath)
                finalCmd.addAll(command.drop(1))
            }
        } else {
            finalCmd.addAll(command)
        }

        val pb = ProcessBuilder(finalCmd)
        pb.directory(if (cwd.exists()) cwd else workspaceDir)
        val env = pb.environment()
        env["HOME"] = homeDir.absolutePath
        val tmpDir = File(filesDir, "tmp").apply { mkdirs() }
        env["TMPDIR"] = tmpDir.absolutePath
        env["PROOT_TMP_DIR"] = tmpDir.absolutePath
        env["LANG"] = "en_US.UTF-8"
        env["LC_ALL"] = "en_US.UTF-8"
        env["GLIBC_TUNABLES"] = "glibc.pthread.rseq=0"
        env["GODEBUG"] = "netdns=go"

        val etcDir = File(homeDir, "etc").apply { mkdirs() }
        val resolvConf = File(etcDir, "resolv.conf")
        if (!resolvConf.exists()) {
            try {
                resolvConf.writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
            } catch (_: Exception) {}
        }
        env["RESOLV_CONF"] = resolvConf.absolutePath

        val caCert = File(runtimeDir, "ca-certificates.crt")
        if (caCert.exists()) {
            env["SSL_CERT_FILE"] = caCert.absolutePath
            env["SSL_CERT_DIR"] = File(runtimeDir, "certs").apply { mkdirs() }.absolutePath
        }

        val prootDir = File(runtimeDir, "proot")
        val currentLd = env["LD_LIBRARY_PATH"] ?: ""
        env["LD_LIBRARY_PATH"] = if (currentLd.isNotEmpty()) "${prootDir.absolutePath}:$currentLd" else prootDir.absolutePath

        val currentPath = env["PATH"] ?: "/system/bin:/system/xbin"
        env["PATH"] = "${binDir.absolutePath}:$currentPath"

        for ((k, v) in customEnv) {
            env[k] = v
        }
        return pb
    }
}
