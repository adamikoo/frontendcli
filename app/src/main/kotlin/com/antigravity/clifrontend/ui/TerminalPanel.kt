package com.antigravity.clifrontend.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.antigravity.clifrontend.R
import com.antigravity.clifrontend.api.BridgeClient

class TerminalPanel(
    val context: Context,
    val bridgeClient: BridgeClient
) {
    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(Color.parseColor("#0C0E12"))
    }

    var cwd: String = "/root"

    private val terminalOutput = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        typeface = Typeface.MONOSPACE
        textSize = 11f
        setTextColor(Color.parseColor("#CCCCCC"))
        setPadding(20, 20, 20, 20)
        isFocusable = true
        setTextIsSelectable(true)
    }

    private val scrollView = ScrollView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        addView(terminalOutput)
    }

    // Quick Action Bar
    private val quickActionsBar = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
    private val quickActionsScrollView = HorizontalScrollView(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 72)
        setBackgroundColor(Color.parseColor("#15181E"))
        isHorizontalScrollBarEnabled = false
        addView(quickActionsBar)
    }

    // Command Input Bar
    private val promptLabel = TextView(context).apply {
        text = "$ "
        setTextColor(Color.parseColor("#10B981"))
        textSize = 13f
        typeface = Typeface.MONOSPACE
        typeface = Typeface.DEFAULT_BOLD
        setPadding(16, 0, 8, 0)
    }

    private val commandInput = EditText(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        hint = "Enter shell command..."
        setHintTextColor(Color.parseColor("#64748B"))
        setTextColor(Color.WHITE)
        textSize = 12.5f
        typeface = Typeface.MONOSPACE
        setBackgroundResource(R.drawable.bg_input)
        setPadding(16, 16, 16, 16)
        isSingleLine = true
    }

    private val runButton = Button(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 80).apply {
            marginStart = 12
        }
        text = "Run"
        textSize = 11.5f
        setTextColor(Color.WHITE)
        setBackgroundResource(R.drawable.bg_chip)
    }

    private val inputBar = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setPadding(12, 12, 12, 12)
        setBackgroundColor(Color.parseColor("#1A1D23"))
        addView(promptLabel)
        addView(commandInput)
        addView(runButton)
    }

    private val history = mutableListOf<String>()
    private var historyIndex = -1
    private val outputBuffer = SpannableStringBuilder()

    init {
        view.addView(scrollView)
        view.addView(quickActionsScrollView)
        view.addView(inputBar)

        initQuickActions()

        runButton.setOnClickListener {
            val cmd = commandInput.text.toString().trim()
            if (cmd.isNotEmpty()) {
                executeCommand(cmd)
            }
        }

        commandInput.setOnEditorActionListener { _, _, _ ->
            val cmd = commandInput.text.toString().trim()
            if (cmd.isNotEmpty()) {
                executeCommand(cmd)
            }
            true
        }

        appendOutput("CLIFrontend Integrated Terminal (Ubuntu 26.04 / aarch64)\nConnected to PRoot shell at $cwd\n\n")
    }

    private fun initQuickActions() {
        val actions = listOf("Clear", "Ctrl+C", "ls -la", "pwd", "git status", "agy help", "agy models")
        for (act in actions) {
            val btn = Button(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
                text = act
                textSize = 11f
                setTextColor(Color.parseColor("#94A3B8"))
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener {
                    when (act) {
                        "Clear" -> {
                            outputBuffer.clear()
                            terminalOutput.text = ""
                        }
                        "Ctrl+C" -> {
                            appendOutput("^C\n")
                        }
                        else -> {
                            commandInput.setText(act)
                            executeCommand(act)
                        }
                    }
                }
            }
            quickActionsBar.addView(btn)
        }
    }

    private fun executeCommand(cmd: String) {
        history.add(cmd)
        historyIndex = history.size
        commandInput.setText("")

        appendOutput("$ $cmd\n")

        bridgeClient.execTerminal(cmd, cwd) { res ->
            res.onSuccess { r ->
                if (r.stdout.isNotEmpty()) {
                    appendAnsiOutput(r.stdout)
                }
                if (r.stderr.isNotEmpty()) {
                    appendErrorOutput(r.stderr)
                }
                if (r.exitCode != 0) {
                    appendOutput("[Process exited with code ${r.exitCode}]\n")
                }
                scrollToBottom()
            }.onFailure { err ->
                appendErrorOutput("Command failed: ${err.message}\n")
                scrollToBottom()
            }
        }
    }

    private fun appendOutput(text: String) {
        outputBuffer.append(text)
        terminalOutput.text = outputBuffer
        scrollToBottom()
    }

    private fun appendAnsiOutput(text: String) {
        val parsed = AnsiColorParser.parse(text)
        outputBuffer.append(parsed)
        terminalOutput.text = outputBuffer
        scrollToBottom()
    }

    private fun appendErrorOutput(text: String) {
        val parsed = AnsiColorParser.parse(text)
        outputBuffer.append(parsed)
        terminalOutput.text = outputBuffer
        scrollToBottom()
    }

    private fun scrollToBottom() {
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
}
