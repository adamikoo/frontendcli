# Graph Report - clifrontend  (2026-09-07)

## Corpus Check
- 32 files · ~127,264 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 312 nodes · 564 edges · 27 communities (21 shown, 6 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 5 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `f7d2d3e3`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- BridgeServer
- MainActivity
- BridgeClient
- BridgeService
- server.py
- DebugLogger
- EditorPanel
- AgentPanel
- RuntimeManager
- ExplorerPanel
- Models.kt
- TerminalPanel
- start.sh
- build.sh
- status.sh
- stop.sh
- Antigravity CLI (`agy`) Interface Specification
- CLIFrontend — Native Android Mobile IDE for Antigravity CLI
- CLIFrontend Architecture
- Troubleshooting CLIFrontend
- Building CLIFrontend
- Termux Bridge Implementation & Integration
- agy

## God Nodes (most connected - your core abstractions)
1. `BridgeServer` - 35 edges
2. `BridgeClient` - 29 edges
3. `MainActivity` - 24 edges
4. `AgentPanel` - 18 edges
5. `BridgeService` - 12 edges
6. `RuntimeManager` - 12 edges
7. `EditorPanel` - 12 edges
8. `ExplorerPanel` - 12 edges
9. `DebugLogger` - 11 edges
10. `TerminalPanel` - 10 edges

## Surprising Connections (you probably didn't know these)
- `MainActivity` --references--> `BridgeClient`  [EXTRACTED]
  app/src/main/kotlin/com/antigravity/pocketgravity/MainActivity.kt → app/src/main/kotlin/com/antigravity/pocketgravity/api/BridgeClient.kt
- `MainActivity` --references--> `AgentPanel`  [EXTRACTED]
  app/src/main/kotlin/com/antigravity/pocketgravity/MainActivity.kt → app/src/main/kotlin/com/antigravity/pocketgravity/ui/AgentPanel.kt
- `MainActivity` --references--> `ChangesPanel`  [EXTRACTED]
  app/src/main/kotlin/com/antigravity/pocketgravity/MainActivity.kt → app/src/main/kotlin/com/antigravity/pocketgravity/ui/ChangesPanel.kt
- `MainActivity` --references--> `DiagnosticsPanel`  [EXTRACTED]
  app/src/main/kotlin/com/antigravity/pocketgravity/MainActivity.kt → app/src/main/kotlin/com/antigravity/pocketgravity/ui/DiagnosticsPanel.kt
- `MainActivity` --references--> `EditorPanel`  [EXTRACTED]
  app/src/main/kotlin/com/antigravity/pocketgravity/MainActivity.kt → app/src/main/kotlin/com/antigravity/pocketgravity/ui/EditorPanel.kt

## Import Cycles
- None detected.

## Communities (27 total, 6 thin omitted)

### Community 0 - "BridgeServer"
Cohesion: 0.19
Nodes (5): BridgeServer, JSONObject, ByteArray, ServerSocket, Socket

### Community 1 - "MainActivity"
Cohesion: 0.11
Nodes (17): Activity, ModelItem, LinearLayout, TextView, MainActivity, Tab, AGENT, CHANGES (+9 more)

### Community 2 - "BridgeClient"
Cohesion: 0.13
Nodes (5): BridgeClient, GitChange, ChangesPanel, LinearLayout, T

### Community 3 - "BridgeService"
Cohesion: 0.16
Nodes (9): BridgeService, Context, DiagnosticsPanel, LinearLayout, IBinder, Intent, Notification, PowerManager (+1 more)

### Community 4 - "server.py"
Cohesion: 0.17
Nodes (11): BaseHTTPRequestHandler, BridgeHandler, cleanup_pid(), get_agy_version(), get_models(), get_settings(), run(), save_settings() (+3 more)

### Community 6 - "EditorPanel"
Cohesion: 0.24
Nodes (6): afterTextChanged(), EditorPanel, LinearLayout, onTextChanged(), OpenTab, Editable

### Community 7 - "AgentPanel"
Cohesion: 0.23
Nodes (4): AgentPanel, LinearLayout, TextView, ImageView

### Community 9 - "ExplorerPanel"
Cohesion: 0.39
Nodes (3): FileItem, ExplorerPanel, LinearLayout

### Community 10 - "Models.kt"
Cohesion: 0.13
Nodes (13): ChatMessage, HealthResponse, JSONObject, MessageStatus, ACTIVE, DONE, ERROR, Role (+5 more)

### Community 11 - "TerminalPanel"
Cohesion: 0.22
Nodes (5): AnsiColorParser, DiffColorizer, LinearLayout, TerminalPanel, View

### Community 12 - "start.sh"
Cohesion: 0.36
Nodes (5): check_health(), is_running(), kill_port_process(), PORT, start.sh script

### Community 19 - "Antigravity CLI (`agy`) Interface Specification"
Cohesion: 0.13
Nodes (14): 1. Binary & System Environment, 1. Initialization Event (`init`), 2. Authentication State & Login Flow, 2. Turn Step Updates (`step_update`), 3. Command-Line Options & Execution Flags, 3. Final Result (`result`), 4. Machine-Readable Streaming Protocol (`stream-json`), 5. Model Catalog (+6 more)

### Community 20 - "CLIFrontend — Native Android Mobile IDE for Antigravity CLI"
Cohesion: 0.17
Nodes (11): 1. Features, 2. Zero-Setup Flow, 3. Architecture & Security, 4. Project Layout, 5. Building the APK, 6. Running the Bridge, 7. Known Android System Constraints, CLIFrontend — Native Android Mobile IDE for Antigravity CLI (+3 more)

### Community 22 - "CLIFrontend Architecture"
Cohesion: 0.33
Nodes (5): 1. System Overview, 2. IPC & Bridge Design, 3. Security Considerations, CLIFrontend Architecture, Endpoints

### Community 23 - "Troubleshooting CLIFrontend"
Cohesion: 0.33
Nodes (5): 1. Connection & Diagnostics, 2. Authentication, 3. Terminal Issues, 4. File Permission Errors, Troubleshooting CLIFrontend

### Community 24 - "Building CLIFrontend"
Cohesion: 0.40
Nodes (4): 1. Prerequisites, 2. Automated Build, 3. Verification, Building CLIFrontend

### Community 25 - "Termux Bridge Implementation & Integration"
Cohesion: 0.40
Nodes (4): 1. Environment Detection Strategy, 2. Zero-Setup Experience, 3. Bridge Process Details, Termux Bridge Implementation & Integration

## Knowledge Gaps
- **50 isolated node(s):** `AGENT`, `EDITOR`, `EXPLORER`, `CHANGES`, `TERMINAL` (+45 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **6 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MainActivity` connect `MainActivity` to `BridgeClient`, `BridgeService`, `EditorPanel`, `AgentPanel`, `ExplorerPanel`, `TerminalPanel`?**
  _High betweenness centrality (0.235) - this node is a cross-community bridge._
- **Why does `BridgeServer` connect `BridgeServer` to `BridgeService`?**
  _High betweenness centrality (0.142) - this node is a cross-community bridge._
- **Why does `BridgeClient` connect `BridgeClient` to `MainActivity`, `BridgeService`, `EditorPanel`, `ExplorerPanel`, `Models.kt`, `TerminalPanel`?**
  _High betweenness centrality (0.134) - this node is a cross-community bridge._
- **What connects `AGENT`, `EDITOR`, `EXPLORER` to the rest of the system?**
  _50 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `MainActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.10804597701149425 - nodes in this community are weakly interconnected._
- **Should `BridgeClient` be split into smaller, more focused modules?**
  _Cohesion score 0.12962962962962962 - nodes in this community are weakly interconnected._
- **Should `Models.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.13333333333333333 - nodes in this community are weakly interconnected._