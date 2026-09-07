package com.antigravity.clifrontend.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.antigravity.clifrontend.R
import com.antigravity.clifrontend.api.BridgeClient
import com.antigravity.clifrontend.api.ChatMessage
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
        setBackgroundColor(Color.parseColor("#121418"))
    }

    private val messagesContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setPadding(24, 24, 24, 24)
    }

    private val scrollView = ScrollView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        addView(messagesContainer)
    }

    private val inputEditText = EditText(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        hint = context.getString(R.string.prompt_hint)
        setHintTextColor(Color.parseColor("#64748B"))
        setTextColor(Color.parseColor("#F1F5F9"))
        setBackgroundResource(R.drawable.bg_input)
        setPadding(24, 20, 24, 20)
        textSize = 13.5f
        maxLines = 5
    }

    private val sendButton = ImageButton(context).apply {
        layoutParams = LinearLayout.LayoutParams(96, 96).apply {
            marginStart = 16
        }
        setImageResource(R.drawable.ic_play)
        setColorFilter(Color.WHITE)
        setBackgroundResource(R.drawable.bg_chip)
        setPadding(16, 16, 16, 16)
    }

    private val stopButton = ImageButton(context).apply {
        layoutParams = LinearLayout.LayoutParams(96, 96).apply {
            marginStart = 16
        }
        setImageResource(R.drawable.ic_stop)
        setColorFilter(Color.parseColor("#EF4444"))
        setBackgroundResource(R.drawable.bg_chip)
        setPadding(16, 16, 16, 16)
        visibility = View.GONE
    }

    private val inputBar = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setPadding(16, 12, 16, 12)
        setBackgroundColor(Color.parseColor("#1A1D23"))
        addView(inputEditText)
        addView(sendButton)
        addView(stopButton)
    }

    private var currentStreamingMessageView: TextView? = null
    private var currentToolStatusView: TextView? = null
    private var isStreaming = false

    init {
        view.addView(scrollView)
        view.addView(inputBar)

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

        // Welcome / initial banner
        addSystemMessage("Antigravity Mobile IDE ready. Enter instructions to edit code, execute commands, or run tests.")
    }

    private var setupCardView: View? = null

    fun showBridgeSetupCard(onRetry: () -> Unit) {
        if (setupCardView != null) return

        val termuxCmd = "pkg install -y python curl && curl -sL https://raw.githubusercontent.com/adamikoo/frontendcli/main/bridge/start.sh -o ~/start.sh && bash ~/start.sh"

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
            text = "⚡ Termux Bridge Setup Required"
            setTextColor(Color.parseColor("#F1F5F9"))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        card.addView(title)

        val desc = TextView(context).apply {
            text = "To start the background bridge, copy and paste this universal command into Termux:"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(0, 8, 0, 12)
        }
        card.addView(desc)

        val codeBox = TextView(context).apply {
            text = termuxCmd
            setTextColor(Color.parseColor("#38BDF8"))
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#0F172A"))
        }
        card.addView(codeBox)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 14, 0, 0)
        }

        val copyBtn = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 6
            }
            text = "📋 Copy"
            textSize = 11.5f
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
                marginStart = 6
                marginEnd = 6
            }
            text = "🚀 Termux"
            textSize = 11.5f
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_chip)
            setOnClickListener {
                try {
                    val intent = context.packageManager.getLaunchIntentForPackage("com.termux")
                    if (intent != null) {
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } else {
                        Toast.makeText(context, "Termux not installed. Please install from F-Droid.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot open Termux: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnRow.addView(openTermuxBtn)

        val retryBtn = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 6
            }
            text = "🔄 Retry"
            textSize = 11.5f
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

    fun addSystemMessage(text: String) {
        val tv = TextView(context).apply {
            this.text = text
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(24, 16, 24, 16)
            setBackgroundResource(R.drawable.bg_rounded_card)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 20
            layoutParams = lp
        }
        messagesContainer.addView(tv)
        scrollToBottom()
    }

    private fun addUserMessage(prompt: String) {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_chip)
            setPadding(28, 20, 28, 20)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 24
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
                setTextColor(Color.parseColor("#F1F5F9"))
                setPadding(0, 8, 0, 0)
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
            setPadding(28, 24, 28, 24)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 24
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
            setPadding(0, 10, 0, 4)
        }
        card.addView(toolStatus)

        val responseText = TextView(context).apply {
            text = "Thinking..."
            textSize = 13.5f
            setTextColor(Color.parseColor("#E2E8F0"))
            typeface = Typeface.SANS_SERIF
            setPadding(0, 8, 0, 0)
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
        currentStreamingMessageView = respTv
        currentToolStatusView = toolTv

        val fullTextBuilder = StringBuilder()

        bridgeClient.streamAgent(
            prompt = prompt,
            onEvent = { event ->
                val eventName = event.optString("event")
                when (eventName) {
                    "init" -> {
                        onStatusChange(context.getString(R.string.status_working))
                    }
                    "step_update" -> {
                        val step = event.optJSONObject("step_update")
                        if (step != null) {
                            val delta = step.optString("text_delta", "")
                            val stepType = step.optString("step_type", "")

                            if (stepType.contains("tool") || stepType == "command") {
                                onStatusChange(context.getString(R.string.status_running))
                                toolTv.visibility = View.VISIBLE
                                toolTv.text = "⚙ Running tool: $stepType"
                            } else {
                                toolTv.visibility = View.GONE
                            }

                            if (delta.isNotEmpty()) {
                                if (fullTextBuilder.isEmpty()) {
                                    respTv.text = ""
                                }
                                fullTextBuilder.append(delta)
                                respTv.text = fullTextBuilder.toString()
                                scrollToBottom()
                            }
                        }
                    }
                    "result" -> {
                        val res = event.optJSONObject("result")
                        if (res != null) {
                            val usage = res.optJSONObject("usage")
                            if (usage != null) {
                                val total = usage.optInt("total_tokens", 0)
                                val inTok = usage.optInt("input_tokens", 0)
                                val outTok = usage.optInt("output_tokens", 0)
                                val thinkTok = usage.optInt("thinking_tokens", 0)
                                toolTv.visibility = View.VISIBLE
                                toolTv.setTextColor(Color.parseColor("#64748B"))
                                toolTv.text = "Tokens: $total (In: $inTok, Out: $outTok, Think: $thinkTok)"
                            }
                        }
                    }
                }
            },
            onComplete = {
                isStreaming = false
                sendButton.visibility = View.VISIBLE
                stopButton.visibility = View.GONE
                onStatusChange(context.getString(R.string.status_ready))
                if (fullTextBuilder.isEmpty()) {
                    respTv.text = "Turn completed."
                }
            },
            onError = { err ->
                isStreaming = false
                sendButton.visibility = View.VISIBLE
                stopButton.visibility = View.GONE
                onStatusChange(context.getString(R.string.status_error))
                respTv.setTextColor(Color.parseColor("#EF4444"))
                respTv.text = "Error: $err"
            }
        )
    }

    private fun scrollToBottom() {
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
}
