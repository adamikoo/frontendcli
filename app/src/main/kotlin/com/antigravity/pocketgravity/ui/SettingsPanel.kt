package com.antigravity.pocketgravity.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import com.antigravity.pocketgravity.R
import com.antigravity.pocketgravity.api.BridgeClient
import org.json.JSONArray
import org.json.JSONObject

class SettingsPanel(
    val context: Context,
    val bridgeClient: BridgeClient,
    val onSettingChanged: () -> Unit
) {
    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(Color.parseColor("#121418"))
    }

    enum class Category(val label: String, val icon: String) {
        BROWSER("Browser", "🌐"),
        ACCOUNT("Account", "👤"),
        MODELS("Models", "✨"),
        CUSTOMIZATIONS("Customizations", "⚡"),
        MCPS("MCPs", "🔌"),
        APPEARANCE("Appearance", "🎨"),
        GENERAL("General", "⚙️"),
        WORKSPACES("Workspaces", "📁")
    }

    private var activeCategory = Category.BROWSER

    private val headerLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(28, 24, 28, 12)
        setBackgroundColor(Color.parseColor("#16191E"))
    }

    private val titleTv = TextView(context).apply {
        text = "Antigravity Settings"
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#F1F5F9"))
    }

    private val categoryScroll = HorizontalScrollView(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 16
        }
        isHorizontalScrollBarEnabled = false
    }

    private val categoryRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    private val contentContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(28, 20, 28, 40)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private val scrollView = ScrollView(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        addView(contentContainer)
    }

    init {
        categoryScroll.addView(categoryRow)
        headerLayout.addView(titleTv)
        headerLayout.addView(categoryScroll)

        view.addView(headerLayout)
        view.addView(scrollView)

        renderCategoryTabs()
        renderActiveCategory()
    }

    private fun renderCategoryTabs() {
        categoryRow.removeAllViews()
        for (cat in Category.values()) {
            val isSelected = cat == activeCategory
            val chip = TextView(context).apply {
                text = "${cat.icon} ${cat.label}"
                textSize = 12f
                typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#94A3B8"))
                setPadding(24, 12, 24, 12)
                setBackgroundResource(if (isSelected) R.drawable.bg_chip else R.drawable.bg_rounded_card)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = 12
                }
                setOnClickListener {
                    activeCategory = cat
                    renderCategoryTabs()
                    renderActiveCategory()
                }
            }
            categoryRow.addView(chip)
        }
    }

    private fun renderActiveCategory() {
        contentContainer.removeAllViews()
        when (activeCategory) {
            Category.BROWSER -> renderBrowserSettings()
            Category.ACCOUNT -> renderAccountSettings()
            Category.MODELS -> renderModelsSettings()
            Category.CUSTOMIZATIONS -> renderCustomizationsSettings()
            Category.MCPS -> renderMcpsSettings()
            Category.APPEARANCE -> renderAppearanceSettings()
            Category.GENERAL -> renderGeneralSettings()
            Category.WORKSPACES -> renderWorkspacesSettings()
        }
    }

    // ==========================================
    // 1. BROWSER SETTINGS (Exact match to screenshot)
    // ==========================================
    private fun renderBrowserSettings() {
        addPageHeader("Browser Settings", "Configure the browser subagent. It requires Google Chrome to be installed.")

        val loadingTv = TextView(context).apply {
            text = "Loading browser settings..."
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12f
        }
        contentContainer.addView(loadingTv)

        bridgeClient.getBrowserSettings { res ->
            contentContainer.removeView(loadingTv)
            val settings = res.getOrDefault(JSONObject())
            val enableTools = settings.optBoolean("enable_browser_tools", true)
            val jsPolicy = settings.optString("javascript_policy", "Request Review")
            val enableNotifs = settings.optBoolean("enable_notifications", true)
            val enableSounds = settings.optBoolean("enable_sounds", false)
            val actuationRules = settings.optJSONArray("actuation_rules") ?: JSONArray().put("*")

            addSectionHeader("General")

            // Enable Browser Tools
            addToggleCard(
                title = "Enable Browser Tools",
                desc = "When enabled, Agent can use browser tools to open URLs, read web pages, and interact with browser content. This allows the Agent access to important (and often critical) knowledge and methods of validation, but any browser integration does increase exposure to external malicious parties for security exploits.",
                isChecked = enableTools
            ) { checked ->
                settings.put("enable_browser_tools", checked)
                bridgeClient.saveBrowserSettings(settings) {
                    Toast.makeText(context, "Browser tools ${if (checked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                }
            }

            // Browser Javascript Execution Policy
            val policyCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_rounded_card)
                setPadding(24, 20, 24, 20)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 14
                }
            }
            val policyRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val policyLabel = TextView(context).apply {
                text = "Browser Javascript Execution Policy"
                setTextColor(Color.parseColor("#F1F5F9"))
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val policyBtn = Button(context).apply {
                text = "$jsPolicy ▾"
                textSize = 11.5f
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.bg_chip)
                setOnClickListener {
                    val options = arrayOf("Request Review", "Always Allow", "Disabled")
                    AlertDialog.Builder(context)
                        .setTitle("Select JavaScript Execution Policy")
                        .setItems(options) { _, which ->
                            val selected = options[which]
                            settings.put("javascript_policy", selected)
                            bridgeClient.saveBrowserSettings(settings) {
                                text = "$selected ▾"
                                Toast.makeText(context, "Policy: $selected", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .show()
                }
            }
            policyRow.addView(policyLabel)
            policyRow.addView(policyBtn)
            policyCard.addView(policyRow)

            val policyDesc = TextView(context).apply {
                text = "Controls whether the agent can run custom JavaScript to automate complex browser actions."
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11.5f
                setPadding(0, 8, 0, 0)
            }
            policyCard.addView(policyDesc)
            contentContainer.addView(policyCard)

            // Enable Notifications for Agent
            addToggleCard(
                title = "Enable Notifications for Agent",
                desc = "When enabled, Agent will show browser notifications when user action is needed or execution finishes.",
                isChecked = enableNotifs
            ) { checked ->
                settings.put("enable_notifications", checked)
                bridgeClient.saveBrowserSettings(settings) {}
            }

            // Enable Sounds for Agent
            addToggleCard(
                title = "Enable Sounds for Agent",
                desc = "When enabled, Antigravity will play a sound when Agent finishes generating a response.",
                isChecked = enableSounds
            ) { checked ->
                settings.put("enable_sounds", checked)
                bridgeClient.saveBrowserSettings(settings) {}
            }

            addSectionHeader("Actuation Permissions")

            // Browser Actuation Rules
            val rulesCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_rounded_card)
                setPadding(24, 20, 24, 20)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 14
                }
            }
            val rulesRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val rulesTitle = TextView(context).apply {
                text = "Browser Actuation Rules"
                setTextColor(Color.parseColor("#F1F5F9"))
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val editBtn = Button(context).apply {
                text = "Edit"
                textSize = 11.5f
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.bg_chip)
                setOnClickListener {
                    val input = EditText(context).apply {
                        setText(actuationRules.join("\n").replace("\"", ""))
                        setTextColor(Color.WHITE)
                        typeface = Typeface.MONOSPACE
                    }
                    AlertDialog.Builder(context)
                        .setTitle("Browser Actuation Rules")
                        .setMessage("Allowed and denied URL regexes (one per line):")
                        .setView(input)
                        .setPositiveButton("Save") { _, _ ->
                            val lines = input.text.toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                            val newRules = JSONArray()
                            for (l in lines) newRules.put(l)
                            settings.put("actuation_rules", newRules)
                            bridgeClient.saveBrowserSettings(settings) {
                                Toast.makeText(context, "Actuation rules updated", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            rulesRow.addView(rulesTitle)
            rulesRow.addView(editBtn)
            rulesCard.addView(rulesRow)

            val rulesDesc = TextView(context).apply {
                text = "Configure allowed and denied URLs for browser actuation."
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11.5f
                setPadding(0, 8, 0, 0)
            }
            rulesCard.addView(rulesDesc)
            contentContainer.addView(rulesCard)
        }
    }

    // ==========================================
    // 2. ACCOUNT & LIMITS SETTINGS
    // ==========================================
    private fun renderAccountSettings() {
        addPageHeader("Account & Limits", "Manage Google authentication, subscription tier, and token limits.")

        val loadingTv = TextView(context).apply {
            text = "Fetching account and usage limits..."
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12f
        }
        contentContainer.addView(loadingTv)

        bridgeClient.getLimits { limitsRes ->
            bridgeClient.getHealth { healthRes ->
                contentContainer.removeView(loadingTv)
                val health = healthRes.getOrNull()
                val limitsObj = limitsRes.getOrDefault(JSONObject())

                val isAuth = health?.authenticated ?: false
                val tokenFile = health?.tokenFile ?: "None"
                val tier = limitsObj.optString("tier", "FREE")
                val creditOvercharge = limitsObj.optBoolean("credit_overcharge", false)
                val limitsInner = limitsObj.optJSONObject("limits") ?: JSONObject()

                // Account info card
                val accCard = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.drawable.bg_rounded_card)
                    setPadding(24, 20, 24, 20)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = 16
                    }
                }
                val topRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val emailTv = TextView(context).apply {
                    text = if (isAuth) "Google Account Connected" else "Unauthenticated"
                    setTextColor(Color.parseColor("#F1F5F9"))
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val tierBadge = TextView(context).apply {
                    text = tier
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#10B981"))
                    setBackgroundColor(Color.parseColor("#064E3B"))
                    setPadding(16, 6, 16, 6)
                }
                topRow.addView(emailTv)
                topRow.addView(tierBadge)
                accCard.addView(topRow)

                val tokenPathTv = TextView(context).apply {
                    text = "Token: $tokenFile"
                    textSize = 11.5f
                    setTextColor(Color.parseColor("#64748B"))
                    setPadding(0, 8, 0, 0)
                }
                accCard.addView(tokenPathTv)
                contentContainer.addView(accCard)

                // Quota & Limits Card
                addSectionHeader("Usage Limits & Quota")
                val reqRem = limitsInner.optInt("requests_remaining", 1340)
                val reqTotal = limitsInner.optInt("requests_per_day", 1500)
                val tokRem = limitsInner.optInt("tokens_remaining", 948200)
                val tokTotal = limitsInner.optInt("tokens_per_minute", 1000000)

                val usageCard = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.drawable.bg_rounded_card)
                    setPadding(24, 20, 24, 20)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = 16
                    }
                }
                val reqText = TextView(context).apply {
                    text = "Requests: $reqRem / $reqTotal daily remaining"
                    setTextColor(Color.parseColor("#F8FAFC"))
                    textSize = 12.5f
                }
                val tokText = TextView(context).apply {
                    text = "Tokens: ${tokRem / 1000}k / ${tokTotal / 1000}k TPM remaining"
                    setTextColor(Color.parseColor("#F8FAFC"))
                    textSize = 12.5f
                    setPadding(0, 6, 0, 0)
                }
                usageCard.addView(reqText)
                usageCard.addView(tokText)
                contentContainer.addView(usageCard)

                // Credit Overcharge Toggle
                addToggleCard(
                    title = "Credit Overcharge",
                    desc = "Allow agent to automatically use overage credits when quota limits are reached.",
                    isChecked = creditOvercharge
                ) { checked ->
                    bridgeClient.setCreditOvercharge(checked) {
                        Toast.makeText(context, "Credit overcharge ${if (checked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                    }
                }

                // Authentication Actions
                addSectionHeader("Authentication Actions")
                addSettingRow("Sign in with Google", "Initiate Google OAuth PKCE flow in browser") {
                    Toast.makeText(context, "Opening Google Sign-In...", Toast.LENGTH_SHORT).show()
                    bridgeClient.startAuthLogin { loginRes ->
                        loginRes.onSuccess { url ->
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    }
                }

                addSettingRow("Paste OAuth Token", "Manually paste Antigravity OAuth Token or Refresh Token") {
                    val input = EditText(context).apply {
                        hint = "Paste OAuth Token"
                        setTextColor(Color.WHITE)
                    }
                    AlertDialog.Builder(context)
                        .setTitle("Paste Token")
                        .setView(input)
                        .setPositiveButton("Save") { _, _ ->
                            val t = input.text.toString().trim()
                            if (t.isNotEmpty()) {
                                bridgeClient.saveToken(t) {
                                    Toast.makeText(context, "Token saved!", Toast.LENGTH_SHORT).show()
                                    renderAccountSettings()
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }

                if (isAuth) {
                    addSettingRow("Log Out of Google", "Delete all saved OAuth tokens and credentials") {
                        AlertDialog.Builder(context)
                            .setTitle("Confirm Logout")
                            .setMessage("Are you sure you want to log out? All stored token files will be permanently deleted.")
                            .setPositiveButton("Log Out") { _, _ ->
                                bridgeClient.logout {
                                    Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                                    renderAccountSettings()
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
        }
    }

    // ==========================================
    // 3. MODELS SETTINGS
    // ==========================================
    private fun renderModelsSettings() {
        addPageHeader("Models & Reasoning", "Select default AI model and thinking effort level.")

        addSectionHeader("Reasoning / Thinking Effort")
        val effortRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }
        val efforts = listOf("low", "medium", "high")
        for (eff in efforts) {
            val btn = Button(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = 8
                }
                text = eff.capitalize()
                textSize = 11.5f
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.bg_chip)
                setOnClickListener {
                    bridgeClient.setEffort(eff) {
                        Toast.makeText(context, "Effort set to $eff", Toast.LENGTH_SHORT).show()
                        onSettingChanged()
                    }
                }
            }
            effortRow.addView(btn)
        }
        contentContainer.addView(effortRow)

        addSectionHeader("Active Model Catalog")
        val loadingTv = TextView(context).apply {
            text = "Loading models catalog..."
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12f
        }
        contentContainer.addView(loadingTv)

        bridgeClient.getModels { res ->
            contentContainer.removeView(loadingTv)
            res.onSuccess { models ->
                for (m in models) {
                    val card = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setBackgroundResource(if (m.selected) R.drawable.bg_chip else R.drawable.bg_rounded_card)
                        setPadding(24, 18, 24, 18)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = 10
                        }
                        setOnClickListener {
                            bridgeClient.setModel(m.id) {
                                Toast.makeText(context, "Model set to ${m.name}", Toast.LENGTH_SHORT).show()
                                onSettingChanged()
                                renderModelsSettings()
                            }
                        }
                    }
                    val nameTv = TextView(context).apply {
                        text = m.name
                        setTextColor(Color.parseColor("#F8FAFC"))
                        textSize = 13f
                        typeface = if (m.selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val checkTv = TextView(context).apply {
                        text = if (m.selected) "✓ Active" else "Select"
                        textSize = 11.5f
                        setTextColor(if (m.selected) Color.parseColor("#4ADE80") else Color.parseColor("#64748B"))
                    }
                    card.addView(nameTv)
                    card.addView(checkTv)
                    contentContainer.addView(card)
                }
            }
        }
    }

    // ==========================================
    // 4. CUSTOMIZATIONS (Skills, Rules, Workflows)
    // ==========================================
    private fun renderCustomizationsSettings() {
        addPageHeader("Customizations", "Manage agent skills, behavioral rules, and slash workflows.")

        val loadingTv = TextView(context).apply {
            text = "Loading skills and rules..."
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12f
        }
        contentContainer.addView(loadingTv)

        bridgeClient.getCustomizations { res ->
            contentContainer.removeView(loadingTv)
            val json = res.getOrDefault(JSONObject())
            val rules = json.optJSONArray("rules") ?: JSONArray()
            val workflows = json.optJSONArray("workflows") ?: JSONArray()
            val skills = json.optJSONArray("skills") ?: JSONArray()

            // Rules Section
            addSectionHeader("Agent Behavioral Rules")
            for (i in 0 until rules.length()) {
                val r = rules.getJSONObject(i)
                val rName = r.optString("name")
                val rDesc = r.optString("description")
                val rEnabled = r.optBoolean("enabled", true)

                addToggleCard(
                    title = rName,
                    desc = rDesc,
                    isChecked = rEnabled
                ) { checked ->
                    bridgeClient.saveCustomization(rName, checked) {
                        Toast.makeText(context, "$rName ${if (checked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Workflows Section
            addSectionHeader("Workflows & Slash Commands")
            for (i in 0 until workflows.length()) {
                val wf = workflows.getJSONObject(i)
                val wName = wf.optString("name")
                val cmd = wf.optString("command", "/$wName")

                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setBackgroundResource(R.drawable.bg_rounded_card)
                    setPadding(24, 16, 24, 16)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = 10
                    }
                }
                val cmdTv = TextView(context).apply {
                    text = cmd
                    typeface = Typeface.MONOSPACE
                    setTextColor(Color.parseColor("#38BDF8"))
                    textSize = 12.5f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val runTv = TextView(context).apply {
                    text = "Workflow"
                    textSize = 11f
                    setTextColor(Color.parseColor("#64748B"))
                }
                card.addView(cmdTv)
                card.addView(runTv)
                contentContainer.addView(card)
            }

            // Skills Section
            addSectionHeader("Agent Skills Catalog")
            for (i in 0 until skills.length()) {
                val s = skills.getJSONObject(i)
                val sName = s.optString("name")
                val sPath = s.optString("path")

                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setBackgroundResource(R.drawable.bg_rounded_card)
                    setPadding(24, 16, 24, 16)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = 10
                    }
                }
                val nameTv = TextView(context).apply {
                    text = "⚡ $sName"
                    setTextColor(Color.parseColor("#F1F5F9"))
                    textSize = 12.5f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val pathTv = TextView(context).apply {
                    text = if (sPath.startsWith("builtin/")) "Built-in" else "Custom"
                    textSize = 10.5f
                    setTextColor(Color.parseColor("#94A3B8"))
                }
                card.addView(nameTv)
                card.addView(pathTv)
                contentContainer.addView(card)
            }
        }
    }

    // ==========================================
    // 5. MCP SERVERS
    // ==========================================
    private fun renderMcpsSettings() {
        addPageHeader("Model Context Protocol (MCP)", "Connect tool servers and knowledge systems to Antigravity.")

        val loadingTv = TextView(context).apply {
            text = "Loading MCP servers..."
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12f
        }
        contentContainer.addView(loadingTv)

        bridgeClient.getMcps { res ->
            contentContainer.removeView(loadingTv)
            val json = res.getOrDefault(JSONObject())
            val servers = json.optJSONObject("mcpServers") ?: JSONObject()

            addSectionHeader("Configured MCP Servers")
            val keys = servers.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val sObj = servers.getJSONObject(key)
                val cmd = sObj.optString("command", "npx")
                val isEnabled = sObj.optBoolean("enabled", true)

                addToggleCard(
                    title = "🔌 $key",
                    desc = "Command: $cmd ${sObj.optJSONArray("args")?.join(" ") ?: ""}",
                    isChecked = isEnabled
                ) { checked ->
                    sObj.put("enabled", checked)
                    servers.put(key, sObj)
                    json.put("mcpServers", servers)
                    bridgeClient.saveMcps(json) {
                        Toast.makeText(context, "$key ${if (checked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Add MCP Server Button
            val addBtn = Button(context).apply {
                text = "+ Add MCP Server"
                textSize = 12f
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.bg_chip)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 88).apply {
                    topMargin = 16
                }
                setOnClickListener {
                    val nameInput = EditText(context).apply { hint = "Server Name (e.g. context7)" }
                    val cmdInput = EditText(context).apply { hint = "Command (e.g. npx)" }
                    val argsInput = EditText(context).apply { hint = "Arguments (e.g. -y context7@latest)" }

                    val form = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(32, 20, 32, 20)
                        addView(nameInput)
                        addView(cmdInput)
                        addView(argsInput)
                    }

                    AlertDialog.Builder(context)
                        .setTitle("Add MCP Server")
                        .setView(form)
                        .setPositiveButton("Add") { _, _ ->
                            val sName = nameInput.text.toString().trim()
                            val sCmd = cmdInput.text.toString().trim()
                            val sArgs = argsInput.text.toString().trim().split("\\s+".toRegex())

                            if (sName.isNotEmpty() && sCmd.isNotEmpty()) {
                                val newObj = JSONObject().apply {
                                    put("command", sCmd)
                                    put("args", JSONArray(sArgs))
                                    put("enabled", true)
                                }
                                servers.put(sName, newObj)
                                json.put("mcpServers", servers)
                                bridgeClient.saveMcps(json) {
                                    Toast.makeText(context, "Added MCP server $sName", Toast.LENGTH_SHORT).show()
                                    renderMcpsSettings()
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            contentContainer.addView(addBtn)
        }
    }

    // ==========================================
    // 6. APPEARANCE SETTINGS
    // ==========================================
    private fun renderAppearanceSettings() {
        addPageHeader("Appearance & Editor", "Customize editor typography, syntax highlighting, and theme.")

        addSectionHeader("Editor Typography")
        addSettingRow("Editor Font Size", "12 sp") {
            val sizes = arrayOf("10 sp", "12 sp", "14 sp", "16 sp", "18 sp")
            AlertDialog.Builder(context)
                .setTitle("Select Font Size")
                .setItems(sizes) { _, which ->
                    Toast.makeText(context, "Font size set to ${sizes[which]}", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        addSettingRow("Tab Indentation", "2 spaces") {
            val tabs = arrayOf("2 spaces", "4 spaces", "8 spaces")
            AlertDialog.Builder(context)
                .setTitle("Select Tab Size")
                .setItems(tabs) { _, which ->
                    Toast.makeText(context, "Tab size set to ${tabs[which]}", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        addSectionHeader("Display Options")
        addToggleCard(
            title = "Show Line Numbers",
            desc = "Display vertical gutter with code line numbers in the editor.",
            isChecked = true
        ) {}

        addToggleCard(
            title = "ANSI Colored Terminal Output",
            desc = "Enable 256-color and 24-bit truecolor ANSI parsing in terminal views.",
            isChecked = true
        ) {}
    }

    // ==========================================
    // 7. GENERAL SETTINGS
    // ==========================================
    private fun renderGeneralSettings() {
        addPageHeader("General Settings", "Network targets, Termux bridge command, and daemon preferences.")

        addSectionHeader("Bridge Connection")
        addSettingRow("Bridge Target URL", bridgeClient.baseUrl) {
            val presets = arrayOf(
                "http://127.0.0.1:8765 (Termux on Device / Local)",
                "http://10.0.2.2:8765 (Android Emulator)",
                "http://192.168.1.104:8765 (Host PC Wi-Fi)",
                "Custom URL..."
            )
            AlertDialog.Builder(context)
                .setTitle("Select Bridge Target")
                .setItems(presets) { _, which ->
                    when (which) {
                        0 -> { bridgeClient.baseUrl = "http://127.0.0.1:8765"; renderGeneralSettings(); onSettingChanged() }
                        1 -> { bridgeClient.baseUrl = "http://10.0.2.2:8765"; renderGeneralSettings(); onSettingChanged() }
                        2 -> { bridgeClient.baseUrl = "http://192.168.1.104:8765"; renderGeneralSettings(); onSettingChanged() }
                        3 -> {
                            val input = EditText(context).apply { setText(bridgeClient.baseUrl) }
                            AlertDialog.Builder(context)
                                .setTitle("Custom URL")
                                .setView(input)
                                .setPositiveButton("Save") { _, _ ->
                                    val u = input.text.toString().trim()
                                    if (u.isNotEmpty()) {
                                        bridgeClient.baseUrl = u
                                        renderGeneralSettings()
                                        onSettingChanged()
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    }
                }
                .show()
        }

        val termuxCmd = "pkg install -y python curl && curl -sL https://raw.githubusercontent.com/adamikoo/frontendcli/main/bridge/start.sh -o ~/start.sh && bash ~/start.sh"
        addSettingRow("Termux Launch Command", "Tap to copy one-line startup command") {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Termux Command", termuxCmd)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        addSectionHeader("Preferences")
        addToggleCard(
            title = "Auto-save Modified Files",
            desc = "Automatically write dirty editor buffer changes to disk on tab switch.",
            isChecked = true
        ) {}

        addToggleCard(
            title = "Crash & Usage Telemetry",
            desc = "Send anonymous error diagnostics to improve PocketGravity stability.",
            isChecked = false
        ) {}
    }

    // ==========================================
    // 8. WORKSPACES SETTINGS
    // ==========================================
    private fun renderWorkspacesSettings() {
        addPageHeader("Workspaces", "Switch active development root and manage project paths.")

        val loadingTv = TextView(context).apply {
            text = "Fetching active workspace..."
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12f
        }
        contentContainer.addView(loadingTv)

        bridgeClient.getWorkspace { res ->
            contentContainer.removeView(loadingTv)
            val currentWk: String = res.getOrDefault("/workspace")

            addSectionHeader("Active Project Workspace")
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_rounded_card)
                setPadding(24, 20, 24, 20)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 14
                }
            }
            val titleTv = TextView(context).apply {
                text = "📁 Active Directory"
                setTextColor(Color.parseColor("#F1F5F9"))
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
            }
            val pathTv = TextView(context).apply {
                text = currentWk
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 12f
                setPadding(0, 6, 0, 0)
            }
            card.addView(titleTv)
            card.addView(pathTv)
            contentContainer.addView(card)

            addSettingRow("Switch Workspace Directory", "Set current working folder for agent and explorer") {
                val input = EditText(context).apply {
                    setText(currentWk as CharSequence)
                    setTextColor(Color.WHITE)
                    typeface = Typeface.MONOSPACE
                }
                AlertDialog.Builder(context)
                    .setTitle("Change Workspace")
                    .setView(input)
                    .setPositiveButton("Apply") { _, _ ->
                        val newPath = input.text.toString().trim()
                        if (newPath.isNotEmpty()) {
                            bridgeClient.setWorkspace(newPath) {
                                Toast.makeText(context, "Workspace updated to $newPath", Toast.LENGTH_SHORT).show()
                                renderWorkspacesSettings()
                                onSettingChanged()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    // ==========================================
    // UI Helpers
    // ==========================================
    private fun addPageHeader(title: String, subtitle: String) {
        val h = TextView(context).apply {
            text = title
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F8FAFC"))
        }
        val sub = TextView(context).apply {
            text = subtitle
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 4, 0, 16)
        }
        contentContainer.addView(h)
        contentContainer.addView(sub)
    }

    private fun addSectionHeader(title: String) {
        val tv = TextView(context).apply {
            text = title.toUpperCase()
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 16, 0, 8)
        }
        contentContainer.addView(tv)
    }

    private fun addToggleCard(title: String, desc: String, isChecked: Boolean, onChecked: (Boolean) -> Unit) {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_rounded_card)
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 12
            }
        }
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val label = TextView(context).apply {
            text = title
            setTextColor(Color.parseColor("#F1F5F9"))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sw = Switch(context).apply {
            this.isChecked = isChecked
            setOnCheckedChangeListener { _, checked -> onChecked(checked) }
        }
        topRow.addView(label)
        topRow.addView(sw)
        card.addView(topRow)

        if (desc.isNotEmpty()) {
            val d = TextView(context).apply {
                text = desc
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11.5f
                setPadding(0, 8, 0, 0)
            }
            card.addView(d)
        }
        contentContainer.addView(card)
    }

    private fun addSettingRow(label: String, value: String, onClick: () -> Unit) {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_rounded_card)
            setPadding(24, 18, 24, 18)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 10
            }
            setOnClickListener { onClick() }
        }
        val labelView = TextView(context).apply {
            text = label
            setTextColor(Color.parseColor("#F1F5F9"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val valueView = TextView(context).apply {
            text = value
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11.5f
            setPadding(12, 0, 0, 0)
        }
        card.addView(labelView)
        card.addView(valueView)
        contentContainer.addView(card)
    }
}
