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
                val agyDesc = if (h.antigravityInstalled) {
                    val path = h.antigravityPath ?: ""
                    if (path.contains("standalone") || path.contains("embedded")) {
                        "Standalone Antigravity Engine (Active - zero external dependencies)"
                    } else {
                        "Installed (${h.antigravityVersion ?: "detected"}) at $path"
                    }
                } else {
                    "Antigravity CLI (agy) not found in Linux environment"
                }
                addTierCard("Antigravity CLI Runtime", h.antigravityInstalled, agyDesc)
                val adcExists = java.io.File(com.antigravity.pocketgravity.api.RuntimeManager.homeDir, ".config/gcloud/application_default_credentials.json").exists()
                val tokenFile = com.antigravity.pocketgravity.api.RuntimeManager.agyTokenFile
                val isGeminiKey = tokenFile.exists() && tokenFile.readText().trim().startsWith("AIza")
                val isAuth = h.authenticated || adcExists || isGeminiKey
                val authDesc = when {
                    isGeminiKey -> "Authenticated via Gemini API Key (Direct API active)"
                    adcExists -> "Authenticated via Google OAuth (ADC active)"
                    h.authenticated -> "Authenticated (Token present)"
                    else -> "Unauthenticated. Tap 'Sign in with Google' or paste a free Gemini API Key below."
                }
                addTierCard("Google / Gemini Authentication", isAuth, authDesc)

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
                        text = "Paste API Key / Token"
                        textSize = 11.5f
                        setTextColor(Color.WHITE)
                        setBackgroundResource(R.drawable.bg_chip)
                        setOnClickListener {
                            val input = EditText(context).apply {
                                hint = "Gemini API Key (AIza...) or Token"
                                setHintTextColor(Color.parseColor("#64748B"))
                                textAlignment = android.view.View.TEXT_ALIGNMENT_VIEW_START
                                setTextColor(Color.parseColor("#F8FAFC"))
                            }
                            AlertDialog.Builder(context)
                                .setTitle("Set API Key / Token")
                                .setMessage("Paste your free Gemini API Key (starts with AIza from aistudio.google.com) or Antigravity Token:")
                                .setView(input)
                                .setPositiveButton("Save") { _, _ ->
                                    val token = input.text.toString().trim()
                                    if (token.isNotEmpty()) {
                                        bridgeClient.saveToken(token) {
                                            Toast.makeText(context, "Credential saved!", Toast.LENGTH_SHORT).show()
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
                addTierCard("Embedded Bridge", false, "Bridge starting on http://127.0.0.1:8765 (${err.message ?: "Starting engine..."})")
                
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
                    text = "Start Embedded Engine"
                    setTextColor(Color.parseColor("#F1F5F9"))
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                }
                helperBox.addView(promptTitle)

                val promptDesc = TextView(context).apply {
                    text = "CLIFrontend runs completely standalone. Tap below to start or restart the embedded engine:"
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 11.5f
                    setPadding(0, 6, 0, 12)
                }
                helperBox.addView(promptDesc)

                val btnRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 8, 0, 0)
                }

                val startEngineBtn = Button(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = 8
                    }
                    text = "🚀 Start Engine"
                    textSize = 11.5f
                    setTextColor(Color.WHITE)
                    setBackgroundResource(R.drawable.bg_chip)
                    setOnClickListener {
                        com.antigravity.pocketgravity.api.BridgeService.start(context)
                        Toast.makeText(context, "Starting standalone engine...", Toast.LENGTH_SHORT).show()
                        postDelayed({ runDiagnostics() }, 1500)
                    }
                }
                btnRow.addView(startEngineBtn)

                val retryBtn = Button(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = 8
                    }
                    text = "🔄 Refresh"
                    textSize = 11.5f
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
