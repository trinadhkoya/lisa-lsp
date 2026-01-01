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
            // Ported HTML from VS Code Extension
            val htmlContent = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <style>
                    :root {
                        --bg-color: #2b2b2b;
                        --fg-color: #a9b7c6;
                        --border-color: #4e5254;
                        --item-hover: #3c3f41;
                        --input-bg: #3c3f41;
                        --input-fg: #bababa;
                        --primary: #365880;
                        --secondary: #808080;
                        --success: #629755;
                        --error: #cc666e;
                        --font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                    }

                    body {
                        background-color: var(--bg-color);
                        color: var(--fg-color);
                        font-family: var(--font-family);
                        margin: 0;
                        padding: 0;
                        height: 100vh;
                        display: flex;
                        flex-direction: column;
                        overflow: hidden;
                    }

                    /* Header */
                    header {
                        padding: 12px 16px;
                        border-bottom: 1px solid var(--border-color);
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        background-color: var(--bg-color);
                        flex-shrink: 0;
                    }
                    header h2 {
                        margin: 0;
                        font-size: 14px;
                        font-weight: 600;
                        display: flex;
                        align-items: center;
                        gap: 8px;
                    }
                    .header-controls {
                        display: flex;
                        gap: 8px;
                    }
                    .icon-btn {
                        background: none;
                        border: none;
                        color: var(--fg-color);
                        cursor: pointer;
                        padding: 4px;
                        border-radius: 4px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 16px;
                    }
                    .icon-btn:hover {
                        background-color: var(--item-hover);
                    }

                    /* Chat Area */
                    #chat-history {
                        flex: 1;
                        overflow-y: auto;
                        padding: 20px;
                        display: flex;
                        flex-direction: column;
                        gap: 20px;
                    }

                    /* Message Blocks */
                    .message-block {
                        display: flex;
                        flex-direction: column;
                        gap: 8px;
                        animation: fadeIn 0.3s ease;
                    }
                    @keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }

                    .user-request {
                        border-left: 2px solid var(--primary);
                        padding-left: 12px;
                        margin-bottom: 10px;
                    }
                    .user-request .label {
                        font-size: 11px;
                        color: var(--secondary);
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                        margin-bottom: 4px;
                    }
                    .user-request .content {
                        font-size: 14px;
                        line-height: 1.4;
                    }

                    /* Step Items (Like AntiGravity) */
                    .step-item {
                        display: flex;
                        align-items: center;
                        gap: 10px;
                        padding: 8px 12px;
                        border: 1px solid var(--border-color);
                        background-color: #323232;
                        border-radius: 6px;
                        font-size: 13px;
                    }
                    .step-icon {
                        font-size: 16px;
                        width: 20px;
                        text-align: center;
                    }
                    .step-content {
                        flex: 1;
                        display: flex;
                        flex-direction: column;
                    }
                    .step-title {
                        font-weight: 500;
                    }
                    .step-detail {
                        font-size: 11px;
                        color: var(--secondary);
                        margin-top: 2px;
                    }

                    /* Input Area (Bottom) */
                    .input-container {
                        padding: 16px;
                        border-top: 1px solid var(--border-color);
                        background-color: var(--bg-color);
                        flex-shrink: 0;
                    }
                    .input-wrapper {
                        position: relative;
                        background-color: var(--input-bg);
                        border: 1px solid var(--border-color);
                        border-radius: 8px;
                        padding: 2px;
                        transition: border-color 0.2s;
                    }
                    textarea {
                        width: 100%;
                        border: none;
                        background: none;
                        color: var(--input-fg);
                        padding: 10px;
                        font-family: inherit;
                        font-size: 13px;
                        resize: none;
                        outline: none;
                        min-height: 40px;
                        box-sizing: border-box;
                        display: block;
                    }
                    .input-actions {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        padding: 4px 8px;
                        border-top: 1px solid var(--border-color);
                    }
                    
                    /* Agent Selector (Pill style in input) */
                    .agent-pills {
                        display: flex;
                        gap: 6px;
                    }
                    .pill {
                        font-size: 11px;
                        padding: 3px 8px;
                        border-radius: 12px;
                        background-color: #404040;
                        color: #ccc;
                        cursor: pointer;
                        opacity: 0.6;
                        transition: opacity 0.2s;
                        border: 1px solid transparent;
                    }
                    .pill:hover, .pill.active {
                        opacity: 1;
                        border-color: var(--fg-color);
                    }

                    button.send-btn {
                        background-color: var(--primary);
                        color: white;
                        border: none;
                        border-radius: 4px;
                        width: 32px;
                        height: 32px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        cursor: pointer;
                        transition: background-color 0.2s;
                    }
                    button.send-btn:hover { 
                        opacity: 0.9;
                    }
                    button.send-btn svg {
                        width: 16px; 
                        height: 16px;
                        fill: white;
                    }

                    /* Settings Overlay */
                    .settings-overlay {
                        position: absolute;
                        top: 50px;
                        right: 16px;
                        width: 280px;
                        background-color: #3c3f41;
                        border: 1px solid var(--border-color);
                        border-radius: 8px;
                        box-shadow: 0 4px 12px rgba(0,0,0,0.5);
                        padding: 16px;
                        z-index: 100;
                        display: none;
                    }
                    .settings-overlay.open { display: block; }
                    .form-group { margin-bottom: 12px; }
                    .form-group label { display: block; font-size: 11px; font-weight: 600; margin-bottom: 4px; color: var(--secondary); }
                    .form-group select, .form-group input { 
                        width: 100%; padding: 6px; border-radius: 4px; 
                        border: 1px solid var(--border-color); background: var(--input-bg); color: var(--input-fg);
                    }

                    /* Agent Response */
                    .agent-response {
                        border-left: 2px solid var(--success);
                        padding-left: 12px;
                        margin-bottom: 10px;
                        margin-top: 10px;
                        animation: fadeIn 0.3s ease;
                    }
                    .agent-response .label {
                        font-size: 11px;
                        color: var(--success);
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                        margin-bottom: 4px;
                    }
                    .agent-response .content {
                        font-size: 14px;
                        line-height: 1.5;
                        white-space: pre-wrap;
                    }
                  </style>
                </head>
                <body>
                  
                  <header>
                    <h2>🤖 LISA Agent</h2>
                    <div class="header-controls">
                        <button class="icon-btn" id="toggle-settings" title="Configuration">⚙️</button>
                    </div>
                  </header>

                  <div id="settings-panel" class="settings-overlay">
                        <div style="display:flex; justify-content:space-between; margin-bottom:10px;">
                            <strong>Configuration</strong>
                            <span id="close-settings" style="cursor:pointer;">✖️</span>
                        </div>
                        <div class="form-group">
                            <label>Provider</label>
                            <select id="provider-select">
                                <option value="openai">OpenAI</option>
                                <option value="anthropics">Anthropic (Claude)</option>
                                <option value="gemini">Google Gemini</option>
                                <option value="groq">Groq</option>
                            </select>
                        </div>
                        <div class="form-group">
                            <label>Model</label>
                            <select id="model-select"></select>
                        </div>
                        <div class="form-group">
                            <label>API Key</label>
                            <input type="password" id="api-key" placeholder="Saved Securely" />
                        </div>
                        <button id="save-config-btn" style="width:100%; padding:6px; cursor:pointer; background:var(--primary); color:white; border:none; border-radius:4px;">Save Config</button>
                  </div>

                  <!-- Chat Feed -->
                  <div id="chat-history">
                      <div class="step-item">
                          <div class="step-icon">👋</div>
                          <div class="step-content">
                              <div class="step-title">Welcome to LISA</div>
                              <div class="step-detail">I'm ready to help you code. Select an agent capability below.</div>
                          </div>
                      </div>
                  </div>

                  <!-- Input Area -->
                  <div class="input-container">
                    <div class="input-wrapper">
                        <textarea id="instruction" rows="2" placeholder="Ask me anything or describe a task..."></textarea>
                        <div class="input-actions">
                            <div class="agent-pills">
                                <div class="pill active" data-value="chat">Chat</div>
                                <div class="pill" data-value="generateTests">Test Gen</div>
                                <div class="pill" data-value="addJsDoc">Docs</div>
                                <div class="pill" data-value="refactor">Refactor</div>
                            </div>
                            <button class="send-btn" id="run-btn" title="Send Message">
                                <svg viewBox="0 0 16 16" xmlns="http://www.w3.org/2000/svg"><path d="M1.72365 1.57467C1.19662 1.34026 0.655953 1.8817 0.891391 2.40871L3.08055 7.30906C3.12067 7.39886 3.12066 7.50207 3.08054 7.59187L0.891392 12.4922C0.655953 13.0192 1.19662 13.5607 1.72366 13.3262L14.7762 7.5251C15.32 7.28315 15.32 6.51778 14.7762 6.27583L1.72365 1.57467Z"/></svg>
                            </button>
                        </div>
                    </div>
                  </div>
                  
                  <div id="debug-log" style="font-size:10px; color:#666; padding:10px; border-top:1px solid #444; max-height:100px; overflow-y:auto; display:block;">
                    <div>Debug Log (v1.0.20):</div>
                  </div>

                  <script>
                    const debugLog = document.getElementById('debug-log');
                    function log(msg) {
                        const d = document.createElement('div');
                        d.textContent = "> " + msg;
                        debugLog.appendChild(d);
                        debugLog.scrollTop = debugLog.scrollHeight;
                    }
                    
                    log("UI Loaded. Waiting for Bridge...");

                    const chatHistory = document.getElementById('chat-history');
                    const instructionInput = document.getElementById('instruction');
                    const runBtn = document.getElementById('run-btn');
                    const pills = document.querySelectorAll('.pill');
                    
                    const settingsPanel = document.getElementById('settings-panel');
                    const toggleSettings = document.getElementById('toggle-settings');
                    const closeSettings = document.getElementById('close-settings');
                    const saveConfig = document.getElementById('save-config-btn');
                    const providerSelect = document.getElementById('provider-select');
                    const modelSelect = document.getElementById('model-select');
                    const apiKeyInput = document.getElementById('api-key');

                    let currentAgent = 'chat';
                    let lastLoadingId = '';

                    const models = {
                        'openai': ['gpt-4-turbo', 'gpt-4o', 'gpt-3.5-turbo'],
                        'anthropics': ['claude-3-opus-20240229', 'claude-3-sonnet-20240229', 'claude-3-haiku-20240307'],
                        'gemini': ['gemini-1.5-pro', 'gemini-1.5-flash', 'gemini-pro'],
                        'groq': ['llama3-70b-8192', 'mixtral-8x7b-32768', 'gemma-7b-it']
                    };

                    function updateModels() {
                        const p = providerSelect ? providerSelect.value : 'openai';
                        if (modelSelect && models[p]) {
                            modelSelect.innerHTML = (models[p] || []).map(m => `<option value="${'$'}{m}">${'$'}{m}</option>`).join('');
                        }
                    }
                    if (providerSelect) {
                        providerSelect.addEventListener('change', updateModels);
                        updateModels();
                    }

                    toggleSettings.onclick = () => settingsPanel.classList.toggle('open');
                    closeSettings.onclick = () => settingsPanel.classList.remove('open');

                    saveConfig.onclick = () => {
                        window.lisa.postMessage(JSON.stringify({
                            command: 'saveConfig',
                            provider: providerSelect.value,
                            model: modelSelect.value,
                            apiKey: apiKeyInput.value
                        }));
                        settingsPanel.classList.remove('open');
                        addStep('Config Saved', `Provider: ${'$'}{providerSelect.value}`, 'check');
                    };

                    pills.forEach(p => {
                        p.onclick = () => {
                            pills.forEach(all => all.classList.remove('active'));
                            p.classList.add('active');
                            currentAgent = p.dataset.value;
                            const map = {
                                'chat': 'Ask me anything...',
                                'generateTests': 'What should I test? (e.g. edge cases)',
                                'addJsDoc': 'Which functions need docs?',
                                'refactor': 'How should I refactor this?'
                            };
                            instructionInput.placeholder = map[currentAgent] || 'Enter instructions...';
                        };
                    });



                    runBtn.onclick = submit;
                    instructionInput.onkeydown = (e) => {
                        if(e.key === 'Enter' && !e.shiftKey) {
                            e.preventDefault();
                            submit();
                        }
                    };

                    function submit() {
                        const text = instructionInput.value.trim();
                        if(!text) return;
                        
                        if (!window.lisa) {
                            log("FATAL: window.lisa missing!");
                            alert("FATAL ERROR: Internal Bridge (window.lisa) is missing.");
                            return;
                        }
                        
                        log("Sending request: " + text.substring(0, 10));

                        const userDiv = document.createElement('div');
                        userDiv.className = 'user-request';
                        userDiv.innerHTML = `<div class="label">YOU</div><div class="content">${'$'}{text}</div>`;
                        chatHistory.appendChild(userDiv);
                        
                        instructionInput.value = '';

                        lastLoadingId = 'loading-' + Date.now();
                        const loadingStep = createStepElement('Thinking...', `${'$'}{currentAgent} is working...`, 'loading', lastLoadingId);
                        chatHistory.appendChild(loadingStep);
                        scrollToBottom();

                        try {
                             window.lisa.postMessage(JSON.stringify({
                                 command: 'runAgent',
                                 agent: currentAgent,
                                 instruction: text
                             }));
                             log("Bridge postMessage success");
                        } catch (e) {
                             log("Bridge postMessage FAILED: " + e.message);
                        }
                    }

                    function addStep(title, detail, icon, id) {
                        const step = createStepElement(title, detail, icon, id);
                        chatHistory.appendChild(step);
                        scrollToBottom();
                    }

                    function createStepElement(title, detail, iconType, id) {
                        const div = document.createElement('div');
                        div.className = 'step-item';
                        if(id) div.id = id;
                        
                        let icon = '⚪';
                        if(iconType === 'check') icon = '✅';
                        if(iconType === 'error') icon = '❌';
                        if(iconType === 'loading') icon = '⏳';
                        if(iconType === 'info') icon = 'ℹ️';

                        div.innerHTML = `
                            <div class="step-icon">${'$'}{icon}</div>
                            <div class="step-content">
                                <div class="step-title">${'$'}{title}</div>
                                ${'$'}{detail ? `<div class="step-detail">${'$'}{detail}</div>` : ''}
                            </div>
                        `;
                        return div;
                    }

                    function scrollToBottom() {
                        chatHistory.scrollTop = chatHistory.scrollHeight;
                    }

                    // Listen for messages from the extension (Agent Response)
                    window.addEventListener('message', event => {
                        const message = event.data; 
                        log("Event Listener Triggered. Cmd: " + (message ? message.command : 'null'));

                        if (message && message.command === 'agentResponse') {
                            log("Handling agentResponse. Success: " + (message.data ? message.data.success : 'unknown'));
                            
                            // Find and remove the loading step
                            if (lastLoadingId) {
                                const loadingStep = document.getElementById(lastLoadingId);
                                if (loadingStep) loadingStep.remove();
                                lastLoadingId = '';
                            }

                            const result = message.data || {};
                            const success = result.success;
                            const data = result.data; 
                            const error = result.error;

                            if (success) {
                                const responseDiv = document.createElement('div');
                                responseDiv.className = 'agent-response';
                                responseDiv.innerHTML = `<div class="label">LISA</div><div class="content">${'$'}{data}</div>`;
                                chatHistory.appendChild(responseDiv);
                            } else {
                                log("Agent returned error: " + error);
                                addStep('Error', error || 'Unknown error occurred.', 'error');
                                
                                if (error && (error.includes('API Key') || error.includes('401'))) {
                                    settingsPanel.classList.add('open');
                                }
                            }
                            scrollToBottom();
                        }
                        
                        if (message && message.command === 'debug') {
                             log("BACKEND: " + message.message);
                        }
                    });

                    // Robust Message Receiver for JCEF
                    window.receiveMessage = function(jsonStr) {
                         log("receiveMessage called. Len: " + jsonStr.length);
                         try {
                             const msg = JSON.parse(jsonStr);
                             
                             // Dispatch a synthetic message event so existing listeners work
                             const event = new MessageEvent('message', { data: msg });
                             window.dispatchEvent(event);
                             log("Event dispatched for: " + msg.command);
                             
                         } catch (e) {
                             log("receiveMessage Error: " + e.message);
                         }
                    };
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
                                    val future = (it as Endpoint).request("lisa/configure", configParams) as CompletableFuture<Any>
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
