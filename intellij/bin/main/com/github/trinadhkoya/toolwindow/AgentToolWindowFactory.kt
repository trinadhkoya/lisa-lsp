package com.github.trinadhkoya.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import javax.swing.JPanel
import com.intellij.openapi.util.Disposer

import com.github.trinadhkoya.LisaLspServerSupportProvider
import com.github.trinadhkoya.lisaintellijplugin.services.MyProjectService
import com.intellij.openapi.components.service
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.openapi.fileEditor.FileEditorManager
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.jsonrpc.Endpoint
import java.util.concurrent.CompletableFuture
import com.intellij.openapi.application.ApplicationManager

class AgentToolWindowFactory : ToolWindowFactory, DumbAware {

    companion object {
        private val panels = mutableMapOf<Project, AgentPanel>()

        fun getPanel(project: Project): AgentPanel? = panels[project]
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val agentPanel = AgentPanel(project)
        panels[project] = agentPanel
        val content = ContentFactory.getInstance().createContent(agentPanel.component, "", false)
        toolWindow.contentManager.addContent(content)
    }

    class AgentPanel(private val project: Project) {
        val component = JPanel(BorderLayout())
        private val browser = JBCefBrowser()
        private var jsQuery: JBCefJSQuery? = null

        fun sendAction(action: String, context: Map<String, String>) {
            val file = context["file"] ?: ""
            val selection = context["selection"] ?: ""
            val language = context["language"] ?: ""
            
            val safeFile = file.replace("\"", "\\\"")
            val safeSelection = selection.replace("\"", "\\\"").replace("\n", "\\n")
            
            val code = """
                window.postMessage({
                    command: 'setAction',
                    action: '$action',
                    context: {
                        file: "$safeFile",
                        selection: "$safeSelection",
                        language: "$language"
                    }
                }, '*');
            """
            browser.cefBrowser.executeJavaScript(code, browser.cefBrowser.url, 0)
        }

        fun sendProjectContext(count: Int, fileList: String) {
            val safeFileList = fileList.replace("\"", "\\\"").replace("\n", "\\n")
            val code = """
                window.postMessage({
                    command: 'projectContext',
                    count: $count,
                    files: "$safeFileList"
                }, '*');
            """
            browser.cefBrowser.executeJavaScript(code, browser.cefBrowser.url, 0)
        }

        private fun readProject() {
            ApplicationManager.getApplication().invokeLater {
                val result = com.intellij.openapi.ui.Messages.showYesNoDialog(project, 
                    "Allow LISA to scan and index your project files? This will read file names and structure to provide better context.", 
                    "Project Context Permission", 
                    com.intellij.openapi.ui.Messages.getQuestionIcon())
                    
                if (result == com.intellij.openapi.ui.Messages.YES) {
                    val files = mutableListOf<String>()
                    val base = project.basePath ?: ""
                    com.intellij.openapi.roots.ProjectFileIndex.getInstance(project).iterateContent { file ->
                        if (!file.isDirectory && !file.name.startsWith(".")) {
                            files.add(file.path.replace(base, "").trimStart('/'))
                        }
                        true
                    }
                    val limitedFiles = files.take(500)
                    val fileList = limitedFiles.joinToString("\\n")
                    sendProjectContext(files.size, fileList)
                }
            }
        }

        init {
            component.add(browser.component, BorderLayout.CENTER)
            setupBrowser()
        }

