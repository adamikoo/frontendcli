package com.antigravity.pocketgravity.ui

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

data class AttachedMedia(
    val filename: String,
    val relPath: String,
    val bitmap: Bitmap? = null
)

class AgentPanel(
    val context: Context,
    val bridgeClient: BridgeClient,
    val onPickImageRequested: (() -> Unit)? = null,
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

    private val attachedMediaList = mutableListOf<AttachedMedia>()
    private var currentMode = "Planning"
    private var currentModel: String? = null
    private var currentEffort: String = "medium"
    private var isStreaming = false
    private var isWelcomeState = true

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

        val quotaBadge = TextView(context).apply {
            text = "⚡ Quota"
            textSize = 10.5f
            setTextColor(Color.parseColor("#10B981"))
            setBackgroundResource(R.drawable.bg_chip)
            setPadding(12, 4, 12, 4)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = 12
            }
            layoutParams = lp
            setOnClickListener { refreshLimits() }
        }
        addView(quotaBadge)

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
            setOnClickListener { showContextMenu() }
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

    // Horizontal Thumbnail Strip for Attached Images
    private val attachmentsContainer = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private val attachmentsScrollView = HorizontalScrollView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 10
        }
        visibility = View.GONE
        addView(attachmentsContainer)
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
        text = "📎"
        textSize = 16f
        setTextColor(Color.parseColor("#94A3B8"))
        setPadding(4, 0, 10, 0)
        setOnClickListener { showAttachmentActionsDialog() }
    }

    private val btnAtTag = TextView(context).apply {
        text = "@"
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#60A5FA"))
        setPadding(6, 0, 10, 0)
        setOnClickListener { showFileContextPicker() }
    }

    private val btnSlash = TextView(context).apply {
        text = "/"
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#A78BFA"))
        setPadding(6, 0, 10, 0)
        setOnClickListener { showWorkflowPicker() }
    }

    private val pillMode = TextView(context).apply {
        text = "Planning ▾"
        textSize = 11.5f
        setTextColor(Color.parseColor("#CBD5E1"))
        setBackgroundResource(R.drawable.bg_pill_dropdown)
        setPadding(12, 6, 12, 6)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = 6
        }
        layoutParams = lp
        setOnClickListener { showModePicker() }
    }

    private val pillModel = TextView(context).apply {
        text = "Gemini 3.8 Flash ▾"
        textSize = 11.5f
        setTextColor(Color.parseColor("#CBD5E1"))
        setBackgroundResource(R.drawable.bg_pill_dropdown)
        setPadding(12, 6, 12, 6)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = 6
        }
        layoutParams = lp
        setOnClickListener { showModelPicker() }
    }

    private val pillEffort = TextView(context).apply {
        text = "Effort: Med ▾"
        textSize = 11.5f
        setTextColor(Color.parseColor("#CBD5E1"))
        setBackgroundResource(R.drawable.bg_pill_dropdown)
        setPadding(12, 6, 12, 6)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = 6
        }
        layoutParams = lp
        setOnClickListener { showEffortPicker() }
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

    // Floating Suggestions Bar for @ and /
    private val suggestionsContainer = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private val suggestionsScrollView = HorizontalScrollView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(16, 0, 16, 4)
        }
        visibility = View.GONE
        addView(suggestionsContainer)
    }

    // Disclaimer footer
    private val disclaimerTv = TextView(context).apply {
        text = "AI may make mistakes. Double-check all generated code."
        textSize = 10.5f
        setTextColor(Color.parseColor("#475569"))
        gravity = Gravity.CENTER
        setPadding(0, 6, 0, 10)
    }

    init {
        // Assemble inside-card controls
        cardControlsBar.addView(btnAddContext)
        cardControlsBar.addView(btnAtTag)
        cardControlsBar.addView(btnSlash)
        cardControlsBar.addView(pillMode)
        cardControlsBar.addView(pillModel)
        cardControlsBar.addView(pillEffort)
        cardControlsBar.addView(spacer)
        cardControlsBar.addView(sendButton)
        cardControlsBar.addView(stopButton)

        // Assemble input card
        inputCard.addView(inputEditText)
        inputCard.addView(attachmentsScrollView)
        inputCard.addView(cardControlsBar)

        // Assemble main view
        view.addView(subHeader)
        view.addView(scrollView)
        view.addView(suggestionsScrollView)
        view.addView(inputCard)
        view.addView(disclaimerTv)

        inputEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkSuggestions(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        sendButton.setOnClickListener {
            val prompt = inputEditText.text.toString().trim()
            if ((prompt.isNotEmpty() || attachedMediaList.isNotEmpty()) && !isStreaming) {
                sendPrompt(if (prompt.isEmpty()) "Analyze attached image." else prompt)
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
        refreshLimits()
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

    fun addAttachedMedia(filename: String, relPath: String, bitmap: Bitmap?) {
        attachedMediaList.add(AttachedMedia(filename, relPath, bitmap))
        renderAttachments()
    }

    private fun renderAttachments() {
        attachmentsContainer.removeAllViews()
        if (attachedMediaList.isEmpty()) {
            attachmentsScrollView.visibility = View.GONE
            return
        }
        attachmentsScrollView.visibility = View.VISIBLE
        for (media in attachedMediaList) {
            val chip = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_pill_dropdown)
                setPadding(10, 6, 10, 6)
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 8
                }
                layoutParams = lp
            }

            if (media.bitmap != null) {
                val iv = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(44, 44).apply {
                        marginEnd = 6
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(media.bitmap)
                }
                chip.addView(iv)
            } else {
                val icon = TextView(context).apply {
                    text = "🖼️"
                    textSize = 12f
                    setPadding(0, 0, 4, 0)
                }
                chip.addView(icon)
            }

            val nameTv = TextView(context).apply {
                text = media.filename.take(16)
                textSize = 11f
                setTextColor(Color.parseColor("#E2E8F0"))
            }
            chip.addView(nameTv)

            val removeBtn = TextView(context).apply {
                text = " ✕"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#EF4444"))
                setPadding(8, 0, 2, 0)
                setOnClickListener {
                    attachedMediaList.remove(media)
                    renderAttachments()
                }
            }
            chip.addView(removeBtn)

            attachmentsContainer.addView(chip)
        }
    }

    private fun showAttachmentActionsDialog() {
        val options = arrayOf(
            "📷 Attach Image / Photo",
            "📋 Paste Image from Clipboard",
            "🏷️ Mention File Context (@)",
            "⚡ Workflows (/)",
            "🧠 Reasoning Effort"
        )
        AlertDialog.Builder(context)
            .setTitle("Add Media & Context")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onPickImageRequested?.invoke() ?: Toast.makeText(context, "Image picker not available", Toast.LENGTH_SHORT).show()
                    1 -> handleClipboardPaste()
                    2 -> showFileContextPicker()
                    3 -> showWorkflowPicker()
                    4 -> showEffortPicker()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleClipboardPaste() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = cm?.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }
        val item = clip.getItemAt(0)
        val uri = item.uri
        if (uri != null) {
            uploadUriToBridge(uri)
            return
        }
        val text = item.text?.toString() ?: ""
        if (text.startsWith("data:image/") && text.contains("base64,")) {
            val base64 = text.substringAfter("base64,")
            val fname = "clip_${System.currentTimeMillis()}.png"
            bridgeClient.uploadImage(fname, base64) { res ->
                res.onSuccess {
                    val rel = it.optString("path", ".gemini/attachments/$fname")
                    val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    addAttachedMedia(fname, rel, bmp)
                    Toast.makeText(context, "Pasted image attached!", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "Upload failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (text.isNotEmpty()) {
            inputEditText.append(text)
            Toast.makeText(context, "Text pasted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadUriToBridge(uri: android.net.Uri) {
        try {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream != null) {
                val bytes = stream.readBytes()
                stream.close()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val fname = "img_${System.currentTimeMillis()}.jpg"
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                bridgeClient.uploadImage(fname, base64) { res ->
                    res.onSuccess {
                        val rel = it.optString("path", ".gemini/attachments/$fname")
                        addAttachedMedia(fname, rel, bmp)
                        Toast.makeText(context, "Attached $fname", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, "Upload failed: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Read failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFileContextPicker() {
        bridgeClient.getFiles { res ->
            res.onSuccess { files ->
                val paths = files.map { it.path }.toTypedArray()
                if (paths.isEmpty()) {
                    Toast.makeText(context, "No files found in workspace", Toast.LENGTH_SHORT).show()
                    return@onSuccess
                }
                AlertDialog.Builder(context)
                    .setTitle("Select File Context (@)")
                    .setItems(paths) { _, which ->
                        inputEditText.append("@${paths[which]} ")
                        inputEditText.requestFocus()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }.onFailure {
                val input = EditText(context).apply {
                    hint = "e.g. app/src/main/kotlin/..."
                    setTextColor(Color.WHITE)
                }
                AlertDialog.Builder(context)
                    .setTitle("Mention File (@)")
                    .setView(input)
                    .setPositiveButton("Insert") { _, _ ->
                        val p = input.text.toString().trim()
                        if (p.isNotEmpty()) {
                            inputEditText.append("@$p ")
                            inputEditText.requestFocus()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun showWorkflowPicker() {
        val workflows = arrayOf(
            "/goal - Autonomous long-running task until verified",
            "/schedule - Recurring cron schedule or one-shot timer",
            "/grill-me - Interactive architectural interview",
            "/learn - Extract & save pattern to knowledge base",
            "/ftp-upload - Automated FTP deployment",
            "/sales-automator - Marketing & sales outreach generation"
        )
        AlertDialog.Builder(context)
            .setTitle("Insert Antigravity Workflow (/)")
            .setItems(workflows) { _, which ->
                val cmd = workflows[which].substringBefore(" - ")
                inputEditText.setText("$cmd ")
                inputEditText.setSelection(inputEditText.text.length)
                inputEditText.requestFocus()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEffortPicker() {
        val efforts = arrayOf("low", "medium", "high", "max")
        AlertDialog.Builder(context)
            .setTitle("Reasoning Effort")
            .setItems(efforts) { _, which ->
                currentEffort = efforts[which]
                val label = currentEffort.replaceFirstChar { it.uppercase() }
                pillEffort.text = "Effort: $label ▾"
            }
            .show()
    }

    private fun showContextMenu() {
        val options = arrayOf("Clear Chat", "View Session Transcript", "Refresh Limits & Quotas")
        AlertDialog.Builder(context)
            .setTitle("Agent Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> resetToEmptyChat()
                    1 -> showSessionHistory()
                    2 -> refreshLimits()
                }
            }
            .show()
    }

    fun refreshLimits() {
        bridgeClient.getLimits { res ->
            res.onSuccess { json ->
                val pct = json.optInt("percent_used", 0)
                val overcharge = json.optBoolean("credit_overcharge", false)
                val remaining = (100 - pct).coerceIn(0, 100)
                val quotaView = subHeader.getChildAt(1) as? TextView
                quotaView?.apply {
                    text = "⚡ Quota: $remaining%${if (overcharge) " (PayG)" else ""}"
                    if (remaining < 20) {
                        setTextColor(Color.parseColor("#EF4444"))
                    } else if (remaining < 50) {
                        setTextColor(Color.parseColor("#F59E0B"))
                    } else {
                        setTextColor(Color.parseColor("#10B981"))
                    }
                }
            }
        }
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
                        currentModel = selected.name
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
        val options = arrayOf(
            "Current Session (Active)",
            "➕ Start Fresh Conversation",
            "🧹 Clear Chat History",
            "📋 View Implementation Plan",
            "✨ View Walkthrough Summary"
        )
        AlertDialog.Builder(context)
            .setTitle("Session History & Artifacts")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> Toast.makeText(context, "Current chat session is active", Toast.LENGTH_SHORT).show()
                    1 -> resetToEmptyChat()
                    2 -> resetToEmptyChat()
                    3 -> {
                        bridgeClient.getFile("implementation_plan.md") { res ->
                            res.onSuccess { content ->
                                if (content.isNotEmpty()) {
                                    showPlanViewerDialog("Implementation Plan", content)
                                } else {
                                    Toast.makeText(context, "No implementation_plan.md found.", Toast.LENGTH_SHORT).show()
                                }
                            }.onFailure {
                                Toast.makeText(context, "No plan file available.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    4 -> {
                        bridgeClient.getFile("walkthrough.md") { res ->
                            res.onSuccess { content ->
                                if (content.isNotEmpty()) {
                                    showPlanViewerDialog("Walkthrough Summary", content)
                                } else {
                                    Toast.makeText(context, "No walkthrough.md found.", Toast.LENGTH_SHORT).show()
                                }
                            }.onFailure {
                                Toast.makeText(context, "No walkthrough file available.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun addUserMessage(prompt: String, media: List<AttachedMedia> = emptyList()) {
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
            addView(label)

            if (media.isNotEmpty()) {
                val mediaRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 8, 0, 4)
                }
                for (m in media) {
                    if (m.bitmap != null) {
                        val iv = ImageView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(120, 120).apply {
                                marginEnd = 10
                            }
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            setImageBitmap(m.bitmap)
                        }
                        mediaRow.addView(iv)
                    } else {
                        val tv = TextView(context).apply {
                            text = "📎 ${m.filename}"
                            textSize = 11f
                            setTextColor(Color.parseColor("#94A3B8"))
                            setBackgroundResource(R.drawable.bg_chip)
                            setPadding(8, 4, 8, 4)
                        }
                        mediaRow.addView(tv)
                    }
                }
                addView(mediaRow)
            }

            val content = TextView(context).apply {
                text = prompt
                textSize = 13.5f
                setTextColor(Color.parseColor("#F8FAFC"))
                setPadding(0, 6, 0, 0)
            }
            addView(content)
        }
        messagesContainer.addView(card)
        scrollToBottom()
    }

    private fun startAgentResponse(): Triple<LinearLayout, TextView, TextView> {
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
        return Triple(card, responseText, toolStatus)
    }

    private fun sendPrompt(prompt: String) {
        val mediaToSend = attachedMediaList.toList()
        val imagePaths = mediaToSend.map { it.relPath }
        attachedMediaList.clear()
        renderAttachments()

        inputEditText.setText("")
        addUserMessage(prompt, mediaToSend)

        isStreaming = true
        sendButton.visibility = View.GONE
        stopButton.visibility = View.VISIBLE
        onStatusChange(context.getString(R.string.status_thinking))

        val (respCard, respTv, toolTv) = startAgentResponse()
        val fullTextBuilder = StringBuilder()

        bridgeClient.streamAgent(
            prompt = prompt,
            model = currentModel,
            effort = currentEffort,
            images = imagePaths,
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
                            respTv.text = "⚠️ Process exited with code $exitCode."
                        }
                        addFailureActionCard(exitCode)
                    } else {
                        onStatusChange(context.getString(R.string.status_ready))
                        if (fullTextBuilder.isEmpty()) {
                            respTv.text = "Turn completed (no output)."
                        } else {
                            renderRichAgentResponse(respCard, respTv, fullTextBuilder.toString())
                        }
                        checkAndRenderArtifacts()
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
                    addFailureActionCard(1, err)
                } catch (e: Throwable) {
                    com.antigravity.pocketgravity.api.DebugLogger.e("Agent onError error", e)
                }
            }
        )
    }

    private fun renderRichAgentResponse(card: LinearLayout, defaultTv: TextView, rawText: String) {
        val codeBlockRegex = Regex("```([a-zA-Z0-9_-]*)\\s*\\n([\\s\\S]*?)```")
        val matches = codeBlockRegex.findAll(rawText).toList()
        if (matches.isEmpty()) {
            return
        }

        defaultTv.visibility = View.GONE

        var lastIdx = 0
        for (m in matches) {
            val preText = rawText.substring(lastIdx, m.range.first).trim()
            if (preText.isNotEmpty()) {
                val tv = TextView(context).apply {
                    text = preText
                    textSize = 13.5f
                    setTextColor(Color.parseColor("#E2E8F0"))
                    setPadding(0, 4, 0, 8)
                }
                card.addView(tv)
            }

            val lang = m.groupValues[1].uppercase().ifEmpty { "CODE" }
            val codeSnippet = m.groupValues[2].trimEnd()

            val codeBox = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#0F172A"))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 6
                    bottomMargin = 10
                }
                layoutParams = lp
            }

            val codeHeader = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#1E293B"))
                setPadding(16, 8, 16, 8)
            }
            val langLabel = TextView(context).apply {
                text = lang
                textSize = 10.5f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#94A3B8"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val copyBtn = TextView(context).apply {
                text = "📋 Copy"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#38BDF8"))
                setPadding(8, 0, 4, 0)
                setOnClickListener {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("code", codeSnippet))
                    Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            }
            codeHeader.addView(langLabel)
            codeHeader.addView(copyBtn)
            codeBox.addView(codeHeader)

            val codeScroll = HorizontalScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val codeTv = TextView(context).apply {
                text = codeSnippet
                textSize = 12f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#F8FAFC"))
                setPadding(16, 12, 16, 14)
                setTextIsSelectable(true)
            }
            codeScroll.addView(codeTv)
            codeBox.addView(codeScroll)

            card.addView(codeBox)
            lastIdx = m.range.last + 1
        }

        if (lastIdx < rawText.length) {
            val postText = rawText.substring(lastIdx).trim()
            if (postText.isNotEmpty()) {
                val tv = TextView(context).apply {
                    text = postText
                    textSize = 13.5f
                    setTextColor(Color.parseColor("#E2E8F0"))
                    setPadding(0, 4, 0, 8)
                }
                card.addView(tv)
            }
        }
        scrollToBottom()
    }

    private fun checkAndRenderArtifacts() {
        if (currentMode == "Planning") {
            bridgeClient.getFile("implementation_plan.md") { res ->
                res.onSuccess { content ->
                    if (content.isNotEmpty()) {
                        renderPlanArtifactCard(content)
                    }
                }
            }
        }
    }

    private fun renderPlanArtifactCard(planContent: String) {
        val planCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_rounded_card)
            setPadding(24, 20, 24, 20)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12
                bottomMargin = 14
            }
            layoutParams = lp
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(context).apply {
            text = "📋 Implementation Plan"
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F1F5F9"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val badge = TextView(context).apply {
            text = "PLAN"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#60A5FA"))
            setBackgroundResource(R.drawable.bg_chip)
            setPadding(10, 4, 10, 4)
        }
        topRow.addView(title)
        topRow.addView(badge)
        planCard.addView(topRow)

        val preview = TextView(context).apply {
            val lines = planContent.lines().filter { it.isNotBlank() && !it.startsWith("#") }.take(3).joinToString("\n")
            text = lines.ifEmpty { "Plan generated and ready for review." }
            textSize = 11.5f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 8, 0, 14)
        }
        planCard.addView(preview)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnProceed = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 6
            }
            text = "✅ Proceed"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_send_circle)
            setOnClickListener {
                sendPrompt("Proceed with implementation")
            }
        }
        btnRow.addView(btnProceed)

        val btnView = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 6
                marginEnd = 6
            }
            text = "🔍 View Plan"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_chip)
            setOnClickListener {
                showPlanViewerDialog("Implementation Plan", planContent)
            }
        }
        btnRow.addView(btnView)

        val btnFeedback = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 6
            }
            text = "✏️ Feedback"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_chip)
            setOnClickListener {
                inputEditText.setText("Feedback: ")
                inputEditText.setSelection(inputEditText.text.length)
                inputEditText.requestFocus()
            }
        }
        btnRow.addView(btnFeedback)

        planCard.addView(btnRow)
        messagesContainer.addView(planCard)
        scrollToBottom()
    }

    private fun showPlanViewerDialog(title: String, content: String) {
        val scroll = ScrollView(context)
        val tv = TextView(context).apply {
            text = content
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(24, 20, 24, 20)
            setTextIsSelectable(true)
        }
        scroll.addView(tv)

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("📋 Copy All") { _, _ ->
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText(title, content))
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun checkSuggestions(text: String) {
        val lastAt = text.lastIndexOf('@')
        val lastSlash = text.lastIndexOf('/')

        if (lastAt >= 0 && lastAt >= text.length - 20) {
            val query = text.substring(lastAt + 1).trim()
            showFileSuggestions(query)
            return
        }

        if (text.startsWith("/")) {
            val query = text.substring(1).trim()
            showWorkflowSuggestions(query)
            return
        }

        suggestionsScrollView.visibility = View.GONE
    }

    private fun showWorkflowSuggestions(query: String) {
        val workflows = listOf(
            "/goal", "/schedule", "/grill-me", "/learn", "/ftp-upload", "/sales-automator"
        ).filter { it.contains(query, ignoreCase = true) }

        if (workflows.isEmpty()) {
            suggestionsScrollView.visibility = View.GONE
            return
        }

        suggestionsContainer.removeAllViews()
        for (wf in workflows) {
            val chip = TextView(context).apply {
                text = wf
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#A78BFA"))
                setBackgroundResource(R.drawable.bg_pill_dropdown)
                setPadding(14, 6, 14, 6)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = 8
                }
                layoutParams = lp
                setOnClickListener {
                    inputEditText.setText("$wf ")
                    inputEditText.setSelection(inputEditText.text.length)
                    suggestionsScrollView.visibility = View.GONE
                }
            }
            suggestionsContainer.addView(chip)
        }
        suggestionsScrollView.visibility = View.VISIBLE
    }

    private fun showFileSuggestions(query: String) {
        bridgeClient.getFiles { res ->
            res.onSuccess { files ->
                val filtered = files
                    .filter { !it.isDir && (query.isEmpty() || it.path.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)) }
                    .take(6)

                if (filtered.isEmpty()) {
                    suggestionsScrollView.visibility = View.GONE
                    return@onSuccess
                }

                suggestionsContainer.removeAllViews()
                for (f in filtered) {
                    val chip = TextView(context).apply {
                        text = "📄 ${f.name}"
                        textSize = 11f
                        setTextColor(Color.parseColor("#60A5FA"))
                        setBackgroundResource(R.drawable.bg_pill_dropdown)
                        setPadding(12, 6, 12, 6)
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginEnd = 8
                        }
                        layoutParams = lp
                        setOnClickListener {
                            val cur = inputEditText.text.toString()
                            val atIdx = cur.lastIndexOf('@')
                            if (atIdx >= 0) {
                                val newText = cur.substring(0, atIdx) + "@${f.path} "
                                inputEditText.setText(newText)
                                inputEditText.setSelection(newText.length)
                            }
                            suggestionsScrollView.visibility = View.GONE
                        }
                    }
                    suggestionsContainer.addView(chip)
                }
                suggestionsScrollView.visibility = View.VISIBLE
            }.onFailure {
                suggestionsScrollView.visibility = View.GONE
            }
        }
    }

    private fun addFailureActionCard(exitCode: Int, customError: String? = null) {
        val errCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_rounded_card)
            setPadding(24, 20, 24, 20)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 16
                bottomMargin = 16
            }
            layoutParams = lp
        }

        val errTitle = TextView(context).apply {
            text = "⚠️ Antigravity Process Exited with Code $exitCode"
            setTextColor(Color.parseColor("#F87171"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        errCard.addView(errTitle)

        val snippet = customError ?: com.antigravity.pocketgravity.api.DebugLogger.lastStderr.ifEmpty { "Check console / Android Studio Logcat for full details." }
        val errSnippetTv = TextView(context).apply {
            text = snippet.take(400)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(0, 8, 0, 14)
        }
        errCard.addView(errSnippetTv)

        val btnBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val btnCopy = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 6
            }
            text = "📋 Copy Debug Info"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_chip)
            setOnClickListener {
                val report = com.antigravity.pocketgravity.api.DebugLogger.generateFullReport(context)
                com.antigravity.pocketgravity.api.DebugLogger.copyToClipboard(context, report, "PocketGravity Failure Log")
            }
        }
        btnBar.addView(btnCopy)

        val btnDiag = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 6
            }
            text = "🔍 View Full Report"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_chip)
            setOnClickListener {
                val report = com.antigravity.pocketgravity.api.DebugLogger.generateFullReport(context)
                val reportView = TextView(context).apply {
                    text = report
                    textSize = 10f
                    typeface = Typeface.MONOSPACE
                    setPadding(20, 16, 20, 16)
                    setTextColor(Color.parseColor("#E2E8F0"))
                }
                val scroll = ScrollView(context).apply { addView(reportView) }
                AlertDialog.Builder(context)
                    .setTitle("PocketGravity Diagnostic Report")
                    .setView(scroll)
                    .setPositiveButton("📋 Copy All") { _, _ ->
                        com.antigravity.pocketgravity.api.DebugLogger.copyToClipboard(context, report, "PocketGravity Diagnostic Report")
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }
        btnBar.addView(btnDiag)

        errCard.addView(btnBar)
        messagesContainer.addView(errCard)
        scrollToBottom()
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
            text = "⚡ Termux Bridge Offline"
            setTextColor(Color.parseColor("#F1F5F9"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        card.addView(title)

        val desc = TextView(context).apply {
            text = "Launch the bridge daemon in Termux to connect to Antigravity:"
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
