package com.antigravity.pocketgravity.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.antigravity.pocketgravity.R
import com.antigravity.pocketgravity.api.BridgeClient
import com.antigravity.pocketgravity.api.ChatMessage
import org.json.JSONObject

class AgentPanel(
    val context: Context,
    val bridgeClient: BridgeClient,
    val onStatusChange: (String) -> Unit
) {
    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(Color.parseColor("#101216"))
    }

    // Top Agent Sub-Header (Antigravity style)
    private val subHeader = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setPadding(20, 14, 20, 10)

        val titleTv = TextView(context).apply {
            text = "Agent"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E2E8F0"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        addView(titleTv)

        val btnNewChat = TextView(context).apply {
            text = "+"
            textSize = 20f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(14, 0, 14, 0)
            setOnClickListener { resetToEmptyChat() }
        }
        addView(btnNewChat)

        val btnHistory = TextView(context).apply {
            text = "🕒"
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(10, 0, 10, 0)
            setOnClickListener { showSessionHistory() }
        }
        addView(btnHistory)

        val btnMenu = TextView(context).apply {
            text = "•••"
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(10, 0, 0, 0)
        }
        addView(btnMenu)
    }

    private val messagesContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setPadding(20, 16, 20, 16)
    }

    private val scrollView = ScrollView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        addView(messagesContainer)
    }

    // Antigravity Signature Floating Input Card
    private val inputCard = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_antigravity_input_card)
        setPadding(18, 16, 18, 14)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(16, 8, 16, 4)
        layoutParams = lp
    }

    private val inputEditText = EditText(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        hint = "Ask anything, @ to mention, / for workflows"
        setHintTextColor(Color.parseColor("#64748B"))
        setTextColor(Color.parseColor("#F8FAFC"))
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(0, 4, 0, 12)
        textSize = 14f
        maxLines = 6
    }

    // Inside-Card Controls Bar
    private val cardControlsBar = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private val btnAddContext = TextView(context).apply {
        text = "+"
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#94A3B8"))
        setPadding(4, 0, 12, 0)
        setOnClickListener {
            Toast.makeText(context, "Attach file or context (@)", Toast.LENGTH_SHORT).show()
        }
    }

    private val pillMode = TextView(context).apply {
        text = "Planning ▾"
        textSize = 11.5f
        setTextColor(Color.parseColor("#CBD5E1"))
        setBackgroundResource(R.drawable.bg_pill_dropdown)
        setPadding(14, 6, 14, 6)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = 8
        }
        layoutParams = lp
        setOnClickListener { showModePicker() }
    }

    private val pillModel = TextView(context).apply {
        text = "Gemini 3.8 Flash ▾"
        textSize = 11.5f
        setTextColor(Color.parseColor("#CBD5E1"))
        setBackgroundResource(R.drawable.bg_pill_dropdown)
        setPadding(14, 6, 14, 6)
        setOnClickListener { showModelPicker() }
    }

    private val spacer = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
    }

    private val sendButton = ImageButton(context).apply {
        layoutParams = LinearLayout.LayoutParams(76, 76)
        setImageResource(R.drawable.ic_play)
        setColorFilter(Color.WHITE)
        setBackgroundResource(R.drawable.bg_send_circle)
        setPadding(16, 16, 16, 16)
    }

    private val stopButton = ImageButton(context).apply {
        layoutParams = LinearLayout.LayoutParams(76, 76)
        setImageResource(R.drawable.ic_stop)
        setColorFilter(Color.parseColor("#EF4444"))
        setBackgroundResource(R.drawable.bg_pill_dropdown)
        setPadding(16, 16, 16, 16)
        visibility = View.GONE
    }

    // Disclaimer footer
    private val disclaimerTv = TextView(context).apply {
        text = "AI may make mistakes. Double-check all generated code."
        textSize = 10.5f
        setTextColor(Color.parseColor("#475569"))
        gravity = Gravity.CENTER
        setPadding(0, 6, 0, 10)
    }

    private var currentMode = "Planning"
    private var isStreaming = false
    private var isWelcomeState = true

    init {
        // Assemble inside-card controls
        cardControlsBar.addView(btnAddContext)
        cardControlsBar.addView(pillMode)
        cardControlsBar.addView(pillModel)
        cardControlsBar.addView(spacer)
        cardControlsBar.addView(sendButton)
        cardControlsBar.addView(stopButton)

        // Assemble input card
        inputCard.addView(inputEditText)
        inputCard.addView(cardControlsBar)

        // Assemble main view
        view.addView(subHeader)
        view.addView(scrollView)
        view.addView(inputCard)
        view.addView(disclaimerTv)

        sendButton.setOnClickListener {
            val prompt = inputEditText.text.toString().trim()
            if (prompt.isNotEmpty() && !isStreaming) {
                sendPrompt(prompt)
            }
        }

        stopButton.setOnClickListener {
            bridgeClient.stopAgent {
                onStatusChange(context.getString(R.string.status_ready))
                stopButton.visibility = View.GONE
                sendButton.visibility = View.VISIBLE
                isStreaming = false
            }
        }

        showWelcomeState()
    }

    private fun showWelcomeState() {
        messagesContainer.removeAllViews()
        isWelcomeState = true

        val centerBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20, 60, 20, 40)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val logo = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(160, 160)
            setImageResource(R.drawable.ic_launcher)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        centerBox.addView(logo)

        val title = TextView(context).apply {
            text = "Pocket gravity"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F1F5F9"))
            setPadding(0, 14, 0, 24)
        }
        centerBox.addView(title)

        // Shortcut Action Rows (matching Antigravity welcome UI)
        centerBox.addView(createShortcutRow("Switch to Agent Manager", "⌘ E") {
            Toast.makeText(context, "Agent Manager Active", Toast.LENGTH_SHORT).show()
        })
        centerBox.addView(createShortcutRow("Code with Agent", "⌘ L") {
            inputEditText.requestFocus()
        })
        centerBox.addView(createShortcutRow("Edit code inline", "⌘ I") {
            inputEditText.setText("/edit ")
            inputEditText.setSelection(inputEditText.text.length)
            inputEditText.requestFocus()
        })

        messagesContainer.addView(centerBox)
    }

    private fun createShortcutRow(label: String, shortcut: String, onClick: () -> Unit): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundResource(R.drawable.bg_rounded_card)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 10
            }
            layoutParams = lp
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            val labelTv = TextView(context).apply {
                text = label
                textSize = 12.5f
                setTextColor(Color.parseColor("#94A3B8"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val keyTv = TextView(context).apply {
                text = shortcut
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#64748B"))
                setBackgroundResource(R.drawable.bg_chip)
                setPadding(10, 4, 10, 4)
            }
            addView(labelTv)
            addView(keyTv)
        }
    }

    private fun resetToEmptyChat() {
        showWelcomeState()
        inputEditText.setText("")
    }

    private fun showModePicker() {
        val modes = arrayOf("Planning", "Fast Execution", "Code Review", "Accept Edits")
        AlertDialog.Builder(context)
            .setTitle("Agent Execution Mode")
            .setItems(modes) { _, which ->
                currentMode = modes[which].split(" ")[0]
                pillMode.text = "$currentMode ▾"
            }
            .show()
    }

    private fun showModelPicker() {
        bridgeClient.getModels { res ->
            res.onSuccess { models ->
                val names = models.map { it.name }.toTypedArray()
                AlertDialog.Builder(context)
                    .setTitle("Select Antigravity Model")
                    .setItems(names) { _, which ->
                        val selected = models[which]
                        bridgeClient.setModel(selected.name) {
                            pillModel.text = "${selected.name.take(16)}... ▾"
                        }
                    }
                    .show()
            }.onFailure {
                Toast.makeText(context, "Cannot load models: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSessionHistory() {
        Toast.makeText(context, "Loading conversations...", Toast.LENGTH_SHORT).show()
    }

    private fun addUserMessage(prompt: String) {
        if (isWelcomeState) {
            messagesContainer.removeAllViews()
            isWelcomeState = false
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_antigravity_input_card)
            setPadding(20, 16, 20, 16)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 16
            }
            layoutParams = lp

            val label = TextView(context).apply {
                text = "You"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#3B82F6"))
            }
            val content = TextView(context).apply {
                text = prompt
                textSize = 13.5f
                setTextColor(Color.parseColor("#F8FAFC"))
                setPadding(0, 6, 0, 0)
            }
            addView(label)
            addView(content)
        }
        messagesContainer.addView(card)
        scrollToBottom()
    }

    private fun startAgentResponse(): Pair<TextView, TextView> {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_rounded_card)
            setPadding(22, 18, 22, 18)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 16
            }
            layoutParams = lp
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val label = TextView(context).apply {
                text = "Antigravity Agent"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#10B981"))
            }
            addView(label)
        }
        card.addView(header)

        val toolStatus = TextView(context).apply {
            visibility = View.GONE
            textSize = 11f
            setTextColor(Color.parseColor("#F59E0B"))
            typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 4)
        }
        card.addView(toolStatus)

        val responseText = TextView(context).apply {
            text = "Thinking..."
            textSize = 13.5f
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(0, 6, 0, 0)
        }
        card.addView(responseText)

        messagesContainer.addView(card)
        scrollToBottom()
        return Pair(responseText, toolStatus)
    }

    private fun sendPrompt(prompt: String) {
        inputEditText.setText("")
        addUserMessage(prompt)

        isStreaming = true
        sendButton.visibility = View.GONE
        stopButton.visibility = View.VISIBLE
        onStatusChange(context.getString(R.string.status_thinking))

        val (respTv, toolTv) = startAgentResponse()
        val fullTextBuilder = StringBuilder()

        bridgeClient.streamAgent(
            prompt = prompt,
            onEvent = { event ->
                try {
                    val eventName = event.optString("event", event.optString("type", event.optString("method", "")))
                    val step = event.optJSONObject("step_update") ?: event.optJSONObject("params") ?: event

                    // ACP & stream-json tool execution
                    val toolCallObj = step.optJSONObject("toolCall")
                    val toolName = when {
                        step.has("tool") -> step.optString("tool", "")
                        step.has("tool_name") -> step.optString("tool_name", "")
                        step.has("step_type") -> step.optString("step_type", "")
                        toolCallObj != null -> toolCallObj.optString("title", "")
                        else -> ""
                    }
                    if (toolName.isNotEmpty() && (toolName.contains("tool") || toolName == "command" || toolName.contains("run") || toolName.contains("edit"))) {
                        onStatusChange(context.getString(R.string.status_running))
                        toolTv.visibility = View.VISIBLE
                        toolTv.setTextColor(Color.parseColor("#F59E0B"))
                        toolTv.text = "⚙ $toolName"
                    }

                    // ACP & stream-json thought / reasoning
                    val thoughtDelta = when {
                        step.has("thought_delta") -> step.optString("thought_delta", "")
                        step.has("thoughtDelta") -> step.optString("thoughtDelta", "")
                        step.has("thought") -> step.optString("thought", "")
                        else -> ""
                    }
                    if (thoughtDelta.isNotEmpty()) {
                        onStatusChange(context.getString(R.string.status_thinking))
                        toolTv.visibility = View.VISIBLE
                        toolTv.setTextColor(Color.parseColor("#94A3B8"))
                        toolTv.text = "💭 Thinking: ${thoughtDelta.take(60)}..."
                    }

                    // ACP & stream-json text delta
                    val delta = when {
                        step.has("text_delta") -> step.optString("text_delta", "")
                        step.has("textDelta") -> step.optString("textDelta", "")
                        step.has("delta") -> step.optString("delta", "")
                        event.has("delta") -> event.optString("delta", "")
                        step.has("content") -> step.optString("content", "")
                        else -> ""
                    }
                    if (delta.isNotEmpty()) {
                        onStatusChange(context.getString(R.string.status_working))
                        if (fullTextBuilder.isEmpty()) respTv.text = ""
                        fullTextBuilder.append(delta)
                        respTv.text = fullTextBuilder.toString()
                        scrollToBottom()
                    }

                    // Error events
                    val err = when {
                        event.has("error") -> event.optString("error", "")
                        step.has("error") -> step.optString("error", "")
                        else -> ""
                    }
                    if (err.isNotEmpty()) {
                        onStatusChange(context.getString(R.string.status_error))
                        respTv.setTextColor(Color.parseColor("#EF4444"))
                        if (fullTextBuilder.isEmpty()) respTv.text = ""
                        fullTextBuilder.append(err).append("\n")
                        respTv.text = fullTextBuilder.toString()
                        scrollToBottom()
                    }

                    // Result / Usage metrics / Final response
                    val res = event.optJSONObject("result") ?: step.optJSONObject("result")
                    if (res != null) {
                        val resResponse = res.optString("response", "")
                        val resError = res.optString("error", "")
                        if (resResponse.isNotEmpty() && fullTextBuilder.isEmpty()) {
                            fullTextBuilder.append(resResponse)
                            respTv.text = fullTextBuilder.toString()
                            scrollToBottom()
                        } else if (resError.isNotEmpty() && fullTextBuilder.isEmpty()) {
                            onStatusChange(context.getString(R.string.status_error))
                            respTv.setTextColor(Color.parseColor("#EF4444"))
                            val displayErr = if (resError.contains("authentication", ignoreCase = true)) {
                                "⚠️ Google Authentication Required\n\nPlease go to Settings (⚙) and tap 'Sign in with Google' to authenticate Pocket gravity."
                            } else {
                                "⚠️ Error: $resError"
                            }
                            fullTextBuilder.append(displayErr)
                            respTv.text = fullTextBuilder.toString()
                            scrollToBottom()
                        }
                        val usage = res.optJSONObject("usage")
                        if (usage != null) {
                            val total = usage.optInt("total_tokens", usage.optInt("totalTokens", 0))
                            if (total > 0) {
                                toolTv.visibility = View.VISIBLE
                                toolTv.setTextColor(Color.parseColor("#64748B"))
                                toolTv.text = "Tokens: $total"
                            }
                        }
                    }
                } catch (e: Throwable) {
                    com.antigravity.pocketgravity.api.DebugLogger.e("Agent onEvent error", e)
                }
            },
            onComplete = { exitCode ->
                try {
                    isStreaming = false
                    sendButton.visibility = View.VISIBLE
                    stopButton.visibility = View.GONE
                    if (exitCode != 0) {
                        onStatusChange(context.getString(R.string.status_error))
                        if (fullTextBuilder.isEmpty()) {
                            respTv.setTextColor(Color.parseColor("#EF4444"))
                            respTv.text = "⚠️ Process exited with code $exitCode.\nCheck Diagnostics or logcat for details."
                        }
                    } else {
                        onStatusChange(context.getString(R.string.status_ready))
                        if (fullTextBuilder.isEmpty()) {
                            respTv.text = "Turn completed (no output)."
                        }
                    }
                } catch (e: Throwable) {
                    com.antigravity.pocketgravity.api.DebugLogger.e("Agent onComplete error", e)
                }
            },
            onError = { err ->
                try {
                    isStreaming = false
                    sendButton.visibility = View.VISIBLE
                    stopButton.visibility = View.GONE
                    onStatusChange(context.getString(R.string.status_error))
                    respTv.setTextColor(Color.parseColor("#EF4444"))
                    respTv.text = "Error: $err"
                } catch (e: Throwable) {
                    com.antigravity.pocketgravity.api.DebugLogger.e("Agent onError error", e)
                }
            }
        )
    }

    fun setModelName(model: String) {
        pillModel.text = "$model ▾"
    }

    private var setupCardView: View? = null

    fun showBridgeSetupCard(onRetry: () -> Unit) {
        if (setupCardView != null) return

        val termuxCmd = "bash /sdcard/Download/frontendcli/start.sh"

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_rounded_card)
            setPadding(28, 24, 28, 24)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 24
            }
            layoutParams = lp
        }

        val title = TextView(context).apply {
            text = "⚡ Engine & Bridge Offline"
            setTextColor(Color.parseColor("#F1F5F9"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        card.addView(title)

        val desc = TextView(context).apply {
            text = "Tap 'Start Engine' to launch the in-app standalone engine, or copy the Termux launcher command below:"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(0, 8, 0, 12)
        }
        card.addView(desc)

        val codeBox = TextView(context).apply {
            text = termuxCmd
            setTextColor(Color.parseColor("#38BDF8"))
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(20, 16, 20, 16)
            setBackgroundColor(Color.parseColor("#0F172A"))
        }
        card.addView(codeBox)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 14, 0, 0)
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
                postDelayed({ onRetry() }, 1500)
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
                val clip = android.content.ClipData.newPlainText("Termux Command", termuxCmd)
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
            text = "🔄 Retry"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_chip)
            setOnClickListener {
                onRetry()
            }
        }
        btnRow.addView(retryBtn)

        card.addView(btnRow)

        setupCardView = card
        messagesContainer.addView(card, 0)
    }

    fun hideBridgeSetupCard() {
        setupCardView?.let {
            messagesContainer.removeView(it)
            setupCardView = null
        }
    }

    private fun scrollToBottom() {
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
}