        private fun setupBrowser() {
            // Register JS Query handler to receive messages from UI
            val query = JBCefJSQuery.create(browser as JBCefBrowser)
            Disposer.register(browser, query)
            jsQuery = query
            
            query.addHandler { msg ->
                handleMessage(msg)
                null
            }

            // Inject JS bridge when page loads
            browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    if (browser != null) {
                        val injection = query.inject("msg")
                        val script = """
                            window.lisa = { 
                                postMessage: function(msg) { 
                                    $injection 
                                } 
                            };
                        """.trimIndent()
                        // Use null URL to avoid security context issues with loadHTML
                        browser.executeJavaScript(script, null, 0)
                    }
                }
            }, browser.cefBrowser)

            loadContent()
        }

        private fun loadContent() {
            val htmlContent = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <style>
                    :root {
                        --bg-app: #09090b;
                        --bg-panel: #18181b;
                        --bg-input: #27272a;
                        --border: #3f3f46;
                        --text-primary: #e4e4e7;
                        --text-secondary: #a1a1aa;
                        --accent: #3b82f6;
                        --accent-hover: #2563eb;
                        --font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    }

                    * { box-sizing: border-box; }
                    
                    body {
                        background: var(--bg-app);
                        color: var(--text-primary);
                        font-family: var(--font-family);
                        margin: 0;
                        padding: 0;
                        height: 100vh;
                        display: flex;
                        flex-direction: column;
                        overflow: hidden;
                        font-size: 13px;
                        line-height: 1.5;
                    }

                    header {
                        background: var(--bg-app);
                        padding: 16px 20px;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        border-bottom: 1px solid var(--border);
                        flex-shrink: 0;
                    }
                    .header-title {
                        font-weight: 600;
                        font-size: 18px;
                        display: flex;
                        align-items: center;
                        gap: 12px;
                        color: var(--text-primary);
                    }
                    .header-title img {
                        width: 32px;
                        height: 32px;
                        object-fit: contain;
                    }
                    .header-actions {
                        display: flex;
                        gap: 8px;
                    }
                    .header-btn {
                        background: transparent;
                        border: none;
                        color: var(--text-secondary);
                        font-size: 12px;
                        cursor: pointer;
                        padding: 6px 10px;
                        border-radius: 6px;
                        transition: all 0.2s;
                    }
                    .header-btn:hover { background: var(--bg-panel); color: var(--text-primary); }

                    #chat-history {
                        flex: 1;
                        overflow-y: auto;
                        padding: 20px;
                        display: flex;
                        flex-direction: column;
                        gap: 24px;
                    }
                    #chat-history:empty {
                        overflow: hidden;
                    }

                    .welcome-container {
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        height: 100%;
                        text-align: center;
                        color: var(--text-secondary);
                        opacity: 0.8;
                    }
                    .welcome-text {
                        font-size: 15px;
                        line-height: 1.6;
                        max-width: 300px;
                    }
                    .welcome-text strong {
                        color: var(--text-primary);
                        font-weight: 600;
                    }

                    .user-message {
                        align-self: flex-end;
                        background: var(--bg-input);
                        padding: 12px 16px;
                        border-radius: 12px;
                        border-bottom-right-radius: 4px;
                        max-width: 85%;
                        font-size: 14px;
                        line-height: 1.5;
                        color: var(--text-primary);
                        border: 1px solid var(--border);
                    }
                    .agent-message {
                        align-self: flex-start;
                        max-width: 90%;
                        font-size: 14px;
                        line-height: 1.6;
                        color: var(--text-secondary);
                        padding-left: 4px;
                    }
                    .agent-message strong { color: var(--text-primary); font-weight: 600; }

                     .starter-chips {
                            display: flex;
                            flex-direction: column;
                            gap: 12px;
                            padding: 0 20px;
                            margin-bottom: 24px;
                            justify-content: center;
                            max-width: 400px;
                            margin-left: auto;
                            margin-right: auto;
                    }
                    .chip-btn {
                        background: var(--bg-panel);
                        border: 1px solid var(--border);
                        color: var(--text-primary);
                        padding: 12px 16px;
                        border-radius: 8px;
                        font-size: 13px;
                        cursor: pointer;
                        transition: all 0.2s;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        text-align: left;
                    }
                    .chip-btn:hover {
                            border-color: var(--text-secondary);
                            background: var(--bg-input);
                            transform: translateY(-1px);
                    }
                    .chip-btn i {
                        font-size: 16px;
                        color: var(--accent);
                        width: 20px;
                        display: inline-block;
                        margin-right: 12px;
                    }
                    .chip-btn::after {
                        content: '→';
                        opacity: 0.5;
                        font-family: monospace;
                    }

                    .input-container {
                        padding: 20px;
                        background: var(--bg-app);
                        position: relative;
                        z-index: 10;
                    }
                    .input-box {
                        background: var(--bg-panel);
                        border: 1px solid var(--border);
                        border-radius: 12px;
                        padding: 14px;
                        display: flex;
                        flex-direction: column;
                        gap: 10px;
                        transition: border-color 0.2s, box-shadow 0.2s;
                    }
                    .input-box:focus-within {
                        border-color: var(--border);
                        box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.05);
                    }
                    textarea {
                        background: transparent;
                        border: none;
                        color: var(--text-primary);
                        font-family: var(--font-family);
                        font-size: 14px;
                        resize: none;
                        outline: none;
                        min-height: 24px;
                        width: 100%;
                        line-height: 1.5;
                    }
                    textarea::placeholder { color: #52525b; }

                    .input-controls {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        padding-top: 4px;
                    }
                    .left-controls {
                        display: flex;
                        align-items: center;
                        gap: 8px;
                    }
                    .right-controls {
                        display: flex;
                        align-items: center;
                        gap: 10px;
                    }

                    .pill-btn {
                        background: transparent;
                        border: none;
                        color: var(--text-secondary);
                        font-size: 12px;
                        font-weight: 500;
                        padding: 6px 10px;
                        border-radius: 6px;
                        cursor: pointer;
                        display: flex;
                        align-items: center;
                        gap: 6px;
                        transition: all 0.15s;
                    }
                    .pill-btn:hover { background: var(--bg-input); color: var(--text-primary); }
                    
                    .icon-btn {
                        background: transparent;
                        border: none;
                        color: var(--text-secondary);
                        cursor: pointer;
                        transition: color 0.15s;
                        font-size: 16px;
                        padding: 4px;
                        display: flex; align-items: center; justify-content: center;
                    }
                    .icon-btn:hover { color: var(--text-primary); }

                    .send-btn {
                        background: var(--text-primary);
                        color: var(--bg-app);
                        border: none;
                        border-radius: 8px;
                        width: 32px;
                        height: 32px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        cursor: pointer;
                        transition: all 0.15s;
                    }
                    .send-btn:hover { opacity: 0.9; }
                    .send-btn svg { width: 16px; height: 16px; fill: currentColor; }

                    /* Modern Settings Overlay */
                    .settings-overlay {
                        position: absolute;
                        top: 50%;
                        left: 50%;
                        transform: translate(-50%, -50%);
                        width: 340px;
                        background: rgba(24, 24, 27, 0.95);
                        backdrop-filter: blur(12px);
                        -webkit-backdrop-filter: blur(12px);
                        border: 1px solid var(--border);
                        border-radius: 16px;
                        padding: 24px;
                        display: none;
                        box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.4), 0 10px 10px -5px rgba(0, 0, 0, 0.2);
                        z-index: 100;
                    }
                    .settings-overlay.open { display: block; }
                    
                    .settings-header {
                        font-size: 16px;
                        font-weight: 600;
                        margin-bottom: 24px;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        color: var(--text-primary);
                    }

                    .form-group { margin-bottom: 20px; }
                    .form-group label {
                        display: block;
                        font-size: 12px;
                        font-weight: 500;
                        color: var(--text-secondary);
                        margin-bottom: 8px;
                    }
                    .form-group select, .form-group input {
                        width: 100%;
                        background: var(--bg-input);
                        border: 1px solid var(--border);
                        color: var(--text-primary);
                        padding: 10px 12px;
                        border-radius: 8px;
                        font-size: 13px;
                        transition: border-color 0.2s, box-shadow 0.2s;
                        outline: none;
                    }
                    .form-group select:focus, .form-group input:focus {
                        border-color: var(--text-secondary);
                        box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.1);
                    }
                    .save-btn {
                        width: 100%;
                        background: var(--accent);
                        color: white;
                        border: none;
                        padding: 12px;
                        border-radius: 8px;
                        cursor: pointer;
                        font-size: 13px;
                        font-weight: 600;
                        margin-top: 8px;
                        transition: background 0.2s;
                    }
                    .save-btn:hover { background: var(--accent-hover); }

                    /* Context Pill Styles */
                    .context-area {
                        display: flex;
                        flex-wrap: wrap;
                        gap: 8px;
                        margin-bottom: 8px;
                    }
                    .context-pill {
                        background: var(--bg-app);
                        border: 1px dashed var(--border);
                        color: var(--text-secondary);
                        font-size: 11px;
                        padding: 4px 8px;
                        border-radius: 4px;
                        display: flex;
                        align-items: center;
                        gap: 6px;
                        max-width: 100%;
                    }
                    .context-pill span {
                        white-space: nowrap;
                        overflow: hidden;
                        text-overflow: ellipsis;
                        max-width: 200px;
                    }
                    .context-remove {
                        cursor: pointer;
                        opacity: 0.6;
                        font-size: 14px;
                        line-height: 1;
                    }
                    .context-remove:hover { opacity: 1; color: #ef4444; }

                  </style>
                </head>
                <body>
                  
                  <header>
                    <div class="header-title">
                       <img src="data:image/png;base64,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">
                       <span>LISA Agent</span>
                    </div>
                    <div class="header-actions">
                       <img src="data:image/png;base64,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">
                       <span>LISA</span>
                    </div>
                    <div class="header-actions">
                        <!-- Mimicking the 'Reject/Accept' style locally for effect, functional for 'Clear' -->
                        <button class="header-btn" onclick="document.getElementById('chat-history').innerHTML = ''">Clear</button>
                    </div>
                  </header>

                   <div id="chat-history">
                        <div class="welcome-container" id="welcome-screen">
                             <div class="welcome-text">
                                Hi! I'm ready to help.<br>
                                Ask me anything or press <strong>CMD+L</strong> to start.
                             </div>
                        </div>
                  </div>
                  
                  <div id="starter-area" class="starter-chips">
                       <button class="chip-btn" onclick="quickAction('Generate code from descriptions')">
                           <span>Generate code from descriptions</span>
                       </button>
                       <button class="chip-btn" onclick="quickAction('Generate documentation')">
                           <span>Generate documentation</span>
                       </button>
                       <button class="chip-btn" onclick="quickAction('Generate commit messages')">
                           <span>Generate commit messages</span>
                       </button>
                       <button class="chip-btn" onclick="quickAction('Ask about project code')">
                           <span>Ask about project code</span>
                           <i class="codicon codicon-search"></i>
                       </button>
                       <button class="chip-btn" onclick="quickAction('Explain runtime errors')">
                           <span>Explain runtime errors</span>
                       </button>
                  </div>


                    <div id="settings-panel" class="settings-overlay">
                        <div class="settings-header">
                            <span>Configuration</span>
                            <!-- Close button in header -->
                            <button class="icon-btn" onclick="document.getElementById('settings-panel').classList.remove('open')" style="font-size: 18px;">×</button>
                        </div>
                        <div class="form-group">
                            <label>Provider</label>
                            <select id="provider-select">
                                <option value="openai">OpenAI</option>
                                <option value="claude">Anthropic</option>
                                <option value="gemini">Gemini</option>
                                <option value="groq">Groq</option>
                            </select>
                        </div>
                        <div class="form-group">
                            <label>Model</label>
                            <select id="model-select"></select>
                        </div>
                        <div class="form-group">
                            <label>API Key</label>
                            <input type="password" id="api-key" placeholder="Enter API Key" />
                        </div>
                        <button class="save-btn" id="save-config-btn">Save Configuration</button>
                    </div>

                  <div class="input-container">
                    
                    <!-- Settings Panel -->


                    <div class="input-box">
                        <div id="context-area" class="context-area"></div>
                        <textarea id="instruction" placeholder="Ask anything or help with what our LISA can do."></textarea>
                        
                        <div class="input-controls">
                            <div class="left-controls">
                                 <button class="icon-btn" id="attach-btn" title="Add Current File Context">
                                     <svg viewBox="0 0 16 16" width="16" height="16" fill="currentColor"><path d="M4.5 3a2.5 2.5 0 0 1 5 0v9a1.5 1.5 0 0 1-3 0V5a.5.5 0 0 1 1 0v7a.5.5 0 0 0 1 0V3a1.5 1.5 0 1 0-3 0v9a2.5 2.5 0 0 0 5 0V5a.5.5 0 0 1 1 0v7a3.5 3.5 0 1 1-7 0V3z"/></svg>
                                 </button>
                                 <button class="icon-btn" id="attach-project-btn" title="Attach Project Structure (Requires Permission)" style="margin-left: 5px;">
                                    <svg viewBox="0 0 16 16" width="16" height="16" fill="currentColor"><path d="M14.5 3H7.71l-.85-.85A2.5 2.5 0 0 0 5.09 1.5H1.5A1.5 1.5 0 0 0 0 3v9a1.5 1.5 0 0 0 1.5 1.5h13A1.5 1.5 0 0 0 16 12V4.5A1.5 1.5 0 0 0 14.5 3zM1.5 2.5h3.59c.4 0 .78.16 1.06.44l.85.85H1.5V2.5zM14.5 12h-13V4.5h13V12z"/></svg>
                                 </button>
                                 <button class="pill-btn" id="model-btn">
                                     Gemini 3 Pro <span>⌄</span>
                                 </button>
                            </div>
                            <div class="right-controls">
                                <button class="icon-btn" id="mic-btn">🎤</button>
                                <button class="send-btn" id="run-btn">
                                    <svg viewBox="0 0 16 16"><path d="M1.72365 1.57467C1.19662 1.34026 0.655953 1.8817 0.891391 2.40871L3.08055 7.30906C3.12067 7.39886 3.12066 7.50207 3.08054 7.59187L0.891392 12.4922C0.655953 13.0192 1.19662 13.5607 1.72366 13.3262L14.7762 7.5251C15.32 7.28315 15.32 6.51778 14.7762 6.27583L1.72365 1.57467Z"/></svg>
                                </button>
                            </div>
                        </div>
                    </div>
                  </div>

                  <!-- Logic Scripts -->
                  <div id="debug-log" style="display:none;"></div>
                  <script>
                    // Core Elements
                    const chatHistory = document.getElementById('chat-history');
                    const instructionInput = document.getElementById('instruction');
                    const runBtn = document.getElementById('run-btn');
                    const micBtn = document.getElementById('mic-btn');
                    const attachBtn = document.getElementById('attach-btn');
                    const attachProjectBtn = document.getElementById('attach-project-btn');
                    const contextArea = document.getElementById('context-area');
                    
                    // Config Elements
                    const settingsPanel = document.getElementById('settings-panel');
                    const modelBtn = document.getElementById('model-btn');
                    const saveConfigBtn = document.getElementById('save-config-btn');
                    const providerSelect = document.getElementById('provider-select');
                    const modelSelect = document.getElementById('model-select');
                    const apiKeyInput = document.getElementById('api-key');

                    // State
                    let currentAgent = 'chat';
                    let attachedContext = null;
                    
                    // Models Data
                     const models = {
                        'openai': ['gpt-5-2025-08-07', 'gpt-4o', 'o3'],
                        'groq': ['grok-4', 'llama-3.3-70b-versatile', 'mistral-saba-24b'],
                        'gemini': ['gemini-3-flash', 'gemini-3-pro', 'gemini-2.5-pro'],
                        'claude': ['claude-opus-4-5-20251101', 'claude-sonnet-4-5-20250929', 'claude-haiku-4-5-20251001']
                    };

                    function updateModels() {
                        const p = providerSelect.value;
                        if (models[p]) {
                            modelSelect.innerHTML = models[p].map(m => `<option value="${'$'}{m}">${'$'}{m}</option>`).join('');
                        }
                        // Update UI Pill
                        const currentModel = modelSelect.value || 'Model';
                        // Simplify name for UI
                        let displayName = currentModel.split('-').map(s => s.charAt(0).toUpperCase() + s.slice(1)).join(' ');
                        displayName = displayName.replace('Gpt', 'GPT').replace('Claude', 'Claude').replace('Gemini', 'Gemini');
                        if (displayName.length > 15) displayName = displayName.substring(0, 12) + '...';
                        
                        document.querySelector('#model-btn').innerHTML = `${'$'}{displayName} <span>⌄</span>`;
                    }

                    providerSelect.addEventListener('change', updateModels);
                    modelSelect.addEventListener('change', updateModels);
                    
                    // Toggle Settings
                    modelBtn.onclick = () => settingsPanel.classList.toggle('open');
                    
                    // Save Config
                    saveConfigBtn.onclick = () => {
                        window.lisa.postMessage(JSON.stringify({
                            command: 'saveConfig',
                            provider: providerSelect.value,
                            model: modelSelect.value,
                            apiKey: apiKeyInput.value
                        }));
                        settingsPanel.classList.remove('open');
                        updateModels(); // Ensure label matches
                    };

                    attachBtn.onclick = () => {
                        window.lisa.postMessage({ command: 'getContext' });
                    };

                    attachProjectBtn.onclick = () => {
                        window.lisa.postMessage({ command: 'readProject' });
                    };

                    function renderContextPill() {
                        contextArea.innerHTML = '';
                        if (attachedContext) {
                            const pill = document.createElement('div');
                            pill.className = 'context-pill';
                            pill.innerHTML = `
                                <span>${'$'}{attachedContext.file}</span>
                                <span class="context-remove" onclick="removeContext()">×</span>
                            `;
                            contextArea.appendChild(pill);
                        }
                    }

                    window.removeContext = function() {
                        attachedContext = null;
                        renderContextPill();
                    };


                    // Auto-resize textarea
                    instructionInput.addEventListener('input', function() {
                        this.style.height = 'auto';
                        this.style.height = (this.scrollHeight) + 'px';
                    });

                    // Submit Logic
                    function submit() {
                        const text = instructionInput.value.trim();
                        if (!text) return;
                        
                        // Hide starter chips
                        const starterArea = document.getElementById('starter-area');
                        if (starterArea) starterArea.style.display = 'none';
                        
                        
                        // User Message UI
                        // Remove welcome screen if it exists
                        const welcome = document.getElementById('welcome-screen');
                        if (welcome) welcome.remove();

                        const userDiv = document.createElement('div');
                        userDiv.className = 'user-message';
                        userDiv.textContent = text;
                        chatHistory.appendChild(userDiv);
                        
                        instructionInput.value = '';
                        instructionInput.style.height = 'auto';
                        scrollToBottom();
                        
                        // Send to Backend
                         try {
                             window.lisa.postMessage(JSON.stringify({
                                 command: 'runAgent',
                                 agent: currentAgent,
                                 instruction: text,
                                 attachedContext: attachedContext
                             }));
                        } catch (e) {
                             console.error("Bridge Error", e);
                        }
                    }

                    window.quickAction = function(text) {
                        instructionInput.value = text;
                        submit();
                    };

                    runBtn.onclick = submit;
                    instructionInput.onkeydown = (e) => {
                        if(e.key === 'Enter' && !e.shiftKey) {
                            e.preventDefault();
                            submit();
                        }
                    };
                    
                    function scrollToBottom() {
                        chatHistory.scrollTop = chatHistory.scrollHeight;
                    }

                    // Message Listener for Agent Responses
                     window.addEventListener('message', event => {
                        const message = event.data; 
                        if (message && message.command === 'agentResponse') {
                            const result = message.data || {};
                            if (result.success) {
                                const responseDiv = document.createElement('div');
                                responseDiv.className = 'agent-message';
                                // Simple markdown-ish bolding for now
                                let content = result.data;
                                content = content.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
                                content = content.replace(/`(.*?)`/g, '<code style="background:#333;padding:2px 4px;border-radius:3px;">$1</code>');
                                responseDiv.innerHTML = `<strong>LISA</strong><br>${'$'}{content}`;
                                chatHistory.appendChild(responseDiv);
                            } else {
                                const errDiv = document.createElement('div');
                                errDiv.className = 'agent-message';
                                errDiv.style.color = '#ef4444';
                                errDiv.textContent = `Error: ${'$'}{result.error}`;
                                chatHistory.appendChild(errDiv);
                            }
                            scrollToBottom();
                        }
                        
                        if (message && message.command === 'setContext') {
                            if (message.file) {
                                attachedContext = {
                                    file: message.file,
                                    content: message.content,
                                    language: message.language
                                };
                                renderContextPill();
                                instructionInput.focus();
                            }
                        }
                     });
                     
                     // Helper for JCEF compatibility
                    window.receiveMessage = function(jsonStr) {
                         try {
                             const msg = JSON.parse(jsonStr);
                             const event = new MessageEvent('message', { data: msg });
                             window.dispatchEvent(event);
                         } catch (e) {}
                    };


                    // Speech Recognition Logic
                    // Speech Recognition Logic
                    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;

                    if (SpeechRecognition) {
                        try {
                            const recognition = new SpeechRecognition();
                            recognition.continuous = false;
                            recognition.interimResults = false;
                            
                            micBtn.onclick = () => {
                                if (micBtn.classList.contains('listening')) {
                                    try { recognition.stop(); } catch(e) {}
                                    micBtn.classList.remove('listening');
                                    micBtn.style.color = '';
                                } else {
                                    // Visual feedback immediately
                                    micBtn.style.opacity = '0.5';
                                    try {
                                        recognition.start();
                                    } catch (err) {
                                        // Ignore 'already started' errors, just ensure UI reflects it
                                        if (err.message.includes('already started')) {
                                             micBtn.classList.add('listening');
                                             micBtn.style.color = '#ef4444';
                                             micBtn.style.opacity = '1';
                                        } else {
                                            alert('Failed to start speech recognition: ' + err.message);
                                            micBtn.style.opacity = '1';
                                        }
                                    }
                                }
                            };
                            
                            recognition.onstart = () => {
                                micBtn.classList.add('listening');
                                micBtn.style.color = '#ef4444'; // Red when listening
                                micBtn.style.opacity = '1';
                            };
                            
                            recognition.onend = () => {
                                micBtn.classList.remove('listening');
                                micBtn.style.color = ''; 
                                micBtn.style.opacity = '1';
                            };
                            
                            recognition.onresult = (event) => {
                                const transcript = event.results[0][0].transcript;
                                instructionInput.value += (instructionInput.value ? ' ' : '') + transcript;
                                instructionInput.style.height = 'auto';
                                instructionInput.style.height = (instructionInput.scrollHeight) + 'px';
                                instructionInput.focus();
                            };
                            
                            recognition.onerror = (event) => {
                                console.error('Speech error', event);
                                micBtn.style.color = '';
                                micBtn.style.opacity = '1';
                                if (event.error === 'not-allowed') {
                                    alert('Microphone access denied. Please check your OS settings for IntelliJ.');
                                } else if (event.error === 'no-speech') {
                                    // Quiet failure, just reset
                                    micBtn.classList.remove('listening');
                                } else {
                                    // alert('Speech error: ' + event.error);
                                }
                            };
                        } catch (e) {
                             console.error("Speech Init Failed", e);
                             micBtn.onclick = () => alert("Speech Recognition Initialization Failed: " + e.message);
                        }
                    } else {
                        micBtn.style.opacity = '0.5';
                        micBtn.onclick = () => {
                            alert('Speech recognition is not supported in this IDE version (JCEF/Chromium limitation).');
                        };
                    }

                    // Init
                    updateModels();
                    
                    // Global Keyboard Shortcuts
                    window.addEventListener('keydown', (e) => {
                        // CMD+L (Mac) or CTRL+L (Windows/Linux) to focus input
                        if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'l') {
                            e.preventDefault();
                            instructionInput.focus();
                            // Optional: select all text if you want to overwrite
                            // instructionInput.select(); 
                        }
                    });
                  </script>
                </body>
                </html>
            """.trimIndent()
            
            browser.loadHTML(htmlContent)
        }
        
        private fun debugLog(msg: String) {
             val escapedMsg = escapeJsonString(msg)
             // Manually construct JSON to avoid nested quote issues
             val json = "{\"command\": \"debug\", \"message\": $escapedMsg}"
             val js = "if(window.receiveMessage) window.receiveMessage('$json');"
             // Use invokeLater to run on EDT
             ApplicationManager.getApplication().invokeLater {
                 browser.cefBrowser.executeJavaScript(js, null, 0)
             }
        }

        private fun handleMessage(jsonStr: String) {
            // Run on background thread to avoid blocking UI, but use Read Action for PSI access
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val jsonObject = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject
                    val command = jsonObject.get("command")?.asString ?: ""
                    
                    if (command == "getContext") {
                         // Must run read action on UI thread or valid context
                         ApplicationManager.getApplication().invokeLater {
                             try {
                                 val editor = FileEditorManager.getInstance(project).selectedTextEditor
                                 if (editor != null) {
                                     val file = editor.virtualFile
                                     if (file != null) {
                                         val fileName = file.name
                                         val content = editor.document.text
                                         // rudimentary language detection
                                         val ext = file.extension ?: ""
                                         val language = when(ext) {
                                             "kt" -> "kotlin"
                                             "ts", "js" -> "typescript"
                                             "java" -> "java"
                                             else -> ext
                                         }
                                         
                                         // Send back to JS
                                         val escapedName = escapeJsonString(fileName)
                                         // Content escaping is tricky in manual JSON, handle carefully
                                         val escapedContent = escapeJsonString(content)
                                         val escapedLang = escapeJsonString(language)
                                         
                                         val json = """{"command": "setContext", "file": $escapedName, "content": $escapedContent, "language": $escapedLang}"""
                                         // Execute JS
                                          val js = "if(window.receiveMessage) window.receiveMessage('$json');"
                                          browser.cefBrowser.executeJavaScript(js, null, 0)
                                     }
                                 } else {
                                     browser.cefBrowser.executeJavaScript("alert('No active editor found. Please open a file to attach context.');", null, 0)
                                 }
                             } catch (e: Exception) {
                                 debugLog("Error getting context: ${e.message}")
                             }
                         }
                         return@executeOnPooledThread
                    }
                    
                    if (command == "readProject") {
                        readProject()
                        return@executeOnPooledThread
                    }

                    if (command == "saveConfig") {
                        debugLog("Kotlin: Received saveConfig")
                        val provider = jsonObject.get("provider")?.asString ?: ""
                        val model = jsonObject.get("model")?.asString ?: ""
                        val apiKey = jsonObject.get("apiKey")?.asString ?: ""
                        
                        val lspManager = LspServerManager.getInstance(project)
                        val servers = lspManager.getServersForProvider(LisaLspServerSupportProvider::class.java)
                        
                        if (servers.isEmpty()) {
                            debugLog("Kotlin: No LSP server for config save")
                            dispatchResponse(false, null, "LSP server not found")
                            return@executeOnPooledThread
                        }
                        
                        val server = servers.first()
                        project.service<MyProjectService>().scope.launch {
                            try {
                                val configParams = mutableMapOf<String, String>()
                                if (provider.isNotEmpty()) configParams["provider"] = provider
                                if (model.isNotEmpty()) configParams["model"] = model
                                if (apiKey.isNotEmpty()) configParams["apiKey"] = apiKey
                                
                                debugLog("Kotlin: Sending config to LSP: $configParams")
                                
                                val response = server.sendRequest<Any> {
                                    val future = (it as Endpoint).request("lisa/updateConfig", configParams) as CompletableFuture<Any>
                                    future.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                                }
                                
                                debugLog("Kotlin: Config response: ${response.toString()}")
                                ApplicationManager.getApplication().invokeLater {
                                    dispatchResponse(true, "Configuration saved successfully!", null)
                                }
                            } catch (e: Exception) {
                                debugLog("Kotlin: Config save failed: ${e.message}")
                                ApplicationManager.getApplication().invokeLater {
                                    dispatchResponse(false, null, "Failed to save configuration: ${e.message}")
                                }
                            }
                        }
                        return@executeOnPooledThread
                    }

                    if (command == "runAgent") {
                        val agent = jsonObject.get("agent")?.asString ?: ""
                        val instruction = jsonObject.get("instruction")?.asString ?: ""
                        debugLog("Kotlin: Received runAgent for $agent")
                        
                        val lspManager = LspServerManager.getInstance(project)
                        val servers = lspManager.getServersForProvider(LisaLspServerSupportProvider::class.java)

                        if (servers.isEmpty()) {
                            debugLog("Kotlin: No LSP Servers found!")
                            dispatchResponse(false, null, "LISA Server not found. Please open a file in the editor.")
                            return@executeOnPooledThread
                        }
                        val server = servers.first()

                        // READ ACTION REQUIRED: Accesing Editor/PSI
                        val contextData = mutableMapOf<String, String>()
                        
                        com.intellij.openapi.application.ApplicationManager.getApplication().runReadAction {
                            val editor = FileEditorManager.getInstance(project).selectedTextEditor
                            if (editor != null) {
                                contextData["selection"] = editor.selectionModel.selectedText ?: ""
                                contextData["fileContent"] = editor.document.text
                                contextData["uri"] = editor.virtualFile?.url ?: ""
                                contextData["languageId"] = editor.virtualFile?.fileType?.name?.lowercase() ?: ""
                                // debugLog cannot be called inside read action easily if it touches UI, so we log after
                            }
                        }
                        
                        // Handle Attached Context (Override/Augment)
                        if (jsonObject.has("attachedContext") && !jsonObject.get("attachedContext").isJsonNull) {
                             val ac = jsonObject.getAsJsonObject("attachedContext")
                             val acContent = ac.get("content")?.asString
                             val acLang = ac.get("language")?.asString
                             
                             if (!acContent.isNullOrEmpty()) {
                                 contextData["fileContent"] = acContent
                                 debugLog("Kotlin: Using attached context content.")
                             }
                             if (!acLang.isNullOrEmpty()) {
                                 contextData["languageId"] = acLang
                             }
                        }
                        
                        debugLog("Kotlin: Context captured.")

                        var prompt = instruction
                        if (agent == "generateTests") prompt = "Generate unit tests for this code"
                        if (agent == "addJsDoc") prompt = "Add JSDoc documentation"
                        if (agent == "refactor") prompt = "Refactor this code: $instruction"

                        project.service<MyProjectService>().scope.launch {
                             try {
                                debugLog("Kotlin: Sending workspace/executeCommand...")
                                
                                // Use standard LSP executeCommand
                                val command = org.eclipse.lsp4j.ExecuteCommandParams()
                                command.command = "lisa.chat"
                                command.arguments = listOf(
                                    com.google.gson.JsonPrimitive(prompt),
                                    com.google.gson.JsonObject().apply {
                                        contextData.forEach { (key, value) ->
                                            addProperty(key, value)
                                        }
                                    }
                                )
                                
                                val rawResponse = server.sendRequest<Any> {
                                    val endpoint = it as Endpoint
                                    @Suppress("UNCHECKED_CAST")
                                    val future = endpoint.request("workspace/executeCommand", command) as CompletableFuture<Any>
                                    future.orTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                }
                                
                                debugLog("Kotlin: LSP Response Type: ${rawResponse?.javaClass?.name}")
                                debugLog("Kotlin: LSP Response: ${rawResponse.toString()}")
                                
                                // Check if response is null
                                if (rawResponse == null) {
                                    debugLog("Kotlin: NULL response from LSP server!")
                                    ApplicationManager.getApplication().invokeLater {
                                        dispatchResponse(false, null, "LSP server returned null. Please check if the API key is configured.")
                                    }
                                    return@launch
                                }
                                
                                // Handle response - it should be a string now
                                val actualResult = rawResponse.toString()
                                debugLog("Kotlin: Extracted Result: $actualResult")
                                
                                // Check if it's an error
                                if (actualResult.startsWith("ERROR:")) {
                                    ApplicationManager.getApplication().invokeLater {
                                        dispatchResponse(false, null, actualResult.substring(7))
                                    }
                                    return@launch
                                }
                                
                                ApplicationManager.getApplication().invokeLater {
                                     dispatchResponse(true, actualResult, null)
                                }
                             } catch (e: Exception) {
                                debugLog("Kotlin: LSP Request FAILED: ${e.message}")
                                val errorMsg = "LSP Error: ${e.message}"
                                ApplicationManager.getApplication().invokeLater {
                                    dispatchResponse(false, null, errorMsg)
                                }
                             }
                        }
                    }
                } catch (e: Exception) {
                     val errorStr = "Kotlin Crash: ${e.message}"
                     ApplicationManager.getApplication().invokeLater {
                         browser.cefBrowser.executeJavaScript("alert('${escapeJsonString(errorStr)}');", null, 0)
                     }
                }
            }
        }

        private fun dispatchResponse(success: Boolean, data: Any?, error: String?) {
            val successStr = if (success) "true" else "false"
            val errorStr = if (error != null) escapeJsonString(error) else "null"
            val dataStr = if (data != null) escapeJsonString(data.toString()) else "null"

            val jsonMsg = """
                {
                    "command": "agentResponse",
                    "data": {
                        "success": $successStr,
                        "data": $dataStr,
                        "error": $errorStr
                    }
                }
            """.trimIndent()
            
            // Encode the JSON string itself into a JS string literal
            val jsArg = escapeJsonString(jsonMsg)
            
            // IMPORTANT FIX: Pass "about:blank" or null as the URL
            // Passing browser.cefBrowser.url when loaded via loadHTML often fails security checks
            val runJs = """
                if (window.receiveMessage) {
                    window.receiveMessage($jsArg);
                } else {
                    window.postMessage(JSON.parse($jsArg), '*');
                }
            """.trimIndent()

            // Try executeJavaScript with null URL to bypass strict origin checks for data/html content
            browser.cefBrowser.executeJavaScript(runJs, null, 0)
        }

        private fun escapeJsonString(s: String): String {
            val sb = StringBuilder()
            sb.append("\"")
            for (c in s) {
                when (c) {
                    '\"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> {
                        if (c.code in 0..31) {
                            val ss = Integer.toHexString(c.code)
                            sb.append("\\u")
                            for (k in 0 until 4 - ss.length) {
                                sb.append('0')
                            }
                            sb.append(ss.uppercase())
                        } else {
                            sb.append(c)
                        }
                    }
                }
            }
            sb.append("\"")
            return sb.toString()
        }

        private fun extractJsonValue(json: String, key: String): String {
            val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            return regex.find(json)?.groupValues?.get(1) ?: ""
        }
    }
}
