package com.antigravity.pocketgravity.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import com.antigravity.pocketgravity.R
import com.antigravity.pocketgravity.api.BridgeClient

class SettingsPanel(
    val context: Context,
    val bridgeClient: BridgeClient,
    val onSettingChanged: () -> Unit
) {
    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(Color.parseColor("#121418"))
        setPadding(28, 28, 28, 28)
    }

    private val titleTv = TextView(context).apply {
        text = "Pocket gravity Settings"
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#F1F5F9"))
        setPadding(0, 0, 0, 20)
    }

    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private val scrollView = ScrollView(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        addView(container)
    }

    init {
        view.addView(titleTv)
        view.addView(scrollView)

        buildSettingsUI()
    }

    private fun buildSettingsUI() {
        container.removeAllViews()

        // Section: Bridge
        addSectionHeader("Bridge Connection")
        addSettingRow("Bridge URL", bridgeClient.baseUrl) {
            val presets = arrayOf(
                "http://10.0.2.2:8765 (Android Emulator)",
                "http://192.168.1.104:8765 (Host PC Wi-Fi)",
                "http://127.0.0.1:8765 (Termux on Device / Local)",
                "Custom URL..."
            )
            AlertDialog.Builder(context)
                .setTitle("Select Bridge Target")
                .setItems(presets) { _, which ->
                    when (which) {
                        0 -> {
                            bridgeClient.baseUrl = "http://10.0.2.2:8765"
                            buildSettingsUI()
                            onSettingChanged()
                        }
                        1 -> {
                            bridgeClient.baseUrl = "http://192.168.1.104:8765"
                            buildSettingsUI()
                            onSettingChanged()
                        }
                        2 -> {
                            bridgeClient.baseUrl = "http://127.0.0.1:8765"
                            buildSettingsUI()
                            onSettingChanged()
                        }
                        3 -> {
                            val input = EditText(context)
                            input.setText(bridgeClient.baseUrl)
                            AlertDialog.Builder(context)
                                .setTitle("Custom Bridge URL")
                                .setView(input)
                                .setPositiveButton("Save") { _, _ ->
                                    val newUrl = input.text.toString().trim()
                                    if (newUrl.isNotEmpty()) {
                                        bridgeClient.baseUrl = newUrl
                                        buildSettingsUI()
                                        onSettingChanged()
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    }
                }
                .show()
        }

        val termuxCmd = "pkg install -y python curl && curl -sL https://raw.githubusercontent.com/adamikoo/frontendcli/main/bridge/start.sh -o ~/start.sh && bash ~/start.sh"
        addSettingRow("Termux Launch Command", "Tap to copy") {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Termux Command", termuxCmd)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Termux command copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        // Section: AI Configuration
        addSectionHeader("AI Agent Configuration")
        addSettingRow("Thinking / Reasoning Effort", "Tap to change") {
            val efforts = arrayOf("low", "medium", "high")
            AlertDialog.Builder(context)
                .setTitle("Select Reasoning Effort")
                .setItems(efforts) { _, which ->
                    bridgeClient.setEffort(efforts[which]) {
                        Toast.makeText(context, "Effort set to ${efforts[which]}", Toast.LENGTH_SHORT).show()
                        onSettingChanged()
                    }
                }
                .show()
        }

        addSettingRow("Sign in with Google", "Launch Google OAuth login in browser") {
            Toast.makeText(context, "Opening Google Sign-In in browser...", Toast.LENGTH_SHORT).show()
            bridgeClient.startAuthLogin { loginRes ->
                loginRes.onSuccess { url ->
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Toast.makeText(context, "Sign in in browser, then return to Pocket gravity!", Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    Toast.makeText(context, "Failed to get login URL: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        addSettingRow("Antigravity OAuth Token", "Tap to edit or paste") {
            val input = EditText(context).apply {
                hint = "Paste OAuth Token or Refresh Token"
                setHintTextColor(Color.parseColor("#64748B"))
                setTextColor(Color.parseColor("#F8FAFC"))
            }
            AlertDialog.Builder(context)
                .setTitle("Antigravity OAuth Token")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val newToken = input.text.toString().trim()
                    if (newToken.isNotEmpty()) {
                        bridgeClient.saveToken(newToken) {
                            Toast.makeText(context, "Token updated!", Toast.LENGTH_SHORT).show()
                            onSettingChanged()
                        }
                    } else {
                        bridgeClient.logout {
                            Toast.makeText(context, "Token cleared", Toast.LENGTH_SHORT).show()
                            onSettingChanged()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        addSettingRow("Log Out of Google", "Disconnect account") {
            AlertDialog.Builder(context)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to disconnect your Google account and clear tokens?")
                .setPositiveButton("Log Out") { _, _ ->
                    bridgeClient.logout {
                        Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                        onSettingChanged()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Section: Editor
        addSectionHeader("Editor Preferences")
        addSettingRow("Font Size", "12sp") {
            val sizes = arrayOf("10sp", "12sp", "14sp", "16sp")
            AlertDialog.Builder(context)
                .setTitle("Editor Font Size")
                .setItems(sizes) { _, which ->
                    Toast.makeText(context, "Font size updated", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        addSettingRow("Tab Size", "4 spaces") {
            val tabs = arrayOf("2 spaces", "4 spaces")
            AlertDialog.Builder(context)
                .setTitle("Tab Size")
                .setItems(tabs) { _, which ->
                    Toast.makeText(context, "Tab size updated", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        // Section: About
        addSectionHeader("About")
        addSettingRow("Application", "Pocket gravity v1.0.0") {}
        addSettingRow("Backend", "Google Antigravity CLI (Standalone Engine)") {}
    }

    private fun addSectionHeader(title: String) {
        val tv = TextView(context).apply {
            text = title
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#3B82F6"))
            setPadding(0, 24, 0, 10)
        }
        container.addView(tv)
    }

    private fun addSettingRow(label: String, value: String, onClick: () -> Unit) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_rounded_card)
            setPadding(24, 20, 24, 20)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 12
            }
            layoutParams = lp
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            val labelTv = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = label
                textSize = 13f
                setTextColor(Color.parseColor("#F1F5F9"))
                isClickable = false
                isFocusable = false
            }
            val valueTv = TextView(context).apply {
                text = value
                textSize = 11.5f
                setTextColor(Color.parseColor("#94A3B8"))
                isClickable = false
                isFocusable = false
            }
            val arrowTv = TextView(context).apply {
                text = "  ›"
                textSize = 14f
                setTextColor(Color.parseColor("#64748B"))
                isClickable = false
                isFocusable = false
            }
            addView(labelTv)
            addView(valueTv)
            addView(arrowTv)
        }
        container.addView(row)
    }
}
