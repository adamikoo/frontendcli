package com.antigravity.pocketgravity.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import com.antigravity.pocketgravity.R
import com.antigravity.pocketgravity.api.BridgeClient

class DiagnosticsPanel(
    val context: Context,
    val bridgeClient: BridgeClient,
    val onRefreshRequested: () -> Unit
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
        text = "System & Environment Diagnostics"
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#F1F5F9"))
        setPadding(0, 0, 0, 16)
    }

    private val cardsContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private val scrollView = ScrollView(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        addView(cardsContainer)
    }

    private val refreshButton = Button(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 88).apply {
            topMargin = 16
        }
        text = "Run Full Diagnostics"
        textSize = 12f
        setTextColor(Color.WHITE)
        setBackgroundResource(R.drawable.bg_chip)
    }

    init {
        view.addView(titleTv)
        view.addView(scrollView)
        view.addView(refreshButton)

        refreshButton.setOnClickListener {
            runDiagnostics()
        }

        runDiagnostics()
    }

    fun runDiagnostics() {
        cardsContainer.removeAllViews()

        val loadingTv = TextView(context).apply {
            text = "Probing Termux, Ubuntu, Antigravity, and Bridge..."
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(16, 16, 16, 16)
        }
        cardsContainer.addView(loadingTv)

        bridgeClient.getHealth { res ->
            cardsContainer.removeAllViews()

            res.onSuccess { h ->
                val isStandalone = h.standalone || com.antigravity.pocketgravity.api.RuntimeManager.isStandaloneRuntimeReady()
                val runtimeTitle = if (isStandalone) "Embedded PRoot Engine" else "Termux Environment"
                val runtimeDesc = if (isStandalone) {
                    "Standalone Runtime Active (Embedded PRoot userspace emulation + Glibc ARM64)"
                } else {
                    val termuxInstalled = try {
                        context.packageManager.getPackageInfo("com.termux", 0) != null
                    } catch (_: Exception) { false }
                    if (termuxInstalled) "Termux App Installed" else "External bridge active"
                }
                addTierCard(runtimeTitle, true, runtimeDesc)

                addTierCard("Bridge Daemon", true, "Connected to ${bridgeClient.baseUrl} (${h.workspace})")

                val agyDesc = if (h.antigravityInstalled) {
                    val ver = h.antigravityVersion ?: "active"
                    "Official Antigravity CLI ($ver) at ${h.antigravityPath ?: "agy"}"
                } else {
                    "Antigravity CLI (agy) not found in Linux environment"
                }
                addTierCard("Antigravity CLI Runtime", h.antigravityInstalled, agyDesc)

                val isAuth = h.authenticated
                val authDesc = if (isAuth) {
                    "Authenticated with Google OAuth (${h.tokenFile ?: "Token active"})"
                } else {
                    "Unauthenticated. Tap 'Sign in with Google' or paste an Antigravity OAuth Token below."
                }
                addTierCard("Antigravity Authentication", isAuth, authDesc)

                val actionRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = 12
                    }
                }

                if (!isAuth) {
                    val loginBtn = Button(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = 6
                        }
                        text = "Sign in with Google"
                        textSize = 11.5f
                        setTextColor(Color.WHITE)
                        setBackgroundResource(R.drawable.bg_chip)
                        setOnClickListener {
                            Toast.makeText(context, "Opening Google Sign-In in browser...", Toast.LENGTH_SHORT).show()
                            bridgeClient.startAuthLogin { loginRes ->
                                loginRes.onSuccess { url ->
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                    Toast.makeText(context, "Sign in in browser, then return to Pocket gravity!", Toast.LENGTH_LONG).show()
                                }.onFailure { err ->
                                    AlertDialog.Builder(context)
                                        .setTitle("Sign-in Error")
                                        .setMessage(err.message ?: "Failed to start sign-in")
                                        .setPositiveButton("OK", null)
                                        .show()
                                }
                            }
                        }
                    }
                    actionRow.addView(loginBtn)

                    val tokenBtn = Button(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginStart = 6
                        }
                        text = "Paste OAuth Token"
                        textSize = 11.5f
                        setTextColor(Color.WHITE)
                        setBackgroundResource(R.drawable.bg_chip)
                        setOnClickListener {
                            val input = EditText(context).apply {
                                hint = "Antigravity OAuth Token or Refresh Token"
                                setHintTextColor(Color.parseColor("#64748B"))
                                textAlignment = android.view.View.TEXT_ALIGNMENT_VIEW_START
                                setTextColor(Color.parseColor("#F8FAFC"))
                            }
                            AlertDialog.Builder(context)
                                .setTitle("Set Antigravity Token")
                                .setMessage("Paste your Antigravity Google OAuth Token or Refresh Token:")
                                .setView(input)
                                .setPositiveButton("Save") { _, _ ->
                                    val token = input.text.toString().trim()
                                    if (token.isNotEmpty()) {
                                        bridgeClient.saveToken(token) {
                                            Toast.makeText(context, "Token saved!", Toast.LENGTH_SHORT).show()
                                            runDiagnostics()
                                            onRefreshRequested()
                                        }
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    }
                    actionRow.addView(tokenBtn)
                } else {
                    val logoutBtn = Button(context).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        text = "🚪 Log Out / Disconnect Google Account"
                        textSize = 12f
                        setTextColor(Color.parseColor("#F87171"))
                        setBackgroundResource(R.drawable.bg_chip)
                        setOnClickListener {
                            AlertDialog.Builder(context)
                                .setTitle("Log Out")
                                .setMessage("Are you sure you want to log out and clear your Antigravity Google token?")
                                .setPositiveButton("Log Out") { _, _ ->
                                    bridgeClient.logout {
                                        Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                                        runDiagnostics()
                                        onRefreshRequested()
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    }
                    actionRow.addView(logoutBtn)
                }

                cardsContainer.addView(actionRow)
                addConsoleCard()
            }.onFailure { err ->
                val standaloneReady = com.antigravity.pocketgravity.api.RuntimeManager.isStandaloneRuntimeReady()
                addTierCard("Embedded PRoot Engine", standaloneReady, if (standaloneReady) "Standalone Runtime files ready" else "Initializing runtime assets...")
                addTierCard("Bridge Daemon", false, "Bridge offline at ${bridgeClient.baseUrl} (${err.message ?: "Connection refused"})")

                val helperBox = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.drawable.bg_rounded_card)
                    setPadding(24, 24, 24, 24)
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = 16
                    }
                    layoutParams = lp
                }

                val promptTitle = TextView(context).apply {
                    text = "⚡ Engine & Bridge Launcher"
                    setTextColor(Color.parseColor("#F1F5F9"))
                    textSize = 13.5f
                    typeface = Typeface.DEFAULT_BOLD
                }
                helperBox.addView(promptTitle)

                val promptDesc = TextView(context).apply {
                    text = "Tap 'Start Engine' to launch the in-app standalone engine, or copy the Termux launcher command below:"
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 11.5f
                    setPadding(0, 6, 0, 10)
                }
                helperBox.addView(promptDesc)

                val cmdText = "bash /sdcard/Download/frontendcli/start.sh"
                val codeBox = TextView(context).apply {
                    text = cmdText
                    setTextColor(Color.parseColor("#38BDF8"))
                    typeface = Typeface.MONOSPACE
                    textSize = 11.5f
                    setPadding(16, 12, 16, 12)
                    setBackgroundColor(Color.parseColor("#0F172A"))
                }
                helperBox.addView(codeBox)

                val btnRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 12, 0, 0)
                }

                val startEngineBtn = Button(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = 4
                    }
                    text = "🚀 Start"
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    setBackgroundResource(R.drawable.bg_chip)
                    setOnClickListener {
                        com.antigravity.pocketgravity.api.BridgeService.start(context)
                        Toast.makeText(context, "Starting standalone engine...", Toast.LENGTH_SHORT).show()
                        postDelayed({ runDiagnostics() }, 1500)
                    }
                }
                btnRow.addView(startEngineBtn)

                val copyBtn = Button(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = 4
                        marginEnd = 4
                    }
                    text = "📋 Copy"
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    setBackgroundResource(R.drawable.bg_chip)
                    setOnClickListener {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Termux Command", cmdText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Command copied! Switch to Termux and paste.", Toast.LENGTH_SHORT).show()
                    }
                }
                btnRow.addView(copyBtn)

                val openTermuxBtn = Button(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = 4
                        marginEnd = 4
                    }
                    text = "📱 Termux"
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    setBackgroundResource(R.drawable.bg_chip)
                    setOnClickListener {
                        try {
                            val intent = context.packageManager.getLaunchIntentForPackage("com.termux")
                            if (intent != null) {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, "Termux app not installed.", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open Termux: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                btnRow.addView(openTermuxBtn)

                val retryBtn = Button(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = 4
                    }
                    text = "🔄 Refresh"
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    setBackgroundResource(R.drawable.bg_chip)
                    setOnClickListener {
                        runDiagnostics()
                    }
                }
                btnRow.addView(retryBtn)

                helperBox.addView(btnRow)
                cardsContainer.addView(helperBox)
                addConsoleCard()
            }
        }
    }

    private fun addConsoleCard() {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_rounded_card)
            setPadding(20, 16, 20, 16)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 16
                bottomMargin = 16
            }
            layoutParams = lp

            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                val titleView = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    text = "Live Bridge & System Log"
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#3B82F6"))
                }
                val btnClear = TextView(context).apply {
                    text = "Clear"
                    textSize = 11f
                    setTextColor(Color.parseColor("#94A3B8"))
                    setPadding(10, 4, 10, 4)
                    setOnClickListener {
                        com.antigravity.pocketgravity.api.DebugLogger.clear()
                        runDiagnostics()
                    }
                }
                addView(titleView)
                addView(btnClear)
            }
            addView(header)

            val logTv = TextView(context).apply {
                text = com.antigravity.pocketgravity.api.DebugLogger.getAllText().ifEmpty { "No errors logged. Engine ready." }
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(0, 10, 0, 0)
            }
            addView(logTv)
        }
        cardsContainer.addView(card)
    }

    private fun addTierCard(title: String, success: Boolean, details: String) {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_rounded_card)
            setPadding(24, 20, 24, 20)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 16
            }
            layoutParams = lp

            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                val titleView = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    text = title
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#F1F5F9"))
                }
                val statusView = TextView(context).apply {
                    text = if (success) "✓ OK" else "✕ FAIL"
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (success) Color.parseColor("#10B981") else Color.parseColor("#EF4444"))
                }
                addView(titleView)
                addView(statusView)
            }
            addView(header)

            val detailView = TextView(context).apply {
                text = details
                textSize = 11.5f
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(0, 8, 0, 0)
            }
            addView(detailView)
        }
        cardsContainer.addView(card)
    }

    private fun sp(): Float = 15f
}
