package com.antigravity.pocketgravity

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import com.antigravity.pocketgravity.api.BridgeClient
import com.antigravity.pocketgravity.api.ModelItem
import com.antigravity.pocketgravity.ui.*

class MainActivity : Activity() {

    private lateinit var bridgeClient: BridgeClient
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var contentContainer: FrameLayout
    private lateinit var tvProjectName: TextView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvAgentStatus: TextView
    private lateinit var btnDiagnostics: ImageView

    private lateinit var navAgent: LinearLayout
    private lateinit var navEditor: LinearLayout
    private lateinit var navExplorer: LinearLayout
    private lateinit var navChanges: LinearLayout
    private lateinit var navTerminal: LinearLayout

    private lateinit var navIconAgent: ImageView
    private lateinit var navIconEditor: ImageView
    private lateinit var navIconExplorer: ImageView
    private lateinit var navIconChanges: ImageView
    private lateinit var navIconTerminal: ImageView

    private lateinit var navTextAgent: TextView
    private lateinit var navTextEditor: TextView
    private lateinit var navTextExplorer: TextView
    private lateinit var navTextChanges: TextView
    private lateinit var navTextTerminal: TextView

    private lateinit var agentPanel: AgentPanel
    private lateinit var editorPanel: EditorPanel
    private lateinit var explorerPanel: ExplorerPanel
    private lateinit var changesPanel: ChangesPanel
    private lateinit var terminalPanel: TerminalPanel
    private lateinit var diagnosticsPanel: DiagnosticsPanel
    private lateinit var settingsPanel: SettingsPanel

    private var activeTab = Tab.AGENT

    enum class Tab {
        AGENT, EDITOR, EXPLORER, CHANGES, TERMINAL, DIAGNOSTICS, SETTINGS
    }

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            checkHealth()
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("PocketGravity", "FATAL CRASH on thread ${thread.name}", throwable)
            com.antigravity.pocketgravity.api.DebugLogger.e("FATAL CRASH on thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        com.antigravity.pocketgravity.api.RuntimeManager.init(this)
        com.antigravity.pocketgravity.api.AssetExporter.exportBridgeFiles(this)
        com.antigravity.pocketgravity.api.BridgeService.start(this)

        bridgeClient = BridgeClient(this)

        initViews()
        initPanels()
        setupListeners()

        switchTab(Tab.AGENT)
        handler.post(heartbeatRunnable)
    }

