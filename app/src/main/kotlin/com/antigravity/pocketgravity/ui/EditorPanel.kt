package com.antigravity.pocketgravity.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.antigravity.pocketgravity.R
import com.antigravity.pocketgravity.api.BridgeClient

data class OpenTab(
    val path: String,
    val name: String,
    var content: String,
    var isDirty: Boolean = false
)

class EditorPanel(
    val context: Context,
    val bridgeClient: BridgeClient
) {
    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(Color.parseColor("#15181E"))
    }

    private val tabsContainer = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private val tabsScrollView = HorizontalScrollView(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, 80, 1f)
        isHorizontalScrollBarEnabled = false
        addView(tabsContainer)
    }

    private val saveButton = ImageButton(context).apply {
        layoutParams = LinearLayout.LayoutParams(80, 80)
        setImageResource(R.drawable.ic_save)
        setColorFilter(Color.WHITE)
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(16, 16, 16, 16)
    }

    private val searchButton = Button(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 80)
        text = "Find"
        textSize = 11f
        setTextColor(Color.parseColor("#94A3B8"))
        setBackgroundColor(Color.TRANSPARENT)
    }

    private val topToolbar = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            80
        )
        setBackgroundColor(Color.parseColor("#1A1D23"))
        addView(tabsScrollView)
        addView(searchButton)
        addView(saveButton)
    }

    // Search bar (collapsible)
    private val searchInput = EditText(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        hint = "Search text..."
        setHintTextColor(Color.parseColor("#64748B"))
        setTextColor(Color.WHITE)
        textSize = 12f
        setBackgroundResource(R.drawable.bg_input)
        setPadding(16, 12, 16, 12)
    }
    private val searchBar = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setPadding(16, 8, 16, 8)
        setBackgroundColor(Color.parseColor("#222730"))
        visibility = View.GONE
        addView(searchInput)
    }

    // Line Numbers & Editor
    private val lineNumbersView = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        typeface = Typeface.MONOSPACE
        textSize = 12f
        setTextColor(Color.parseColor("#475569"))
        setPadding(16, 16, 16, 16)
        gravity = Gravity.END
        text = "1\n2\n3"
    }

    private val codeEditText = EditText(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        typeface = Typeface.MONOSPACE
        textSize = 12f
        setTextColor(Color.parseColor("#F1F5F9"))
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(16, 16, 16, 16)
        gravity = Gravity.TOP or Gravity.START
        isVerticalScrollBarEnabled = true
    }

    private val editorRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        addView(lineNumbersView)
        addView(codeEditText)
    }

    private val editorScrollView = ScrollView(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        addView(editorRow)
    }

    // Programmer Accessory Keys Bar
    private val accessoryKeysBar = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
    private val accessoryScrollView = HorizontalScrollView(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 72)
        setBackgroundColor(Color.parseColor("#1A1D23"))
        isHorizontalScrollBarEnabled = false
        addView(accessoryKeysBar)
    }

    private val openTabs = mutableListOf<OpenTab>()
    private var activeTabIndex = -1

    init {
        view.addView(topToolbar)
        view.addView(searchBar)
        view.addView(editorScrollView)
        view.addView(accessoryScrollView)

        initAccessoryKeys()

        searchButton.setOnClickListener {
            if (searchBar.visibility == View.VISIBLE) {
                searchBar.visibility = View.GONE
            } else {
                searchBar.visibility = View.VISIBLE
                searchInput.requestFocus()
            }
        }

        saveButton.setOnClickListener {
            saveCurrentTab()
        }

        codeEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (activeTabIndex in openTabs.indices) {
                    val tab = openTabs[activeTabIndex]
                    val newText = s?.toString() ?: ""
                    if (newText != tab.content) {
                        tab.isDirty = true
                        updateTabsUI()
                    }
                }
                updateLineNumbers()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Default tab
        openTab("/root/welcome.txt", "welcome.txt", "// Welcome to CLIFrontend Mobile IDE\n// Use the Files tab to explore your repository\n// Or ask the Antigravity Agent to generate code.")
    }

    private fun initAccessoryKeys() {
        val keys = listOf("Tab", "{", "}", "(", ")", "[", "]", ";", ":", "\"", "'", "=", "+", "-", "*", "/", "\\", "_", "$", "!", "&", "|", "<", ">", "?")
        for (k in keys) {
            val btn = Button(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
                text = k
                textSize = 12f
                setTextColor(Color.parseColor("#94A3B8"))
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener {
                    val insert = if (k == "Tab") "    " else k
                    val start = codeEditText.selectionStart.coerceAtLeast(0)
                    val end = codeEditText.selectionEnd.coerceAtLeast(0)
                    codeEditText.text?.replace(start, end, insert)
                }
            }
            accessoryKeysBar.addView(btn)
        }
    }

    fun openFile(path: String) {
        val existingIndex = openTabs.indexOfFirst { it.path == path }
        if (existingIndex != -1) {
            switchTab(existingIndex)
            return
        }

        bridgeClient.getFile(path) { res ->
            res.onSuccess { content ->
                val name = path.substringAfterLast("/")
                openTab(path, name, content)
            }.onFailure { err ->
                Toast.makeText(context, "Failed to load file: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openTab(path: String, name: String, content: String) {
        val tab = OpenTab(path, name, content, false)
        openTabs.add(tab)
        switchTab(openTabs.size - 1)
    }

    private fun switchTab(index: Int) {
        if (index in openTabs.indices) {
            activeTabIndex = index
            val tab = openTabs[index]
            codeEditText.setText(tab.content)
            updateTabsUI()
            updateLineNumbers()
        }
    }

    private fun updateTabsUI() {
        tabsContainer.removeAllViews()
        for ((idx, tab) in openTabs.withIndex()) {
            val tabView = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 12, 24, 12)
                setBackgroundColor(if (idx == activeTabIndex) Color.parseColor("#222730") else Color.TRANSPARENT)
                isClickable = true
                setOnClickListener { switchTab(idx) }

                val titleTv = TextView(context).apply {
                    val label = if (tab.isDirty) "${tab.name} *" else tab.name
                    text = label
                    textSize = 11.5f
                    setTextColor(if (idx == activeTabIndex) Color.WHITE else Color.parseColor("#94A3B8"))
                }
                addView(titleTv)

                val closeTv = TextView(context).apply {
                    text = "  ✕"
                    textSize = 10f
                    setTextColor(Color.parseColor("#64748B"))
                    setPadding(12, 0, 0, 0)
                    setOnClickListener {
                        closeTab(idx)
                    }
                }
                addView(closeTv)
            }
            tabsContainer.addView(tabView)
        }
    }

    private fun closeTab(index: Int) {
        if (index in openTabs.indices) {
            openTabs.removeAt(index)
            if (openTabs.isEmpty()) {
                activeTabIndex = -1
                codeEditText.setText("")
                updateTabsUI()
                updateLineNumbers()
            } else {
                val newIndex = (index - 1).coerceAtLeast(0)
                switchTab(newIndex)
            }
        }
    }

    private fun saveCurrentTab() {
        if (activeTabIndex !in openTabs.indices) return
        val tab = openTabs[activeTabIndex]
        val currentContent = codeEditText.text.toString()

        bridgeClient.saveFile(tab.path, currentContent) { res ->
            res.onSuccess {
                tab.content = currentContent
                tab.isDirty = false
                updateTabsUI()
                Toast.makeText(context, "Saved ${tab.name}", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(context, "Save failed: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateLineNumbers() {
        val lines = codeEditText.lineCount.coerceAtLeast(1)
        val sb = StringBuilder()
        for (i in 1..lines) {
            sb.append(i).append("\n")
        }
        lineNumbersView.text = sb.toString()
    }
}
