package com.antigravity.clifrontend.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import com.antigravity.clifrontend.R
import com.antigravity.clifrontend.api.BridgeClient

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
                addTierCard("Termux Environment", h.termux, if (h.termux) "Termux installed at /data/data/com.termux" else "Termux package not found")
                addTierCard("Ubuntu PRoot Distro", h.ubuntu, if (h.ubuntu) "Ubuntu container detected and operational" else "Ubuntu rootfs not found")
                addTierCard("Antigravity CLI", h.antigravityInstalled, if (h.antigravityInstalled) "Installed (${h.antigravityVersion ?: "1.1.27"}) at ${h.antigravityPath}" else "agy binary missing from PATH")
                addTierCard("Authentication", h.authenticated, if (h.authenticated) "Authenticated via Google OAuth (Token present)" else "Unauthenticated. Login required to run agent.")
                addTierCard("Localhost Bridge", true, "Connected to http://127.0.0.1:8765 (Active)")

                if (!h.authenticated) {
                    val loginBtn = Button(context).apply {
                        text = "Sign in with Google"
                        setTextColor(Color.WHITE)
                        setBackgroundResource(R.drawable.bg_chip)
                        setOnClickListener {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://antigravity.google"))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    }
                    cardsContainer.addView(loginBtn)
                }
            }.onFailure { err ->
                addTierCard("Localhost Bridge", false, "Bridge unreachable on http://127.0.0.1:8765 (${err.message ?: "Connection refused"}).")
                
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
                    text = "Start Bridge in Termux"
                    setTextColor(Color.parseColor("#F1F5F9"))
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                }
                helperBox.addView(promptTitle)

                val promptDesc = TextView(context).apply {
                    text = "Run this universal 1-line command in Termux. It works from any directory on any device:"
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 11.5f
                    setPadding(0, 6, 0, 12)
                }
                helperBox.addView(promptDesc)

                val termuxCmd = "pkg install -y python curl && curl -sL https://raw.githubusercontent.com/adamikoo/frontendcli/main/bridge/start.sh -o ~/start.sh && bash ~/start.sh"

                val cmdTv = TextView(context).apply {
                    text = termuxCmd
                    setTextColor(Color.parseColor("#38BDF8"))
                    typeface = Typeface.MONOSPACE
                    textSize = 11f
                    setPadding(16, 16, 16, 16)
                    setBackgroundColor(Color.parseColor("#0F172A"))
                }
                helperBox.addView(cmdTv)

                val btnRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 14, 0, 0)
                }

                val copyBtn = Button(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = 8
                    }
                    text = "📋 Copy Command"
                    textSize = 11.5f
                    setTextColor(Color.WHITE)
                    setBackgroundResource(R.drawable.bg_chip)
                    setOnClickListener {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Termux Command", termuxCmd)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Command copied to clipboard! Paste into Termux.", Toast.LENGTH_SHORT).show()
                    }
                }
                btnRow.addView(copyBtn)

                val openTermuxBtn = Button(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = 8
                    }
                    text = "🚀 Open Termux"
                    textSize = 11.5f
                    setTextColor(Color.WHITE)
                    setBackgroundResource(R.drawable.bg_chip)
                    setOnClickListener {
                        try {
                            val intent = context.packageManager.getLaunchIntentForPackage("com.termux")
                            if (intent != null) {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, "Termux not installed. Install from F-Droid or Play Store.", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not launch Termux: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                btnRow.addView(openTermuxBtn)

                helperBox.addView(btnRow)
                cardsContainer.addView(helperBox)
            }
        }
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
