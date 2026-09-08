package com.antigravity.pocketgravity.api

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

object AssetExporter {

    fun exportBridgeFiles(context: Context) {
        try {
            val assetManager = context.assets
            val bridgeDirName = "bridge"
            val files = listOf("start.sh", "server.py")

            // 1. Export to app external files directory (no permission needed)
            val extDir = File(context.getExternalFilesDir(null), "bridge")
            extDir.mkdirs()

            // 2. Export to public Download/frontendcli folder for easy Termux access
            val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "frontendcli")
            try {
                downloadDir.mkdirs()
            } catch (_: Exception) {}

            for (fileName in files) {
                try {
                    val inputStream = assetManager.open("$bridgeDirName/$fileName")
                    val extFile = File(extDir, fileName)
                    FileOutputStream(extFile).use { out ->
                        inputStream.copyTo(out)
                    }
                    extFile.setExecutable(true, false)

                    // Try writing to public downloads if accessible
                    if (downloadDir.exists() && downloadDir.canWrite()) {
                        val pubFile = File(downloadDir, fileName)
                        FileOutputStream(pubFile).use { out ->
                            extFile.inputStream().copyTo(out)
                        }
                        pubFile.setExecutable(true, false)
                    }
                } catch (e: Exception) {
                    DebugLogger.e("Failed to export $fileName", e)
                }
            }
        } catch (e: Exception) {
            DebugLogger.e("AssetExporter error", e)
        }
    }
}
