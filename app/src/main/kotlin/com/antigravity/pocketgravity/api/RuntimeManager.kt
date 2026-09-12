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

    lateinit var appContext: Context
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

    val rootfsDir: File
        get() = File(runtimeDir, "rootfs")

    private fun ensureDirectories() {
        binDir.mkdirs()
        glibcDir.mkdirs()
        homeDir.mkdirs()
        workspaceDir.mkdirs()
        agyConfigDir.mkdirs()
        rootfsDir.mkdirs()

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

    fun setupRootfs() {
        try {
            val rootfs = rootfsDir
            val libDir = File(rootfs, "lib").apply { mkdirs() }
            val lib64Dir = File(rootfs, "lib64").apply { mkdirs() }
            val usrLibDir = File(rootfs, "usr/lib").apply { mkdirs() }
            val rootfsBin = File(rootfs, "bin").apply { mkdirs() }
            val rootfsUsrBin = File(rootfs, "usr/bin").apply { mkdirs() }
            val rootfsEtc = File(rootfs, "etc").apply { mkdirs() }
            val rootfsSsl = File(rootfs, "etc/ssl/certs").apply { mkdirs() }
            File(rootfs, "tmp").mkdirs()
            File(rootfs, "root").mkdirs()
            File(rootfs, "home").mkdirs()
            File(rootfs, "workspace").mkdirs()
            File(rootfs, "dev").mkdirs()
            File(rootfs, "proc").mkdirs()
            File(rootfs, "sys").mkdirs()
            File(rootfs, "system").mkdirs()

            // 1. Copy clean glibc libraries into rootfs /lib, /lib64, /usr/lib
            glibcDir.listFiles()?.filter { !it.name.startsWith(".") }?.forEach { src ->
                listOf(File(libDir, src.name), File(lib64Dir, src.name), File(usrLibDir, src.name)).forEach { dst ->
                    if (!dst.exists() || dst.length() != src.length()) {
                        try {
                            src.copyTo(dst, overwrite = true)
                            dst.setReadable(true, false)
                            dst.setExecutable(true, false)
                        } catch (_: Exception) {}
                    }
                }
            }

            // 2. Copy agy into rootfs /bin/agy and /usr/bin/agy
            val agyBin = File(binDir, "agy")
            if (agyBin.exists() && agyBin.length() > 0) {
                listOf(File(rootfsBin, "agy"), File(rootfsUsrBin, "agy")).forEach { dst ->
                    if (!dst.exists() || dst.length() != agyBin.length()) {
                        try {
                            agyBin.copyTo(dst, overwrite = true)
                            dst.setReadable(true, false)
                            dst.setExecutable(true, false)
                        } catch (_: Exception) {}
                    }
                }
            }

            // 3. Setup resolv.conf, nsswitch.conf, and hosts in rootfs /etc
            val resolvConf = File(rootfsEtc, "resolv.conf")
            try {
                resolvConf.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\nnameserver 1.0.0.1\nnameserver 8.8.4.4\n")
            } catch (_: Exception) {}

            val nsswitch = File(rootfsEtc, "nsswitch.conf")
            if (!nsswitch.exists()) {
                try {
                    nsswitch.writeText("hosts: files dns\n")
                } catch (_: Exception) {}
            }

            val hostsFile = File(rootfsEtc, "hosts")
            if (!hostsFile.exists()) {
                try {
                    hostsFile.writeText("127.0.0.1 localhost\n::1 localhost\n")
                } catch (_: Exception) {}
            }

            // 4. Setup CA certs in rootfs /etc/ssl/certs/ca-certificates.crt
            val caCert = File(runtimeDir, "ca-certificates.crt")
            if (caCert.exists()) {
                val dstCert = File(rootfsSsl, "ca-certificates.crt")
                if (!dstCert.exists() || dstCert.length() != caCert.length()) {
                    try { caCert.copyTo(dstCert, overwrite = true) } catch (_: Exception) {}
                }
                val dstCertEtc = File(rootfsEtc, "ca-certificates.crt")
                if (!dstCertEtc.exists() || dstCertEtc.length() != caCert.length()) {
                    try { caCert.copyTo(dstCertEtc, overwrite = true) } catch (_: Exception) {}
                }
            }

            // 5. Ensure legacy credentials format is synced into rootfs
            syncLegacyCredentials()

            try {
                Runtime.getRuntime().exec(arrayOf("chmod", "-R", "755", rootfs.absolutePath)).waitFor()
            } catch (_: Exception) {}
        } catch (e: Exception) {
            DebugLogger.e("RuntimeManager setupRootfs error", e)
        }
    }

    fun syncLegacyCredentials() {
        try {
            val candidates = listOf(
                File(homeDir, ".gemini/antigravity-cli/antigravity-oauth-token"),
                File(homeDir, ".gemini/oauth_creds.json"),
                File(agyConfigDir, "oauth_credentials.json"),
                File(homeDir, ".config/gcloud/application_default_credentials.json"),
                File(rootfsDir, "root/.gemini/antigravity-cli/antigravity-oauth-token"),
                File(rootfsDir, "root/.gemini/oauth_creds.json")
            )
            var rawToken = ""
            var refreshToken = ""
            for (c in candidates) {
                if (c.exists() && c.length() > 0) {
                    try {
                        val text = c.readText().trim()
                        if (text.startsWith("{")) {
                            val json = org.json.JSONObject(text)
                            if (json.has("access_token") && rawToken.isEmpty()) {
                                rawToken = json.optString("access_token", "")
                            }
                            if (json.has("refresh_token") && refreshToken.isEmpty()) {
                                refreshToken = json.optString("refresh_token", "")
                            }
                        } else if (text.startsWith("ya29") || text.length > 30) {
                            if (rawToken.isEmpty()) rawToken = text
                        }
                    } catch (_: Exception) {}
                }
            }

            if (refreshToken.isEmpty()) {
                try {
                    val assetStream = appContext.assets.open("default_credentials.json")
                    val assetJson = org.json.JSONObject(assetStream.bufferedReader().readText())
                    if (rawToken.isEmpty() && assetJson.has("access_token")) {
                        rawToken = assetJson.optString("access_token", "")
                    }
                    if (assetJson.has("refresh_token")) {
                        refreshToken = assetJson.optString("refresh_token", "")
                    }
                } catch (_: Exception) {}
            }

            if (rawToken.isNotEmpty() || refreshToken.isNotEmpty()) {
                DebugLogger.i("syncLegacyCredentials: normalizing credentials (token len=${rawToken.length}, refresh len=${refreshToken.length})")

                // 1. Raw access token for antigravity-oauth-token (exact legacy baseline)
                val tokenFiles = listOf(
                    File(homeDir, ".gemini/antigravity-cli/antigravity-oauth-token"),
                    File(homeDir, ".gemini/antigravity-oauth-token"),
                    File(rootfsDir, "root/.gemini/antigravity-cli/antigravity-oauth-token"),
                    File(rootfsDir, "root/.gemini/antigravity-oauth-token")
                )
                if (rawToken.isNotEmpty()) {
                    for (tf in tokenFiles) {
                        try {
                            tf.parentFile?.mkdirs()
                            tf.writeText(rawToken)
                        } catch (_: Exception) {}
                    }
                }

                // 2. Full oauth_creds.json & oauth_credentials.json
                val oauthObj = org.json.JSONObject().apply {
                    if (rawToken.isNotEmpty()) put("access_token", rawToken)
                    if (refreshToken.isNotEmpty()) put("refresh_token", refreshToken)
                    put("token_type", "Bearer")
                    put("expires_in", 3600)
                    put("expiry_date", System.currentTimeMillis() + 3300 * 1000L)
                    put("expires_at", System.currentTimeMillis() + 3300 * 1000L)
                }
                val oauthStr = oauthObj.toString(2)
                val credFiles = listOf(
                    File(homeDir, ".gemini/oauth_creds.json"),
                    File(agyConfigDir, "oauth_credentials.json"),
                    File(rootfsDir, "root/.gemini/oauth_creds.json"),
                    File(rootfsDir, "root/.gemini/antigravity-cli/oauth_credentials.json")
                )
                for (cf in credFiles) {
                    try {
                        cf.parentFile?.mkdirs()
                        cf.writeText(oauthStr)
                    } catch (_: Exception) {}
                }

                // 3. Application Default Credentials (ADC)
                val adcObj = org.json.JSONObject().apply {
                    put("type", "authorized_user")
                    put("client_id", "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com")
                    put("client_secret", "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")
                    if (refreshToken.isNotEmpty()) put("refresh_token", refreshToken)
                    if (rawToken.isNotEmpty()) put("access_token", rawToken)
                }
                val adcStr = adcObj.toString(2)
                val adcFiles = listOf(
                    File(homeDir, ".config/gcloud/application_default_credentials.json"),
                    File(agyConfigDir, "application_default_credentials.json"),
                    File(rootfsDir, "root/.config/gcloud/application_default_credentials.json"),
                    File(rootfsDir, "root/.gemini/antigravity-cli/application_default_credentials.json")
                )
                for (af in adcFiles) {
                    try {
                        af.parentFile?.mkdirs()
                        af.writeText(adcStr)
                    } catch (_: Exception) {}
                }

                // 4. Default settings.json
                val defaultSettings = org.json.JSONObject().apply {
                    put("model", "gemini-3.8-flash")
                    put("effort", "medium")
                }.toString(2)
                val settingsFiles = listOf(
                    File(agyConfigDir, "settings.json"),
                    File(rootfsDir, "root/.gemini/antigravity-cli/settings.json")
                )
                for (sf in settingsFiles) {
                    try {
                        if (!sf.exists() || sf.length() == 0L) {
                            sf.parentFile?.mkdirs()
                            sf.writeText(defaultSettings)
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            DebugLogger.e("syncLegacyCredentials error", e)
        }
    }

    fun extractEmbeddedRuntime() {
        try {
            ensureDirectories()
            // 1. Extract glibc libraries if missing or updated
            val glibcStamp = File(glibcDir, ".stamp_v3")
            val ldLinux = File(glibcDir, "ld-linux-aarch64.so.1")
            val oldStampV1 = File(glibcDir, ".stamp_v1")
            val oldStampV2 = File(glibcDir, ".stamp_v2")
            if (!glibcStamp.exists() || !ldLinux.exists() || ldLinux.length() == 0L) {
                try { oldStampV1.delete() } catch (_: Exception) {}
                try { oldStampV2.delete() } catch (_: Exception) {}
                val list = appContext.assets.list("glibc_arm64") ?: emptyArray()
                for (name in list) {
                    val target = File(glibcDir, name)
                    extractAsset("glibc_arm64/$name", target)
                    target.setReadable(true, false)
                    target.setExecutable(true, false)
                    try {
                        Runtime.getRuntime().exec(arrayOf("chmod", "755", target.absolutePath)).waitFor()
                    } catch (_: Exception) {}
                }
                try { glibcStamp.createNewFile() } catch (_: Exception) {}
            }
            glibcDir.listFiles()?.forEach { file ->
                file.setReadable(true, false)
                file.setExecutable(true, false)
            }
            try {
                Runtime.getRuntime().exec(arrayOf("chmod", "-R", "755", glibcDir.absolutePath)).waitFor()
            } catch (_: Exception) {}

            // 2. Extract PRoot binaries and libraries for syscall interception
            val prootDir = File(runtimeDir, "proot")
            val prootStamp = File(prootDir, ".stamp_v4")
            val prootBin = File(prootDir, "proot")
            val prootLoader = File(prootDir, "loader")
            val oldProotStampV1 = File(prootDir, ".stamp_v1")
            val oldProotStampV2 = File(prootDir, ".stamp_v2")
            val oldProotStampV3 = File(prootDir, ".stamp_v3")
            if (!prootStamp.exists() || !prootBin.exists() || !prootLoader.exists()) {
                try { oldProotStampV1.delete() } catch (_: Exception) {}
                try { oldProotStampV2.delete() } catch (_: Exception) {}
                try { oldProotStampV3.delete() } catch (_: Exception) {}
                val list = appContext.assets.list("proot_arm64") ?: emptyArray()
                for (name in list) {
                    val target = File(prootDir, name)
                    extractAsset("proot_arm64/$name", target)
                    target.setReadable(true, false)
                    target.setExecutable(true, false)
                    try {
                        Runtime.getRuntime().exec(arrayOf("chmod", "755", target.absolutePath)).waitFor()
                    } catch (_: Exception) {}
                }
                try { prootStamp.createNewFile() } catch (_: Exception) {}
            }
            prootDir.listFiles()?.forEach { file ->
                file.setReadable(true, false)
                file.setExecutable(true, false)
            }
            try {
                Runtime.getRuntime().exec(arrayOf("chmod", "-R", "755", prootDir.absolutePath)).waitFor()
            } catch (_: Exception) {}

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

            // 5. Populate and prepare standalone guest rootfs
            setupRootfs()
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
        val rootfsAgy = File(rootfsDir, "bin/agy")
        if (rootfsAgy.exists() && rootfsAgy.length() > 150000000L) return rootfsAgy

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
        val rootfsLd = File(rootfsDir, "lib/ld-linux-aarch64.so.1")
        if (rootfsLd.exists()) return rootfsLd
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
    var cachedAgyVersion: String = "1.1.27"
    @Volatile
    private var isFetchingVersion = false

    fun getAgyVersion(): String {
        if (!isFetchingVersion && cachedAgyVersion == "1.1.27") {
            isFetchingVersion = true
            Thread {
                try {
                    val agy = findAgyBinary()
                    if (agy != null) {
                        val pb = buildProcess(listOf(agy.absolutePath, "--version"), workspaceDir)
                        val p = pb.start()
                        val sb = StringBuilder()
                        val readerThread = Thread {
                            try {
                                p.inputStream.bufferedReader().useLines { lines ->
                                    lines.forEach { sb.append(it).append("\n") }
                                }
                            } catch (_: Exception) {}
                        }.apply { isDaemon = true; start() }
                        val completed = p.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)
                        if (!completed) {
                            p.destroyForcibly()
                        }
                        readerThread.join(500)
                        val text = sb.toString().trim()
                        if (text.isNotEmpty()) {
                            cachedAgyVersion = text
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    isFetchingVersion = false
                }
            }.start()
        }
        return cachedAgyVersion
    }

    fun findProotBinary(): File? {
        val candidates = listOf(
            File(File(runtimeDir, "proot"), "proot"),
            File("/data/data/com.termux/files/usr/bin/proot")
        )
        return candidates.firstOrNull { it.exists() && (it.canExecute() || it.length() > 0) }
    }

    @Volatile
    var requiresProot: Boolean = true

    fun buildProcess(
        command: List<String>,
        cwd: File = workspaceDir,
        customEnv: Map<String, String> = emptyMap(),
        forceProot: Boolean = true
    ): ProcessBuilder {
        val agy = findAgyBinary()
        val proot = findProotBinary()
        val finalCmd = mutableListOf<String>()

        val isExecutingAgy = command.isNotEmpty() && (command[0].endsWith("agy") || command[0] == "agy" || (agy != null && command[0] == agy.absolutePath))

        if (isExecutingAgy && proot != null && rootfsDir.exists() && forceProot) {
            finalCmd.add(proot.absolutePath)
            finalCmd.add("--kill-on-exit")
            finalCmd.add("--link2symlink")
            finalCmd.add("-0")

            val canonRootfs = try { rootfsDir.canonicalPath } catch (_: Exception) { rootfsDir.absolutePath }
            finalCmd.add("-r")
            finalCmd.add(canonRootfs)

            finalCmd.add("-b")
            finalCmd.add("/dev:/dev")
            finalCmd.add("-b")
            finalCmd.add("/proc:/proc")
            finalCmd.add("-b")
            finalCmd.add("/sys:/sys")
            finalCmd.add("-b")
            finalCmd.add("/system:/system")

            if (File("/apex").exists()) {
                finalCmd.add("-b")
                finalCmd.add("/apex:/apex")
            }
            if (File("/vendor").exists()) {
                finalCmd.add("-b")
                finalCmd.add("/vendor:/vendor")
            }
            if (File("/data").exists()) {
                finalCmd.add("-b")
                finalCmd.add("/data:/data")
            }
            if (File("/linkerconfig/ld.config.txt").exists()) {
                finalCmd.add("-b")
                finalCmd.add("/linkerconfig/ld.config.txt:/linkerconfig/ld.config.txt")
            }

            val canonHome = try { homeDir.canonicalPath } catch (_: Exception) { homeDir.absolutePath }
            finalCmd.add("-b")
            finalCmd.add("$canonHome:/root")
            finalCmd.add("-b")
            finalCmd.add("$canonHome:/home")

            val targetCwd = if (cwd.exists()) cwd else workspaceDir
            val canonCwd = try { targetCwd.canonicalPath } catch (_: Exception) { targetCwd.absolutePath }
            finalCmd.add("-b")
            finalCmd.add("$canonCwd:/workspace")

            val tmpDir = File(runtimeDir, "tmp").apply { mkdirs() }
            val canonTmp = try { tmpDir.canonicalPath } catch (_: Exception) { tmpDir.absolutePath }
            finalCmd.add("-b")
            finalCmd.add("$canonTmp:/tmp")

            finalCmd.add("-w")
            finalCmd.add("/workspace")

            finalCmd.add("/bin/agy")
            finalCmd.addAll(command.drop(1))
        } else {
            val ld = getLdLinux()
            if (isExecutingAgy && ld != null) {
                finalCmd.add(ld.absolutePath)
                finalCmd.add("--library-path")
                finalCmd.add(glibcDir.absolutePath)
                finalCmd.add(command[0])
                finalCmd.addAll(command.drop(1))
            } else {
                finalCmd.addAll(command)
            }
        }

        val pb = ProcessBuilder(finalCmd)
        pb.directory(if (cwd.exists()) cwd else workspaceDir)
        val env = pb.environment()

        env["HOME"] = "/root"
        val tmpDir = File(rootfsDir, "tmp").apply { mkdirs() }
        val canonTmp = try { tmpDir.canonicalPath } catch (_: Exception) { tmpDir.absolutePath }
        env["TMPDIR"] = canonTmp
        env["PROOT_TMP_DIR"] = canonTmp
        env["PROOT_NO_SECCOMP"] = "1"
        env["PROOT_IGNORE_MISSING_BINDINGS"] = "1"
        env["LANG"] = "en_US.UTF-8"
        env["LC_ALL"] = "en_US.UTF-8"
        env["GLIBC_TUNABLES"] = "glibc.pthread.rseq=0"
        env["NO_COLOR"] = "1"
        env["SSL_CERT_FILE"] = "/etc/ssl/certs/ca-certificates.crt"
        env["SSL_CERT_DIR"] = "/etc/ssl/certs"

        val prootDir = File(runtimeDir, "proot")
        val prootLoader = File(prootDir, "loader")
        val prootLoader32 = File(prootDir, "loader32")
        if (prootLoader.exists()) {
            env["PROOT_LOADER"] = prootLoader.absolutePath
        }
        if (prootLoader32.exists()) {
            env["PROOT_LOADER_32"] = prootLoader32.absolutePath
        }

        val currentLd = env["LD_LIBRARY_PATH"] ?: ""
        env["LD_LIBRARY_PATH"] = listOf(prootDir.absolutePath, glibcDir.absolutePath, currentLd).filter { it.isNotEmpty() }.joinToString(":")

        val currentPath = env["PATH"] ?: "/system/bin:/system/xbin"
        env["PATH"] = "/bin:/usr/bin:$currentPath"

        // Google OAuth Credentials for Antigravity (Application Default Credentials)
        val adcCandidates = listOf(
            File(homeDir, ".config/gcloud/application_default_credentials.json"),
            File(agyConfigDir, "application_default_credentials.json")
        )
        val adcFile = adcCandidates.firstOrNull { it.exists() && it.length() > 0 }
        if (adcFile != null) {
            val relPath = adcFile.relativeTo(homeDir).path.replace('\\', '/')
            env["GOOGLE_APPLICATION_CREDENTIALS"] = "/root/$relPath"
        }

        for ((k, v) in customEnv) {
            env[k] = v
        }
        return pb
    }
}
