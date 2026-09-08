package com.antigravity.pocketgravity.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.antigravity.pocketgravity.api.BridgeClient
import com.antigravity.pocketgravity.api.GitChange

class ChangesPanel(
    val context: Context,
    val bridgeClient: BridgeClient,
    val onOpenFile: (String) -> Unit
) {
    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(Color.parseColor("#121418"))
    }

    var workspace: String = "/root"

    private val titleTv = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        text = "Git Changes (0)"
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#F1F5F9"))
    }

    private val refreshBtn = Button(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 72)
        text = "Refresh"
        textSize = 11f
        setTextColor(Color.parseColor("#3B82F6"))
        setBackgroundColor(Color.TRANSPARENT)
    }

    private val topBar = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setPadding(24, 12, 24, 12)
        setBackgroundColor(Color.parseColor("#1A1D23"))
        addView(titleTv)
        addView(refreshBtn)
    }

    private val changesContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private val diffTextView = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        typeface = Typeface.MONOSPACE
        textSize = 11.5f
        setPadding(24, 24, 24, 24)
        setTextColor(Color.parseColor("#CBD5E1"))
        text = "Select a changed file above to view the diff."
    }

    private val diffScrollView = ScrollView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        setBackgroundColor(Color.parseColor("#0C0E12"))
        addView(diffTextView)
    }

    init {
        view.addView(topBar)
        view.addView(changesContainer)

        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.parseColor("#2C323D"))
        }
        view.addView(divider)
        view.addView(diffScrollView)

        refreshBtn.setOnClickListener {
            loadChanges()
        }

        loadChanges()
    }

    fun loadChanges() {
        bridgeClient.getGitChanges(workspace) { res ->
            res.onSuccess { list ->
                renderChanges(list)
            }.onFailure {
                titleTv.text = "Git Changes (not a git repo)"
                changesContainer.removeAllViews()
                diffTextView.text = "Current workspace is not a Git repository or git is unavailable."
            }
        }
    }

    private fun renderChanges(changes: List<GitChange>) {
        changesContainer.removeAllViews()
        titleTv.text = "Git Changes (${changes.size})"

        if (changes.isEmpty()) {
            val emptyTv = TextView(context).apply {
                text = "✓ Working tree clean. No pending changes."
                setTextColor(Color.parseColor("#10B981"))
                textSize = 12f
                setPadding(24, 24, 24, 24)
            }
            changesContainer.addView(emptyTv)
            diffTextView.text = "No changes to display."
            return
        }

        for (ch in changes) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 16, 24, 16)
                setBackgroundColor(Color.TRANSPARENT)
                isClickable = true

                val badge = TextView(context).apply {
                    text = ch.status
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(12, 4, 12, 4)
                    when (ch.status) {
                        "M" -> {
                            setTextColor(Color.parseColor("#60A5FA"))
                            setBackgroundColor(Color.parseColor("#1E2A38"))
                        }
                        "A", "??" -> {
                            setTextColor(Color.parseColor("#34D399"))
                            setBackgroundColor(Color.parseColor("#162E22"))
                        }
                        "D" -> {
                            setTextColor(Color.parseColor("#F87171"))
                            setBackgroundColor(Color.parseColor("#331A1E"))
                        }
                        else -> {
                            setTextColor(Color.parseColor("#94A3B8"))
                            setBackgroundColor(Color.parseColor("#222730"))
                        }
                    }
                }
                addView(badge)

                val pathTv = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = 16
                    }
                    text = ch.path
                    textSize = 12.5f
                    setTextColor(Color.parseColor("#F1F5F9"))
                    isSingleLine = true
                }
                addView(pathTv)

                val openBtn = TextView(context).apply {
                    text = "Edit ↗"
                    textSize = 11f
                    setTextColor(Color.parseColor("#3B82F6"))
                    setPadding(16, 8, 16, 8)
                    setOnClickListener {
                        val full = if (ch.path.startsWith("/")) ch.path else "$workspace/${ch.path}"
                        onOpenFile(full)
                    }
                }
                addView(openBtn)

                setOnClickListener {
                    loadDiff(ch.path)
                }
            }
            changesContainer.addView(row)
        }
    }

    private fun loadDiff(path: String) {
        diffTextView.text = "Loading diff for $path..."
        bridgeClient.getGitDiff(workspace, path) { res ->
            res.onSuccess { diff ->
                if (diff.isNotEmpty()) {
                    diffTextView.text = DiffColorizer.colorize(diff)
                } else {
                    diffTextView.text = "No unified diff available (untracked or binary file)."
                }
            }.onFailure { err ->
                diffTextView.text = "Failed to load diff: ${err.message}"
            }
        }
    }
}
