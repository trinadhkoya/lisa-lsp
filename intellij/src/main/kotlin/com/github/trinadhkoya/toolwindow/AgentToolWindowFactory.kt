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
                        --bg-primary: #1e1e1e;
                        --bg-secondary: #252526;
                        --bg-tertiary: #2d2d30;
                        --border-color: #3e3e42;
                        --text-primary: #cccccc;
                        --text-secondary: #858585;
                        --text-muted: #6a6a6a;
                        --accent-blue: #007acc;
                        --accent-blue-hover: #1a8cdb;
                        --accent-green: #4ec9b0;
                        --accent-purple: #c586c0;
                        --success: #73c991;
                        --error: #f48771;
                        --font-main: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                        --font-mono: "SF Mono", Monaco, "Cascadia Code", "Roboto Mono", Consolas, "Courier New", monospace;
                    }

                    * { box-sizing: border-box; }
                    
                    body {
                        background: var(--bg-primary);
                        color: var(--text-primary);
                        font-family: var(--font-main);
                        margin: 0;
                        padding: 0;
                        height: 100vh;
                        display: flex;
                        flex-direction: column;
                        overflow: hidden;
                        font-size: 13px;
                        line-height: 1.6;
                    }

                    /* Header - Clean & Minimal */
                    header {
                        padding: 14px 20px;
                        border-bottom: 1px solid var(--border-color);
                        background: var(--bg-secondary);
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        flex-shrink: 0;
                    }
                    header h2 {
                        margin: 0;
                        font-size: 13px;
                        font-weight: 600;
                        color: var(--text-primary);
                        letter-spacing: -0.01em;
                    }
                    .icon-btn {
                        background: transparent;
                        border: none;
                        color: var(--text-secondary);
                        cursor: pointer;
                        padding: 6px 8px;
                        border-radius: 4px;
                        transition: all 0.15s ease;
                        font-size: 14px;
                    }
                    .icon-btn:hover {
                        background: var(--bg-tertiary);
                        color: var(--text-primary);
                    }

                    /* Chat History - Spacious & Clean */
                    #chat-history {
                        flex: 1;
                        overflow-y: auto;
                        padding: 24px 20px;
                        display: flex;
                        flex-direction: column;
                        gap: 24px;
                    }
                    #chat-history::-webkit-scrollbar { width: 8px; }
                    #chat-history::-webkit-scrollbar-track { background: transparent; }
                    #chat-history::-webkit-scrollbar-thumb { 
                        background: var(--border-color); 
                        border-radius: 4px;
                    }
                    #chat-history::-webkit-scrollbar-thumb:hover { background: #4a4a4f; }

                    /* Welcome Card */
                    .welcome-card {
                        background: linear-gradient(135deg, var(--bg-secondary) 0%, var(--bg-tertiary) 100%);
                        border: 1px solid var(--border-color);
                        border-radius: 8px;
                        padding: 20px;
                        margin-bottom: 8px;
                    }
                    .welcome-title {
                        font-size: 15px;
                        font-weight: 600;
                        margin-bottom: 8px;
                        color: var(--text-primary);
                    }
                    .welcome-subtitle {
                        font-size: 12px;
                        color: var(--text-secondary);
                        line-height: 1.5;
                    }

                    /* Message Blocks */
                    .message-block {
                        animation: slideIn 0.2s ease;
                    }
                    @keyframes slideIn {
                        from { opacity: 0; transform: translateY(8px); }
                        to { opacity: 1; transform: translateY(0); }
                    }

                    /* User Message */
                    .user-message {
                        display: flex;
                        flex-direction: column;
                        gap: 6px;
                    }
                    .message-label {
                        font-size: 11px;
                        font-weight: 600;
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                        color: var(--text-muted);
                    }
                    .user-content {
                        background: var(--bg-secondary);
                        border: 1px solid var(--border-color);
                        border-radius: 6px;
                        padding: 12px 14px;
                        font-size: 13px;
                        line-height: 1.5;
                        color: var(--text-primary);
                    }

                    /* Agent Response */
                    .agent-message {
                        display: flex;
                        flex-direction: column;
                        gap: 6px;
                    }
                    .agent-content {
                        background: var(--bg-tertiary);
                        border-left: 3px solid var(--accent-green);
                        border-radius: 6px;
                        padding: 14px 16px;
                        font-size: 13px;
                        line-height: 1.6;
                        color: var(--text-primary);
                        white-space: pre-wrap;
                        word-wrap: break-word;
                    }

                    /* Status Messages */
                    .status-message {
                        display: flex;
                        align-items: center;
                        gap: 10px;
                        padding: 10px 14px;
                        background: var(--bg-secondary);
                        border: 1px solid var(--border-color);
                        border-radius: 6px;
                        font-size: 12px;
                        color: var(--text-secondary);
                    }
                    .status-message.success { border-left: 3px solid var(--success); }
                    .status-message.error { border-left: 3px solid var(--error); }

                    /* Input Container - Modern & Clean */
                    .input-container {
                        padding: 16px 20px 20px;
                        border-top: 1px solid var(--border-color);
                        background: var(--bg-secondary);
                        flex-shrink: 0;
                    }
                    .input-wrapper {
                        background: var(--bg-tertiary);
                        border: 1px solid var(--border-color);
                        border-radius: 8px;
                        transition: border-color 0.2s ease;
                    }
                    .input-wrapper:focus-within {
                        border-color: var(--accent-blue);
                    }
                    textarea {
                        width: 100%;
                        border: none;
                        background: transparent;
                        color: var(--text-primary);
                        padding: 12px 14px;
                        font-family: var(--font-main);
                        font-size: 13px;
                        resize: none;
                        outline: none;
                        min-height: 44px;
                        max-height: 120px;
                    }
                    textarea::placeholder {
                        color: var(--text-muted);
                    }

                    /* Input Actions */
                    .input-actions {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        padding: 8px 10px;
                        border-top: 1px solid var(--border-color);
                    }
                    
                    /* Agent Pills */
                    .agent-pills {
                        display: flex;
                        gap: 6px;
                        flex-wrap: wrap;
                    }
                    .pill {
                        font-size: 11px;
                        font-weight: 500;
                        padding: 4px 10px;
                        border-radius: 4px;
                        background: var(--bg-primary);
                        color: var(--text-secondary);
                        cursor: pointer;
                        border: 1px solid transparent;
                        transition: all 0.15s ease;
                    }
                    .pill:hover {
                        background: var(--bg-secondary);
                        color: var(--text-primary);
                    }
                    .pill.active {
                        background: var(--accent-blue);
                        color: white;
                        border-color: var(--accent-blue);
                    }

                    /* Send Button */
                    .send-btn {
                        background: var(--accent-blue);
                        color: white;
                        border: none;
                        border-radius: 6px;
                        width: 34px;
                        height: 34px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        cursor: pointer;
                        transition: all 0.15s ease;
                        flex-shrink: 0;
                    }
                    .send-btn:hover {
                        background: var(--accent-blue-hover);
                        transform: scale(1.05);
                    }
                    .send-btn:active {
                        transform: scale(0.98);
                    }
                    .send-btn svg {
                        width: 16px;
                        height: 16px;
                        fill: white;
                    }

                    /* Settings Panel */
                    .settings-overlay {
                        position: absolute;
                        top: 56px;
                        right: 20px;
                        width: 300px;
                        background: var(--bg-secondary);
                        border: 1px solid var(--border-color);
                        border-radius: 8px;
                        box-shadow: 0 8px 24px rgba(0,0,0,0.4);
                        padding: 20px;
                        z-index: 100;
                        display: none;
                    }
                    .settings-overlay.open { display: block; animation: slideIn 0.2s ease; }
                    .settings-header {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        margin-bottom: 16px;
                    }
                    .settings-title {
                        font-size: 14px;
                        font-weight: 600;
                        color: var(--text-primary);
                    }
                    .close-btn {
                        background: transparent;
                        border: none;
                        color: var(--text-secondary);
                        cursor: pointer;
                        padding: 4px;
                        font-size: 16px;
                        line-height: 1;
                    }
                    .close-btn:hover { color: var(--text-primary); }
                    
                    .form-group {
                        margin-bottom: 14px;
                    }
                    .form-group label {
                        display: block;
                        font-size: 11px;
                        font-weight: 600;
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                        margin-bottom: 6px;
                        color: var(--text-muted);
                    }
                    .form-group select,
                    .form-group input {
                        width: 100%;
                        padding: 8px 10px;
                        border-radius: 4px;
                        border: 1px solid var(--border-color);
                        background: var(--bg-tertiary);
                        color: var(--text-primary);
                        font-size: 12px;
                        font-family: var(--font-main);
                        outline: none;
                        transition: border-color 0.2s ease;
                    }
                    .form-group select:focus,
                    .form-group input:focus {
                        border-color: var(--accent-blue);
                    }
                    
                    .save-btn {
                        width: 100%;
                        padding: 9px;
                        background: var(--accent-blue);
                        color: white;
                        border: none;
                        border-radius: 6px;
                        font-size: 12px;
                        font-weight: 600;
                        cursor: pointer;
                        transition: all 0.15s ease;
                        margin-top: 6px;
                    }
                    .save-btn:hover {
                        background: var(--accent-blue-hover);
                    }
                  </style>
                </head>
                <body>
                  
                  <header>
                    <h2>LISA Agent</h2>
                    <button class="icon-btn" id="toggle-settings" title="Configuration">⚙️</button>
                  </header>

                  <div id="settings-panel" class="settings-overlay">
                        <div class="settings-header">
                            <div class="settings-title">Configuration</div>
                            <button class="close-btn" id="close-settings">✕</button>
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
                            <input type="password" id="api-key" placeholder="Enter your API key" />
                        </div>
                        <button class="save-btn" id="save-config-btn">Save Configuration</button>
                  </div>

                  <!-- Chat Feed -->
                  <div id="chat-history">
                      <div class="welcome-card">
                          <div class="welcome-title">Welcome to LISA</div>
                          <div class="welcome-subtitle">I'm ready to help you code. Select an agent capability below and start chatting.</div>
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
                  
                  <div id="debug-log" style="font-size:10px; color:#666; padding:10px; border-top:1px solid #444; max-height:100px; overflow-y:auto; display:none;">
                    <div>Debug Log (v1.0.9):</div>
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
                        const successDiv = document.createElement('div');
                        successDiv.className = 'status-message success';
                        successDiv.innerHTML = `<span>✅</span><span>Configuration saved successfully!</span>`;
                        chatHistory.appendChild(successDiv);
                        scrollToBottom();
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
                        userDiv.className = 'message-block user-message';
                        userDiv.innerHTML = `
                            <div class="message-label">YOU</div>
                            <div class="user-content">${'$'}{text}</div>
                        `;
                        chatHistory.appendChild(userDiv);
                        
                        instructionInput.value = '';

                        lastLoadingId = 'loading-' + Date.now();
                        const loadingDiv = document.createElement('div');
                        loadingDiv.id = lastLoadingId;
                        loadingDiv.className = 'status-message';
                        loadingDiv.innerHTML = `<span>⏳</span><span>Thinking...</span>`;
                        chatHistory.appendChild(loadingDiv);
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
                                responseDiv.className = 'message-block agent-message';
                                responseDiv.innerHTML = `
                                    <div class="message-label">LISA</div>
                                    <div class="agent-content">${'$'}{data}</div>
                                `;
                                chatHistory.appendChild(responseDiv);
                            } else {
                                log("Agent returned error: " + error);
                                const errorDiv = document.createElement('div');
                                errorDiv.className = 'status-message error';
                                errorDiv.innerHTML = `<span>❌</span><span>${'$'}{error || 'Unknown error occurred.'}</span>`;
                                chatHistory.appendChild(errorDiv);
                                
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
