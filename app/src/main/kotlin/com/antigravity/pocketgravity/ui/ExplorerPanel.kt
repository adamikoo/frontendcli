package com.antigravity.pocketgravity.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.antigravity.pocketgravity.R
import com.antigravity.pocketgravity.api.BridgeClient
import com.antigravity.pocketgravity.api.FileItem

class ExplorerPanel(
    val context: Context,
    val bridgeClient: BridgeClient,
    val onFileSelected: (String) -> Unit
) {
    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(Color.parseColor("#121418"))
    }

    var currentPath: String = "/root"
    private var includeHidden: Boolean = false

    private val pathTextView = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        text = "/root"
        textSize = 12f
        setTextColor(Color.parseColor("#94A3B8"))
        typeface = Typeface.MONOSPACE
        isSingleLine = true
    }

    private val upButton = Button(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 72)
        text = "⬆ Up"
        textSize = 11f
        setTextColor(Color.parseColor("#3B82F6"))
        setBackgroundColor(Color.TRANSPARENT)
    }

    private val refreshButton = Button(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 72)
        text = "↻"
        textSize = 14f
        setTextColor(Color.parseColor("#94A3B8"))
        setBackgroundColor(Color.TRANSPARENT)
    }

    private val newFileBtn = Button(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 72)
        text = "+ File"
        textSize = 11f
        setTextColor(Color.parseColor("#10B981"))
        setBackgroundColor(Color.TRANSPARENT)
    }

    private val newFolderBtn = Button(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 72)
        text = "+ Dir"
        textSize = 11f
        setTextColor(Color.parseColor("#10B981"))
        setBackgroundColor(Color.TRANSPARENT)
    }

    private val topBar = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setPadding(16, 8, 16, 8)
        setBackgroundColor(Color.parseColor("#1A1D23"))
        addView(upButton)
        addView(pathTextView)
        addView(newFileBtn)
        addView(newFolderBtn)
        addView(refreshButton)
    }

    private val filesContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private val scrollView = ScrollView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        addView(filesContainer)
    }

    init {
        view.addView(topBar)
        view.addView(scrollView)

        upButton.setOnClickListener {
            val parent = java.io.File(currentPath).parent
            if (!parent.isNullOrEmpty() && parent != currentPath) {
                navigateTo(parent)
            }
        }

        refreshButton.setOnClickListener {
            loadFiles()
        }

        newFileBtn.setOnClickListener {
            promptCreateItem(isDir = false)
        }

        newFolderBtn.setOnClickListener {
            promptCreateItem(isDir = true)
        }

        loadFiles()
    }

    fun navigateTo(path: String) {
        currentPath = path
        pathTextView.text = path
        loadFiles()
    }

    fun loadFiles() {
        bridgeClient.getFiles(currentPath, includeHidden) { res ->
            res.onSuccess { items ->
                renderItems(items)
            }.onFailure { err ->
                Toast.makeText(context, "Error: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderItems(items: List<FileItem>) {
        filesContainer.removeAllViews()

        if (items.isEmpty()) {
            val emptyTv = TextView(context).apply {
                text = "Empty folder"
                setTextColor(Color.parseColor("#64748B"))
                textSize = 12f
                setPadding(32, 32, 32, 32)
            }
            filesContainer.addView(emptyTv)
            return
        }

        for (item in items) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 20, 24, 20)
                setBackgroundColor(Color.TRANSPARENT)
                isClickable = true

                val iconTv = TextView(context).apply {
                    text = if (item.isDir) "📁 " else "📄 "
                    textSize = 14f
                }
                addView(iconTv)

                val nameTv = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    text = item.name
                    textSize = 13f
                    setTextColor(if (item.isDir) Color.parseColor("#60A5FA") else Color.parseColor("#F1F5F9"))
                    typeface = if (item.isDir) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                }
                addView(nameTv)

                if (!item.isDir) {
                    val sizeTv = TextView(context).apply {
                        text = formatSize(item.size)
                        textSize = 10.5f
                        setTextColor(Color.parseColor("#64748B"))
                    }
                    addView(sizeTv)
                }

                setOnClickListener {
                    if (item.isDir) {
                        navigateTo(item.path)
                    } else {
                        onFileSelected(item.path)
                    }
                }

                setOnLongClickListener {
                    showItemContextMenu(item)
                    true
                }
            }
            filesContainer.addView(row)

            val divider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(Color.parseColor("#1E232B"))
            }
            filesContainer.addView(divider)
        }
    }

    private fun showItemContextMenu(item: FileItem) {
        val options = arrayOf("Open in Editor", "Rename", "Delete")
        AlertDialog.Builder(context)
            .setTitle(item.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onFileSelected(item.path)
                    1 -> promptRename(item)
                    2 -> promptDelete(item)
                }
            }
            .show()
    }

    private fun promptCreateItem(isDir: Boolean) {
        val input = EditText(context)
        input.hint = if (isDir) "Folder name" else "File name (e.g. main.py)"
        AlertDialog.Builder(context)
            .setTitle(if (isDir) "New Folder" else "New File")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val fullPath = "$currentPath/$name"
                    bridgeClient.createItem(fullPath, isDir) { res ->
                        res.onSuccess { loadFiles() }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptRename(item: FileItem) {
        val input = EditText(context)
        input.setText(item.name)
        AlertDialog.Builder(context)
            .setTitle("Rename ${item.name}")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != item.name) {
                    val parent = java.io.File(item.path).parent ?: currentPath
                    val newPath = "$parent/$newName"
                    bridgeClient.renameItem(item.path, newPath) {
                        loadFiles()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptDelete(item: FileItem) {
        AlertDialog.Builder(context)
            .setTitle("Delete")
            .setMessage("Are you sure you want to delete ${item.name}?")
            .setPositiveButton("Delete") { _, _ ->
                bridgeClient.deleteItem(item.path) {
                    loadFiles()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}