    override fun onResume() {
        super.onResume()
        checkHealth()
        if (activeTab == Tab.DIAGNOSTICS) {
            diagnosticsPanel.runDiagnostics()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(heartbeatRunnable)
    }

    private fun initViews() {
        contentContainer = findViewById(R.id.content_container)
        tvProjectName = findViewById(R.id.tv_project_name)
        tvConnectionStatus = findViewById(R.id.tv_connection_status)
        tvAgentStatus = findViewById(R.id.tv_agent_status)
        btnDiagnostics = findViewById(R.id.btn_open_diagnostics)

        navAgent = findViewById(R.id.nav_agent)
        navEditor = findViewById(R.id.nav_editor)
        navExplorer = findViewById(R.id.nav_explorer)
        navChanges = findViewById(R.id.nav_changes)
        navTerminal = findViewById(R.id.nav_terminal)

        navIconAgent = findViewById(R.id.nav_icon_agent)
        navIconEditor = findViewById(R.id.nav_icon_editor)
        navIconExplorer = findViewById(R.id.nav_icon_explorer)
        navIconChanges = findViewById(R.id.nav_icon_changes)
        navIconTerminal = findViewById(R.id.nav_icon_terminal)

        navTextAgent = findViewById(R.id.nav_text_agent)
        navTextEditor = findViewById(R.id.nav_text_editor)
        navTextExplorer = findViewById(R.id.nav_text_explorer)
        navTextChanges = findViewById(R.id.nav_text_changes)
        navTextTerminal = findViewById(R.id.nav_text_terminal)
    }

    private fun initPanels() {
        agentPanel = AgentPanel(this, bridgeClient) { status ->
            tvAgentStatus.text = "● $status"
            when (status) {
                getString(R.string.status_ready) -> tvAgentStatus.setTextColor(Color.parseColor("#64748B"))
                getString(R.string.status_thinking) -> tvAgentStatus.setTextColor(Color.parseColor("#F59E0B"))
                getString(R.string.status_working), getString(R.string.status_running) -> tvAgentStatus.setTextColor(Color.parseColor("#3B82F6"))
                getString(R.string.status_error) -> tvAgentStatus.setTextColor(Color.parseColor("#EF4444"))
            }
        }

        editorPanel = EditorPanel(this, bridgeClient)

        explorerPanel = ExplorerPanel(this, bridgeClient) { filePath ->
            editorPanel.openFile(filePath)
            switchTab(Tab.EDITOR)
        }

        changesPanel = ChangesPanel(this, bridgeClient) { filePath ->
            editorPanel.openFile(filePath)
            switchTab(Tab.EDITOR)
        }

        terminalPanel = TerminalPanel(this, bridgeClient)

        diagnosticsPanel = DiagnosticsPanel(this, bridgeClient) {
            checkHealth()
        }

        settingsPanel = SettingsPanel(this, bridgeClient) {
            checkHealth()
        }
    }

    private fun setupListeners() {
        navAgent.setOnClickListener { switchTab(Tab.AGENT) }
        navEditor.setOnClickListener { switchTab(Tab.EDITOR) }
        navExplorer.setOnClickListener {
            switchTab(Tab.EXPLORER)
            explorerPanel.loadFiles()
        }
        navChanges.setOnClickListener {
            switchTab(Tab.CHANGES)
            changesPanel.loadChanges()
        }
        navTerminal.setOnClickListener { switchTab(Tab.TERMINAL) }

        btnDiagnostics.setOnClickListener {
            if (activeTab == Tab.DIAGNOSTICS) {
                switchTab(Tab.SETTINGS)
            } else {
                switchTab(Tab.DIAGNOSTICS)
                diagnosticsPanel.runDiagnostics()
            }
        }

        tvConnectionStatus.setOnClickListener {
            if (tvConnectionStatus.text.toString().contains("Offline")) {
                showTermuxSetupDialog()
            } else {
                checkHealth()
                Toast.makeText(this, "Checking connection...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showTermuxSetupDialog() {
        AlertDialog.Builder(this)
            .setTitle("CLIFrontend Standalone Engine")
            .setMessage("The embedded bridge engine runs directly within the app without needing Termux.\n\nTap 'Start Engine' to launch the background daemon.")
            .setPositiveButton("🚀 Start Engine") { _, _ ->
                com.antigravity.pocketgravity.api.BridgeService.start(this)
                Toast.makeText(this, "Starting embedded engine...", Toast.LENGTH_SHORT).show()
                handler.postDelayed({ checkHealth() }, 1000)
            }
            .setNeutralButton("Diagnostics") { _, _ ->
                switchTab(Tab.DIAGNOSTICS)
                diagnosticsPanel.runDiagnostics()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun switchTab(tab: Tab) {
        activeTab = tab
        contentContainer.removeAllViews()

        val activeColor = Color.parseColor("#3B82F6")
        val inactiveColor = Color.parseColor("#94A3B8")

        navIconAgent.setColorFilter(if (tab == Tab.AGENT) activeColor else inactiveColor)
        navTextAgent.setTextColor(if (tab == Tab.AGENT) activeColor else inactiveColor)

        navIconEditor.setColorFilter(if (tab == Tab.EDITOR) activeColor else inactiveColor)
        navTextEditor.setTextColor(if (tab == Tab.EDITOR) activeColor else inactiveColor)

        navIconExplorer.setColorFilter(if (tab == Tab.EXPLORER) activeColor else inactiveColor)
        navTextExplorer.setTextColor(if (tab == Tab.EXPLORER) activeColor else inactiveColor)

        navIconChanges.setColorFilter(if (tab == Tab.CHANGES) activeColor else inactiveColor)
        navTextChanges.setTextColor(if (tab == Tab.CHANGES) activeColor else inactiveColor)

        navIconTerminal.setColorFilter(if (tab == Tab.TERMINAL) activeColor else inactiveColor)
        navTextTerminal.setTextColor(if (tab == Tab.TERMINAL) activeColor else inactiveColor)

        when (tab) {
            Tab.AGENT -> contentContainer.addView(agentPanel.view)
            Tab.EDITOR -> contentContainer.addView(editorPanel.view)
            Tab.EXPLORER -> contentContainer.addView(explorerPanel.view)
            Tab.CHANGES -> contentContainer.addView(changesPanel.view)
            Tab.TERMINAL -> contentContainer.addView(terminalPanel.view)
            Tab.DIAGNOSTICS -> contentContainer.addView(diagnosticsPanel.view)
            Tab.SETTINGS -> contentContainer.addView(settingsPanel.view)
        }
    }

    private fun checkHealth() {
        bridgeClient.getHealth { res ->
            res.onSuccess { h ->
                tvConnectionStatus.text = "● Ready"
                tvConnectionStatus.setTextColor(Color.parseColor("#10B981"))
                tvProjectName.text = h.workspace.substringAfterLast("/").substringAfterLast("\\").ifEmpty { "demo" }
                agentPanel.setModelName(h.model)
                explorerPanel.currentPath = h.workspace
                changesPanel.workspace = h.workspace
                terminalPanel.cwd = h.workspace
                agentPanel.hideBridgeSetupCard()
            }.onFailure {
                tvConnectionStatus.text = "○ Offline"
                tvConnectionStatus.setTextColor(Color.parseColor("#EF4444"))
                agentPanel.showBridgeSetupCard { checkHealth() }
            }
        }
    }
}
