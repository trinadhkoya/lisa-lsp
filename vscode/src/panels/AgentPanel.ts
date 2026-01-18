import { Disposable, Webview, WebviewPanel, window, Uri, ViewColumn } from "vscode";
import { getUri } from "../utilities/getUri";
import { getNonce } from "../utilities/getNonce";

/**
 * This class manages the state and behavior of AgentPanel webview panels.
 */
export class AgentPanel {
    public static currentPanel: AgentPanel | undefined;
    private readonly _panel: WebviewPanel;
    private _disposables: Disposable[] = [];

    private constructor(panel: WebviewPanel, extensionUri: Uri) {
        this._panel = panel;
        this._panel.onDidDispose(() => this.dispose(), null, this._disposables);
        this._panel.webview.html = this._getWebviewContent(this._panel.webview, extensionUri);
        this._setWebviewMessageListener(this._panel.webview);
    }

    public static render(extensionUri: Uri) {
        if (AgentPanel.currentPanel) {
            AgentPanel.currentPanel._panel.reveal(ViewColumn.One);
        } else {
            const panel = window.createWebviewPanel(
                "showAgentPanel",
                "LISA Agent Manager",
                ViewColumn.One,
                {
                    enableScripts: true,
                    localResourceRoots: [
                        Uri.joinPath(extensionUri, "out"),
                        Uri.joinPath(extensionUri, "webview-ui/build"),
                        Uri.joinPath(extensionUri, "node_modules"),
                        Uri.joinPath(extensionUri, "assets")
                    ],
                }
            );
            AgentPanel.currentPanel = new AgentPanel(panel, extensionUri);
        }
    }

    public dispose() {
        AgentPanel.currentPanel = undefined;
        this._panel.dispose();
        while (this._disposables.length) {
            const disposable = this._disposables.pop();
            if (disposable) {
                disposable.dispose();
            }
        }
    }

    private _getWebviewContent(webview: Webview, extensionUri: Uri) {
        const codiconsUri = webview.asWebviewUri(Uri.joinPath(extensionUri, 'node_modules', '@vscode/codicons', 'dist', 'codicon.css'));
        const logoUri = webview.asWebviewUri(Uri.joinPath(extensionUri, 'assets', 'icon.png'));
        const nonce = getNonce();

        return /*html*/ `
      <!DOCTYPE html>
      <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src ${webview.cspSource} 'unsafe-inline'; script-src 'nonce-${nonce}'; font-src ${webview.cspSource};">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <link href="${codiconsUri}" rel="stylesheet" />
          <title>LISA Agent</title>
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
                    <span class="codicon codicon-clear-all"></span>
                </button>
                <button class="icon-btn" title="Settings" onclick="document.getElementById('settings-panel').classList.add('open')">
                    <span class="codicon codicon-settings-gear"></span>
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
                        <span class="codicon codicon-key"></span> 
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
                             <span class="codicon codicon-send"></span>
                        </button>
                    </div>
                </div>
            </div>
          </div>

          <script nonce="${nonce}">
            const vscode = acquireVsCodeApi();
            
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
                        modelSelect.innerHTML = models[p].map(m => '<option value="' + m + '">' + m + '</option>').join('');
                    } else {
                        modelSelect.innerHTML = '<option value="">No models</option>';
                    }
                    const currentModel = modelSelect.value || 'Model';
                    let displayName = currentModel.split('-').map(s => s.charAt(0).toUpperCase() + s.slice(1)).join(' ');
                    if (displayName.length > 15) displayName = displayName.substring(0, 12) + '...';
                    document.getElementById('current-model-name').textContent = displayName;
                }

                if (providerSelect) providerSelect.addEventListener('change', updateModels);
                if (modelSelect) modelSelect.addEventListener('change', updateModels);
                
                if (modelBtn) modelBtn.onclick = () => settingsPanel.classList.toggle('open');
                
                if (saveConfigBtn) saveConfigBtn.onclick = () => {
                    try {
                        vscode.postMessage({
                            command: 'saveConfig',
                            provider: providerSelect.value,
                            model: modelSelect.value,
                            apiKey: apiKeyInput.value
                        });
                        settingsPanel.classList.remove('open');
                        updateModels();
                    } catch(e) { console.error(e); }
                };

                function renderContextPill() {
                    if (!contextArea) return;
                    contextArea.innerHTML = '';
                    if (attachedContext) {
                        const pill = document.createElement('div');
                        pill.className = 'context-pill';
                        pill.innerHTML = '<span>' + attachedContext.file + '</span><span class="context-remove" onclick="removeContext()">×</span>';
                        contextArea.appendChild(pill);
                    }
                }

                window.removeContext = function() {
                    attachedContext = null;
                    renderContextPill();
                };

                if (instructionInput) instructionInput.addEventListener('input', function() {
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

                          vscode.postMessage({
                              command: 'runAgent',
                              agent: currentAgent,
                              instruction: text,
                              attachedContext: attachedContext
                          });
                    } catch (e) {
                          console.error("Bridge Error", e);
                    }
                }

                window.quickAction = function(text) {
                    instructionInput.value = text;
                    submit();
                };

                if (sendBtn) sendBtn.onclick = submit;
                if (instructionInput) instructionInput.onkeydown = (e) => {
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
                            // Sanitize/Format
                            content = content.replace(/\\n/g, '<br>');
                            responseDiv.innerHTML = '<strong>LISA</strong><br>' + content;
                            chatHistory.appendChild(responseDiv);
                        } else {
                            const errDiv = document.createElement('div');
                            errDiv.className = 'agent-message';
                            errDiv.style.color = '#ef4444';
                            errDiv.textContent = 'Error: ' + result.error;
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

                    if (message && message.command === 'loadConfig') {
                        if (message.provider) {
                            providerSelect.value = message.provider;
                            updateModels(); // Populate models first
                            if (message.model) {
                                 modelSelect.value = message.model;
                                 document.getElementById('current-model-name').textContent = message.model;
                            }
                            if (message.apiKey) {
                                 apiKeyInput.value = message.apiKey;
                            }
                        }
                    }
                 });

                // Request existing config from extension
                vscode.postMessage({ command: 'requestConfig' });

          </script>
        </body>
      </html>
    `;
    }

    public static onMessage: (message: any) => void;

    public postMessage(message: any) {
        this._panel.webview.postMessage(message);
    }

    /**
     * Sets up an event listener to listen for messages passed from the webview context and
     * executes code based on the message that is received.
     *
     * @param webview A reference to the extension webview
     */
    private _setWebviewMessageListener(webview: Webview) {
        webview.onDidReceiveMessage(
            (message: any) => {
                if (AgentPanel.onMessage) {
                    AgentPanel.onMessage(message);
                }
            },
            undefined,
            this._disposables
        );
    }
}
