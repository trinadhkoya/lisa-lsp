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

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val agentPanel = AgentPanel(project)
        val content = ContentFactory.getInstance().createContent(agentPanel.component, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private class AgentPanel(private val project: Project) {
        val component = JPanel(BorderLayout())
        private val browser = JBCefBrowser()
        private var jsQuery: JBCefJSQuery? = null

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
                    }

                    /* Header (Diff/Context View Style) */
                    header {
                        background: var(--bg-app);
                        padding: 12px 16px;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        border-bottom: 1px solid var(--border);
                        flex-shrink: 0;
                    }
                    .header-title {
                        font-weight: 500;
                        font-size: 13px;
                        display: flex;
                        align-items: center;
                        gap: 8px;
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
                        padding: 4px 8px;
                        border-radius: 4px;
                    }
                    .header-btn:hover { background: var(--bg-panel); color: var(--text-primary); }
                    .header-btn.primary { background: var(--accent); color: white; }
                    .header-btn.primary:hover { background: var(--accent-hover); }

                    /* Chat Area */
                    #chat-history {
                        flex: 1;
                        overflow-y: auto;
                        padding: 16px;
                        display: flex;
                        flex-direction: column;
                        gap: 20px;
                    }

                    /* Messages */
                    .user-message {
                        align-self: flex-end;
                        background: var(--bg-input);
                        padding: 10px 14px;
                        border-radius: 12px;
                        max-width: 85%;
                        font-size: 13px;
                        line-height: 1.5;
                        color: var(--text-primary);
                    }
                    .agent-message {
                        align-self: flex-start;
                        max-width: 90%;
                        font-size: 13px;
                        line-height: 1.6;
                        color: var(--text-secondary);
                    }
                    .agent-message strong { color: var(--text-primary); font-weight: 600; }
                    
                    /* Input Area Container */
                    .input-container {
                        padding: 16px;
                        background: var(--bg-app);
                        position: relative;
                        z-index: 10;
                    }
                    
                    /* The Main Input Box */
                    .input-box {
                        background: var(--bg-panel);
                        border: 1px solid var(--border);
                        border-radius: 12px;
                        padding: 12px;
                        display: flex;
                        flex-direction: column;
                        gap: 8px;
                        transition: border-color 0.2s;
                    }
                    .input-box:focus-within {
                        border-color: #52525b;
                    }

                    textarea {
                        background: transparent;
                        border: none;
                        color: var(--text-primary);
                        font-family: var(--font-family);
                        font-size: 13px;
                        resize: none;
                        outline: none;
                        min-height: 24px;
                        width: 100%;
                        line-height: 1.5;
                    }
                    textarea::placeholder { color: #52525b; }

                    /* Input Footer Controls */
                    .input-controls {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        padding-top: 4px;
                    }
                    
                    .left-controls {
                        display: flex;
                        align-items: center;
                        gap: 6px;
                    }
                    
                    .right-controls {
                        display: flex;
                        align-items: center;
                        gap: 8px;
                    }

                    /* Buttons & Pills */
                    .icon-btn {
                        background: transparent;
                        border: none;
                        color: var(--text-secondary);
                        cursor: pointer;
                        padding: 4px;
                        border-radius: 4px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        transition: color 0.15s;
                    }
                    .icon-btn:hover { color: var(--text-primary); background: var(--bg-input); }
                    
                    .pill-btn {
                        background: transparent;
                        border: none;
                        color: var(--text-secondary);
                        font-size: 11px;
                        font-weight: 500;
                        padding: 4px 8px;
                        border-radius: 4px;
                        cursor: pointer;
                        display: flex;
                        align-items: center;
                        gap: 4px;
                        transition: all 0.15s;
                    }
                    .pill-btn:hover { background: var(--bg-input); color: var(--text-primary); }
                    .pill-btn span { font-size: 9px; opacity: 0.7; }

                    .send-btn {
                        background: var(--bg-input);
                        color: var(--text-primary);
                        border: none;
                        border-radius: 8px;
                        width: 28px;
                        height: 28px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        cursor: pointer;
                        transition: all 0.15s;
                    }
                    .send-btn:hover { background: var(--text-secondary); color: var(--bg-app); }
                    .send-btn svg { width: 14px; height: 14px; fill: currentColor; }

                    /* Config Overlay */
                    .settings-overlay {
                        position: absolute;
                        bottom: 80px;
                        left: 16px;
                        width: 280px;
                        background: var(--bg-panel);
                        border: 1px solid var(--border);
                        border-radius: 8px;
                        padding: 12px;
                        display: none;
                        box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.5);
                    }
                    .settings-overlay.open { display: block; }
                    
                    .form-group { margin-bottom: 12px; }
                    .form-group label {
                        display: block;
                        font-size: 11px;
                        color: var(--text-secondary);
                        margin-bottom: 4px;
                    }
                    .form-group select, .form-group input {
                        width: 100%;
                        background: var(--bg-app);
                        border: 1px solid var(--border);
                        color: var(--text-primary);
                        padding: 6px;
                        border-radius: 4px;
                        font-size: 12px;
                    }
                    .save-btn {
                        width: 100%;
                        background: var(--accent);
                        color: white;
                        border: none;
                        padding: 8px;
                        border-radius: 4px;
                        cursor: pointer;
                        font-size: 12px;
                    }

                  </style>
                </head>
                <body>
                  
                  <header>
                    <div class="header-title">
                       <img src="data:image/png;base64,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" style="width:16px;height:16px;vertical-align:middle;margin-right:4px;">
                       <span>LISA Agent</span>
                    </div>
                    <div class="header-actions">
                        <!-- Mimicking the 'Reject/Accept' style locally for effect, functional for 'Clear' -->
                        <button class="header-btn" onclick="document.getElementById('chat-history').innerHTML = ''">Clear</button>
                    </div>
                  </header>

                  <div id="chat-history">
                        <div class="agent-message">
                            <strong>LISA</strong><br>
                            Hi! I'm ready to help. Ask me anything or press <strong>CMD+L</strong> to start.
                        </div>
                  </div>

                  <div class="input-container">
                    
                    <!-- Settings Panel -->
                    <div id="settings-panel" class="settings-overlay">
                        <div class="form-group">
                            <label>Provider</label>
                            <select id="provider-select">
                                <option value="openai">OpenAI</option>
                                <option value="anthropics">Anthropic</option>
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
                            <input type="password" id="api-key" placeholder="API Key" />
                        </div>
                        <button class="save-btn" id="save-config-btn">Save</button>
                    </div>

                    <div class="input-box">
                        <textarea id="instruction" placeholder="Ask anything (⌘L), @ to mention, / for workflows"></textarea>
                        
                        <div class="input-controls">
                            <div class="left-controls">
                                <button class="icon-btn" title="Add Context">+</button>
                                <button class="pill-btn" id="mode-btn">
                                    Fast <span>⌄</span>
                                </button>
                                <button class="pill-btn" id="model-btn">
                                    Gemini 3 Pro <span>⌄</span>
                                </button>
                            </div>
                            <div class="right-controls">
                                <button class="icon-btn">🎤</button>
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
                    
                    // Config Elements
                    const settingsPanel = document.getElementById('settings-panel');
                    const modelBtn = document.getElementById('model-btn');
                    const saveConfigBtn = document.getElementById('save-config-btn');
                    const providerSelect = document.getElementById('provider-select');
                    const modelSelect = document.getElementById('model-select');
                    const apiKeyInput = document.getElementById('api-key');

                    // State
                    let currentAgent = 'chat';
                    
                    // Models Data
                     const models = {
                        'openai': ['gpt-4-turbo', 'gpt-4o', 'gpt-3.5-turbo'],
                        'anthropics': ['claude-3-opus-20240229', 'claude-3-sonnet-20240229', 'claude-3-haiku-20240307'],
                        'gemini': ['gemini-1.5-pro', 'gemini-1.5-flash', 'gemini-pro'],
                        'groq': ['llama3-70b-8192', 'mixtral-8x7b-32768', 'gemma-7b-it']
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

                    // Auto-resize textarea
                    instructionInput.addEventListener('input', function() {
                        this.style.height = 'auto';
                        this.style.height = (this.scrollHeight) + 'px';
                    });

                    // Submit Logic
                    function submit() {
                        const text = instructionInput.value.trim();
                        if (!text) return;
                        
                        // User Message UI
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
                                 instruction: text
                             }));
                        } catch (e) {
                             console.error("Bridge Error", e);
                        }
                    }

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
                     });
                     
                     // Helper for JCEF compatibility
                    window.receiveMessage = function(jsonStr) {
                         try {
                             const msg = JSON.parse(jsonStr);
                             const event = new MessageEvent('message', { data: msg });
                             window.dispatchEvent(event);
                         } catch (e) {}
                    };

                    // Init
                    updateModels();
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
                    val command = extractJsonValue(jsonStr, "command")
                    val agent = extractJsonValue(jsonStr, "agent")
                    val instruction = extractJsonValue(jsonStr, "instruction")
                    
                    if (command == "saveConfig") {
                        debugLog("Kotlin: Received saveConfig")
                        val provider = extractJsonValue(jsonStr, "provider")
                        val model = extractJsonValue(jsonStr, "model")
                        val apiKey = extractJsonValue(jsonStr, "apiKey")
                        
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
                                
                                debugLog("Kotlin: Config response: $response")
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
