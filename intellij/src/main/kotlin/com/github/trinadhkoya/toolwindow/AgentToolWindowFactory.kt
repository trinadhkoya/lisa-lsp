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
import org.eclipse.lsp4j.services.LanguageServer
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
            val query = JBCefJSQuery.create(browser as JBCefBrowser)
            Disposer.register(browser, query)
            jsQuery = query
            
            query.addHandler { msg ->
                handleMessage(msg)
                null
            }

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
                        --bg-app: #1e1f22; 
                        --bg-panel: #2b2d30;
                        --bg-input: #1e1f22;
                        --border: #393b40;
                        --text-primary: #dfe1e5;
                        --text-secondary: #9da0a8;
                        --accent: #3574f0;
                        --accent-hover: #3069d6;
                        --font-family: 'Inter', system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
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
                        line-height: 1.4;
                    }

                    header {
                        padding: 12px 16px;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        flex-shrink: 0;
                    }
                    .header-title { font-weight: 600; font-size: 14px; color: var(--text-primary); display: flex; align-items: center; gap: 8px;}

                    #chat-history {
                        flex: 1;
                        overflow-y: auto;
                        padding: 0;
                        display: flex;
                        flex-direction: column;
                        position: relative;
                    }

                    .welcome-container {
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        flex: 1;
                        padding: 40px 20px;
                        text-align: center;
                        background: radial-gradient(circle at 50% 20%, #2b2d30 0%, var(--bg-app) 70%);
                    }

                    .hero-art {
                        position: relative;
                        width: 200px;
                        height: 140px;
                        margin-bottom: 24px;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                    }
                    
                    .bubble {
                        position: absolute;
                        border-radius: 50%;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        box-shadow: 0 4px 20px rgba(0,0,0,0.3);
                        font-weight: 700;
                        color: white;
                        animation: float 6s ease-in-out infinite;
                    }
                    @keyframes float { 0% { transform: translateY(0px); } 50% { transform: translateY(-10px); } 100% { transform: translateY(0px); } }

                    .b-main { width: 60px; height: 60px; background: linear-gradient(135deg, #fa7e23, #d95e16); z-index: 3; font-size: 24px; top: 30px; left: 60px; animation-delay: 0s; }
                    .b-sec { width: 50px; height: 50px; background: linear-gradient(135deg, #3574f0, #2558c2); z-index: 2; top: 20px; right: 50px; animation-delay: 1s; opacity: 0.9; }
                    .b-tri { width: 40px; height: 40px; background: #50a14f; z-index: 1; bottom: 30px; left: 80px; animation-delay: 2s; opacity: 0.8; }
                    .b-glow { width: 140px; height: 140px; background: radial-gradient(circle, rgba(255,255,255,0.03) 0%, transparent 70%); position: absolute; z-index: 0; }

                    .welcome-title { font-size: 20px; font-weight: 600; margin: 0 0 8px 0; color: var(--text-primary); }
                    .welcome-subtitle { font-size: 13px; color: var(--text-secondary); max-width: 280px; margin: 0 auto 32px auto; line-height: 1.5; }

                    .setup-btn {
                        background: rgba(255,255,255,0.05);
                        border: 1px solid var(--border);
                        color: var(--text-primary);
                        padding: 10px 16px;
                        border-radius: 6px;
                        font-size: 13px;
                        cursor: pointer;
                        display: inline-flex; align-items: center; gap: 8px;
                    }
                    .setup-btn:hover { background: rgba(255,255,255,0.1); border-color: var(--text-secondary); }

                    .user-message {
                        align-self: flex-end; background: #2b2d30; padding: 10px 14px; border-radius: 12px;
                        max-width: 85%; font-size: 13px; margin: 8px 16px; color: var(--text-primary); border: 1px solid var(--border);
                    }
                    .agent-message {
                        align-self: flex-start; max-width: 90%; font-size: 13px; line-height: 1.6; color: var(--text-primary); margin: 8px 16px;
                    }
                    .agent-message strong { font-weight: 600; color: var(--text-primary); }
                    
                    .input-container { padding: 16px; background: var(--bg-app); border-top: 1px solid var(--border); }
                    .input-box {
                        background: var(--bg-input); border: 1px solid var(--border); border-radius: 8px;
                        padding: 10px; display: flex; flex-direction: column; gap: 8px;
                    }
                    .input-box:focus-within { border-color: var(--text-secondary); }
                    textarea {
                        background: transparent; border: none; color: var(--text-primary);
                        font-family: inherit; font-size: 13px; resize: none; outline: none;
                        min-height: 24px; max-height: 150px;
                    }
                    .input-footer { display: flex; justify-content: space-between; align-items: center; margin-top: 4px; }
                    .model-selector { font-size: 11px; color: var(--text-secondary); cursor: pointer; display: flex; align-items: center; gap: 4px; }
                    .icon-btn { background: none; border: none; color: var(--text-secondary); cursor: pointer; padding: 4px; border-radius: 4px; }
                    .icon-btn:hover { color: var(--text-primary); background: rgba(255,255,255,0.05); }

                    .settings-overlay {
                        position: absolute; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.8);
                        display: none; z-index: 100; align-items: center; justify-content: center;
                    }
                    .settings-overlay.open { display: flex; }
                    .settings-card {
                        background: var(--bg-panel); border: 1px solid var(--border); border-radius: 8px;
                        padding: 24px; width: 300px; box-shadow: 0 10px 40px rgba(0,0,0,0.5);
                    }
                    .form-group { margin-bottom: 16px; }
                    .form-group label { display: block; font-size: 11px; margin-bottom: 6px; color: var(--text-secondary); }
                    .form-group select, .form-group input { 
                        width: 100%; background: var(--bg-app); border: 1px solid var(--border); 
                        color: var(--text-primary); padding: 8px; border-radius: 4px; font-size: 13px;
                    }
                    .save-btn { width: 100%; background: var(--accent); color: white; border: none; padding: 10px; border-radius: 4px; cursor: pointer; }
                    
                    .context-pill {
                        background: var(--bg-app); border: 1px dashed var(--border); color: var(--text-secondary);
                        font-size: 11px; padding: 4px 8px; border-radius: 4px; display: flex; align-items: center; gap: 6px; margin-bottom: 8px;
                    }
                    .context-remove { cursor: pointer; opacity: 0.6; }
                    .context-remove:hover { opacity: 1; color: #ef4444; }
                  </style>
                </head>
                <body>
                  <header>
                    <div class="header-title">
                       LISA Agent
                    </div>
                    <div>
                        <button class="icon-btn" title="Clear Chat" onclick="window.location.reload()">
                            <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor"><path d="M5.5 5.5A.5.5 0 0 1 6 6v6a.5.5 0 0 1-1 0V6a.5.5 0 0 1 .5-.5zm2.5 0a.5.5 0 0 1 .5.5v6a.5.5 0 0 1-1 0V6a.5.5 0 0 1 .5-.5zm3 .5a.5.5 0 0 0-1 0v6a.5.5 0 0 0 1 0V6z"/><path fill-rule="evenodd" d="M14.5 3a1 1 0 0 1-1 1H13v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V4h-.5a1 1 0 0 1-1-1V2a1 1 0 0 1 1-1H6a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1h3.5a1 1 0 0 1 1 1v1zM4.118 4 4 4.059V13a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1V4.059L11.882 4H4.118zM2.5 3V2h11v1h-11z"/></svg>
                        </button>
                        <button class="icon-btn" title="Settings" onclick="document.getElementById('settings-panel').classList.add('open')">
                            <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor"><path d="M9.405 1.05c-.413-1.4-2.397-1.4-2.81 0l-.1.34a1.464 1.464 0 0 1-2.105.872l-.31-.17c-1.283-.698-2.686.705-1.987 1.987l.169.311c.446.82.023 1.841-.872 2.105l-.34.1c-1.4.413-1.4 2.397 0 2.81l.34.1a1.464 1.464 0 0 1 .872 2.105l-.17.31c-.698 1.283.705 2.686 1.987 1.987l.311-.169a1.464 1.464 0 0 1 2.105.872l.1.34c.413 1.4 2.397 1.4 2.81 0l.1-.34a1.464 1.464 0 0 1 2.105-.872l.31.17c1.283.698 2.686-.705 1.987-1.987l-.169-.311a1.464 1.464 0 0 1 .872-2.105l.34-.1c1.4-.413 1.4-2.397 0-2.81l-.34-.1a1.464 1.464 0 0 1-.872-2.105l.17-.31c.698-1.283-.705-2.686-1.987-1.987l-.311.169a1.464 1.464 0 0 1-2.105-.872l-.1-.34zM8 10.93a2.929 2.929 0 1 1 0-5.86 2.929 2.929 0 0 1 0 5.86z"/></svg>
                        </button>
                    </div>
                  </header>

                   <div id="chat-history">
                        <div class="welcome-container" id="welcome-screen">
                             <div class="hero-art">
                                <div class="bubble b-glow"></div>
                                <div class="bubble b-main">AI</div>
                                <div class="bubble b-sec"></div>
                                <div class="bubble b-tri"></div>
                             </div>
                             <div class="welcome-title">Coding Agents. Ready When You Are.</div>
                             <div class="welcome-subtitle">
                                Meet your AI crew. Get seamless assistance from agents like Claude, Gemini, and Local Models.
                             </div>
                             
                             <button class="setup-btn" onclick="document.getElementById('settings-panel').classList.add('open')">
                                <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor"><path d="M8 0a8 8 0 1 0 0 16A8 8 0 0 0 8 0zm0 15A7 7 0 1 1 8 1a7 7 0 0 1 0 14z"/><path d="M11 7H8V4H7v3H4v1h3v3h1V8h3V7z"/></svg> 
                                Bring Your Own API Key
                             </button>
                        </div>
                  </div>

                    <div id="settings-panel" class="settings-overlay">
                        <div class="settings-card">
                            <h3 style="margin-top:0; font-size:14px; margin-bottom:16px; color:var(--text-primary);">Configuration</h3>
                            <div class="form-group">
                                <label>Provider</label>
                                <select id="provider-select">
                                    <option value="openai">OpenAI</option>
                                    <option value="claude">Anthropic</option>
                                    <option value="gemini">Gemini</option>
                                    <option value="groq">Groq</option>
                                    <option value="ollama">Ollama (Local)</option>
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
                            <div style="display:flex; gap:10px;">
                                <button class="save-btn" onclick="document.getElementById('settings-panel').classList.remove('open')" style="background:transparent; border:1px solid var(--border);">Cancel</button>
                                <button class="save-btn" id="save-config-btn">Save Configuration</button>
                            </div>
                        </div>
                    </div>

                  <div class="input-container">
                    <div id="context-area" class="context-area"></div>
                    <div class="input-box">
                        <textarea id="instruction" placeholder="Ask AI Assistant..."></textarea>
                        <div class="input-footer">
                            <div class="model-selector" id="model-btn">
                                <span id="current-model-name">Model</span> <span>⌄</span>
                            </div>
                            <div style="display:flex; gap:6px;">
                                <button class="icon-btn" id="send-btn" style="color:var(--accent);">
                                    <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor"><path d="M15.854.146a.5.5 0 0 1 .11.54l-5.819 14.547a.75.75 0 0 1-1.329.124l-3.178-4.995L.643 7.184a.75.75 0 0 1 .124-1.33L15.314.037a.5.5 0 0 1 .54.11ZM6.636 10.07l2.761 4.338L14.13 2.576 6.636 10.07Zm6.787-8.201L1.591 6.602l4.339 2.76 7.494-7.493Z"/></svg>
                                </button>
                            </div>
                        </div>
                    </div>
                  </div>

                  <div id="debug-log" style="display:none;"></div>
                  <script>
                    const chatHistory = document.getElementById('chat-history');
                    const instructionInput = document.getElementById('instruction');
                    const sendBtn = document.getElementById('send-btn');
                    const contextArea = document.getElementById('context-area');
                    
                    const settingsPanel = document.getElementById('settings-panel');
                    const modelBtn = document.getElementById('model-btn');
                    const saveConfigBtn = document.getElementById('save-config-btn');
                    const providerSelect = document.getElementById('provider-select');
                    const modelSelect = document.getElementById('model-select');
                    const apiKeyInput = document.getElementById('api-key');

                    let currentAgent = 'chat';
                    let attachedContext = null;
                    
                     const models = {
                        'openai': ['gpt-4o', 'gpt-4-turbo', 'gpt-4', 'gpt-3.5-turbo', 'o1-preview', 'o1-mini'],
                        'groq': ['llama-3.1-70b-versatile', 'llama-3.1-8b-instant', 'mixtral-8x7b-32768', 'gemma-7b-it'],
                        'gemini': ['gemini-1.5-pro', 'gemini-1.5-flash', 'gemini-1.0-pro'],
                        'claude': ['claude-3-5-sonnet-20240620', 'claude-3-opus-20240229', 'claude-3-sonnet-20240229', 'claude-3-haiku-20240307'],
                        'ollama': ['llama3.2', 'llama3', 'mistral', 'codellama', 'deepseek-coder']
                    };

                    function updateModels() {
                        const p = providerSelect.value;
                        if (models[p]) {
                            modelSelect.innerHTML = models[p].map(m => `<option value="${'$'}{m}">${'$'}{m}</option>`).join('');
                        }
                        const currentModel = modelSelect.value || 'Model';
                        let displayName = currentModel.split('-').map(s => s.charAt(0).toUpperCase() + s.slice(1)).join(' ');
                        if (displayName.length > 15) displayName = displayName.substring(0, 12) + '...';
                        document.getElementById('current-model-name').textContent = displayName;
                    }

                    providerSelect.addEventListener('change', updateModels);
                    modelSelect.addEventListener('change', updateModels);
                    
                    modelBtn.onclick = () => settingsPanel.classList.toggle('open');
                    
                    saveConfigBtn.onclick = () => {
                        window.lisa.postMessage(JSON.stringify({
                            command: 'saveConfig',
                            provider: providerSelect.value,
                            model: modelSelect.value,
                            apiKey: apiKeyInput.value
                        }));
                        settingsPanel.classList.remove('open');
                        updateModels();
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

                    instructionInput.addEventListener('input', function() {
                        this.style.height = 'auto';
                        this.style.height = (this.scrollHeight) + 'px';
                    });

                    function submit() {
                        const text = instructionInput.value.trim();
                        if (!text) return;
                        
                        const welcome = document.getElementById('welcome-screen');
                        if (welcome) welcome.remove();

                        const userDiv = document.createElement('div');
                        userDiv.className = 'user-message';
                        userDiv.textContent = text;
                        chatHistory.appendChild(userDiv);
                        
                        instructionInput.value = '';
                        instructionInput.style.height = 'auto';
                        chatHistory.scrollTop = chatHistory.scrollHeight;
                        
                         try {
                             const loadingDiv = document.createElement('div');
                             loadingDiv.className = 'agent-message';
                             loadingDiv.id = 'lisa-thinking';
                             loadingDiv.innerHTML = '<strong>LISA</strong><br><span style="opacity:0.7">Thinking...</span>';
                             chatHistory.appendChild(loadingDiv);

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

                    sendBtn.onclick = submit;
                    instructionInput.onkeydown = (e) => {
                        if(e.key === 'Enter' && !e.shiftKey) {
                            e.preventDefault();
                            submit();
                        }
                    };
                    
                    window.addEventListener('message', event => {
                        const message = event.data; 
                        
                        const loading = document.getElementById('lisa-thinking');
                        if (loading && (message.command === 'agentResponse' || (message.data && message.data.error))) {
                            loading.remove();
                        }

                        if (message && message.command === 'agentResponse') {
                            const result = message.data || {};
                            if (result.success) {
                                const responseDiv = document.createElement('div');
                                responseDiv.className = 'agent-message';
                                let content = result.data || "";
                                content = content.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
                                content = content.replace(/`(.*?)`/g, '<code style="background:#333;padding:2px 4px;border-radius:3px;">$1</code>');
                                content = content.replace(/\n/g, '<br>'); 
                                responseDiv.innerHTML = `<strong>LISA</strong><br>${'$'}{content}`;
                                chatHistory.appendChild(responseDiv);
                            } else {
                                const errDiv = document.createElement('div');
                                errDiv.className = 'agent-message';
                                errDiv.style.color = '#ef4444';
                                errDiv.textContent = `Error: ${'$'}{result.error}`;
                                chatHistory.appendChild(errDiv);
                            }
                            chatHistory.scrollTop = chatHistory.scrollHeight;
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
                     
                    window.receiveMessage = function(jsonStr) {
                         try {
                             const msg = JSON.parse(jsonStr);
                             const event = new MessageEvent('message', { data: msg });
                             window.dispatchEvent(event);
                         } catch (e) {}
                    };

                    updateModels();
                    
                    window.addEventListener('keydown', (e) => {
                        if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'l') {
                            e.preventDefault();
                            instructionInput.focus();
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
             val json = "{\"command\": \"debug\", \"message\": $escapedMsg}"
             val js = "if(window.receiveMessage) window.receiveMessage('$json');"
             ApplicationManager.getApplication().invokeLater {
                 browser.cefBrowser.executeJavaScript(js, null, 0)
             }
        }

        private fun handleMessage(jsonStr: String) {
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val jsonObject = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject
                    val command = jsonObject.get("command")?.asString ?: ""
                    
                    if (command == "getContext") {
                         ApplicationManager.getApplication().invokeLater {
                             try {
                                 val editor = FileEditorManager.getInstance(project).selectedTextEditor
                                 if (editor != null) {
                                     val file = editor.virtualFile
                                     if (file != null) {
                                         val fileName = file.name
                                         val content = editor.document.text
                                         val ext = file.extension ?: ""
                                         val language = when(ext) {
                                             "kt" -> "kotlin"
                                             "ts", "js" -> "typescript"
                                             "java" -> "java"
                                             else -> ext
                                         }
                                         
                                         val escapedName = escapeJsonString(fileName)
                                         val escapedContent = escapeJsonString(content)
                                         val escapedLang = escapeJsonString(language)
                                         
                                         val json = """{"command": "setContext", "file": $escapedName, "content": $escapedContent, "language": $escapedLang}"""
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
                                
                                val future = invokeLspRequest(server, "lisa/updateConfig", configParams)
                                if (future != null) {
                                    future.get() 
                                    ApplicationManager.getApplication().invokeLater {
                                        dispatchResponse(true, "Configuration saved successfully!", null)
                                    }
                                } else {
                                     throw Exception("No compatible sendRequest method found on " + server.javaClass.name + ". Methods: " + server.javaClass.methods.map { it.name }.joinToString(", "))
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
                            dispatchResponse(false, null, "LISA Server not found. Please open a file in the editor.")
                            return@executeOnPooledThread
                        }
                        val server = servers.first()

                        val contextData = mutableMapOf<String, String>()
                        
                        com.intellij.openapi.application.ApplicationManager.getApplication().runReadAction {
                            val editor = FileEditorManager.getInstance(project).selectedTextEditor
                            if (editor != null) {
                                contextData["selection"] = editor.selectionModel.selectedText ?: ""
                                contextData["fileContent"] = editor.document.text
                                contextData["uri"] = editor.virtualFile?.url ?: ""
                                contextData["languageId"] = editor.virtualFile?.fileType?.name?.lowercase() ?: ""
                            }
                        }
                        
                        if (jsonObject.has("attachedContext") && !jsonObject.get("attachedContext").isJsonNull) {
                             val ac = jsonObject.getAsJsonObject("attachedContext")
                             val acContent = ac.get("content")?.asString
                             val acLang = ac.get("language")?.asString
                             
                             if (!acContent.isNullOrEmpty()) contextData["fileContent"] = acContent
                             if (!acLang.isNullOrEmpty()) contextData["languageId"] = acLang
                        }
                        
                        var prompt = instruction
                        if (agent == "generateTests") prompt = "Generate unit tests for this code"
                        if (agent == "addJsDoc") prompt = "Add JSDoc documentation"
                        if (agent == "refactor") prompt = "Refactor this code: $instruction"

                        project.service<MyProjectService>().scope.launch {
                             try {
                                val commandParams = org.eclipse.lsp4j.ExecuteCommandParams()
                                commandParams.command = "lisa.chat"
                                commandParams.arguments = listOf(
                                    com.google.gson.JsonPrimitive(prompt),
                                    com.google.gson.JsonObject().apply {
                                        contextData.forEach { (key, value) ->
                                            addProperty(key, value)
                                        }
                                    }
                                )
                                
                                val future = invokeLspRequest(server, "workspace/executeCommand", commandParams)
                                
                                if (future == null) throw Exception("No compatible sendRequest method found on " + server.javaClass.name)

                                val rawResponse = future.get()
                                
                                if (rawResponse == null) {
                                    ApplicationManager.getApplication().invokeLater {
                                        dispatchResponse(false, null, "LSP server returned null.")
                                    }
                                    return@launch
                                }
                                
                                val actualResult = rawResponse.toString()
                                
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
        
        // Helper to invoke sendRequest via reflection supporting Direct Server Access, Lambda (Modern), and Legacy styles
        private fun invokeLspRequest(server: Any, lspMethod: String, params: Any): CompletableFuture<Any>? {
            val serverClass = server.javaClass
            
            // Strategy 1: Get underlying lsp4jServer directly (Most Robust)
            // This works by finding the accessor method (often mangled with $intellij_platform_lsp_impl)
            // and invoking methods directly on the Endpoint/LanguageServer interface, avoiding Kotlin lambda type mismatches.
            try {
                val getServerMethod = serverClass.methods.find { it.name.startsWith("getLsp4jServer") }
                if (getServerMethod != null) {
                    getServerMethod.isAccessible = true
                    val ls = getServerMethod.invoke(server)
                    
                    if (ls != null) {
                         // Check for LanguageServer for executeCommand (which is on WorkspaceService)
                         if (lspMethod == "workspace/executeCommand" && ls is LanguageServer && params is org.eclipse.lsp4j.ExecuteCommandParams) {
                             return ls.workspaceService.executeCommand(params) as? CompletableFuture<Any>
                         } 
                         // Check for Endpoint for generic requests
                         else if (ls is Endpoint) {
                             return ls.request(lspMethod, params) as? CompletableFuture<Any>
                         }
                    }
                }
            } catch (e: Exception) {
                // Ignore and try next strategy
            }
            
            // Strategy 2: Modern API (sendRequest { ls -> ... })
            val lambda = { ls: Any ->
                if (lspMethod == "workspace/executeCommand" && ls is LanguageServer && params is org.eclipse.lsp4j.ExecuteCommandParams) {
                     ls.workspaceService.executeCommand(params)
                } else if (ls is Endpoint) {
                     ls.request(lspMethod, params)
                } else {
                     throw RuntimeException("LanguageServer proxy $ls does not support Endpoint or LanguageServer interface for method $lspMethod")
                }
            }
            
            val modernCandidates = serverClass.methods.filter { it.name == "sendRequest" }
            for (m in modernCandidates) {
                try {
                     return m.invoke(server, lambda) as? CompletableFuture<Any>
                } catch (e: Exception) {
                     // ignore
                }
            }
        
            // Strategy 3: Legacy API (sendRequestAsync(String, Object))
            val legacyCandidates = serverClass.methods.filter { it.name == "sendRequestAsync" || it.name == "sendRequest" || it.name == "request" }
            for (m in legacyCandidates) {
                try {
                    // Avoid re-invoking the function-based sendRequest which we tried in Strategy 2
                    if (m.parameterTypes.isNotEmpty() && !m.parameterTypes[0].name.contains("Function")) {
                        return m.invoke(server, lspMethod, params) as? CompletableFuture<Any>
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
            
            return null
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
            
            val jsArg = escapeJsonString(jsonMsg)
            
            val runJs = """
                if (window.receiveMessage) {
                    window.receiveMessage($jsArg);
                } else {
                    window.postMessage(JSON.parse($jsArg), '*');
                }
            """.trimIndent()

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
