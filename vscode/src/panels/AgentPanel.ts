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

        return /*html*/ `
      <!DOCTYPE html>
      <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <link href="${codiconsUri}" rel="stylesheet" />
          <title>LISA Agent</title>
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
                font-weight: 600;
                font-size: 20px;
                display: flex;
                align-items: center;
                gap: 12px;
            }
            .header-title img {
                width: 48px;
                height: 48px;
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
                padding: 4px 8px;
                border-radius: 4px;
            }
            .header-btn:hover { background: var(--bg-panel); color: var(--text-primary); }

            #chat-history {
                flex: 1;
                overflow-y: auto;
                padding: 16px;
                display: flex;
                flex-direction: column;
                gap: 20px;
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
                font-size: 14px;
                line-height: 1.6;
                max-width: 260px;
            }
            .welcome-text strong {
                color: var(--text-primary);
                font-weight: 600;
            }

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

            .starter-chips {
                    display: flex;
                    flex-wrap: wrap;
                    gap: 8px;
                    padding: 0 16px;
                    margin-bottom: 20px;
                    justify-content: center;
            }
            .chip-btn {
                background: var(--bg-panel);
                border: 1px solid var(--border);
                color: var(--text-secondary);
                padding: 6px 12px;
                border-radius: 16px;
                font-size: 11px;
                cursor: pointer;
                transition: all 0.2s;
            }
            .chip-btn:hover {
                    color: var(--text-primary);
                    border-color: var(--text-secondary);
                    background: var(--bg-input);
            }

            .input-container {
                padding: 16px;
                background: var(--bg-app);
                position: relative;
                z-index: 10;
            }
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
                border-color: #71717a;
                box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.1);
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
            
            .icon-btn {
                background: transparent;
                border: none;
                color: var(--text-secondary);
                cursor: pointer;
                transition: color 0.15s;
                font-size: 14px;
            }
            .icon-btn:hover { color: var(--text-primary); }

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
                display: block; font-size: 11px; color: var(--text-secondary); margin-bottom: 4px;
            }
            .form-group select, .form-group input {
                width: 100%; background: var(--bg-app); border: 1px solid var(--border);
                color: var(--text-primary); padding: 6px; border-radius: 4px; font-size: 12px;
            }
            .save-btn {
                width: 100%; background: var(--accent); color: white; border: none;
                padding: 8px; border-radius: 4px; cursor: pointer; font-size: 12px;
            }
          </style>
        </head>
        <body>
          
          <header>
            <div class="header-title">
               <img src="${logoUri}" alt="LISA">
               <span>LISA Agent</span>
            </div>
            <div class="header-actions">
                <button class="header-btn" id="clear-btn">Clear</button>
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
               <button class="chip-btn" onclick="quickAction('Explain this code')">Explain Code</button>
               <button class="chip-btn" onclick="quickAction('Write unit tests for this')">Generate Tests</button>
               <button class="chip-btn" onclick="quickAction('Find bugs in this')">Find Bugs</button>
          </div>

          <div class="input-container">
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
                <textarea id="instruction" placeholder="Ask anything or help with what our LISA can do."></textarea>
                
                <div class="input-controls">
                    <div class="left-controls">
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

          <script>
            const vscode = acquireVsCodeApi();
            
            const chatHistory = document.getElementById('chat-history');
            const instructionInput = document.getElementById('instruction');
            const runBtn = document.getElementById('run-btn');
            const micBtn = document.getElementById('mic-btn');
            const clearBtn = document.getElementById('clear-btn');
            
            const settingsPanel = document.getElementById('settings-panel');
            const modelBtn = document.getElementById('model-btn');
            const saveConfigBtn = document.getElementById('save-config-btn');
            const providerSelect = document.getElementById('provider-select');
            const modelSelect = document.getElementById('model-select');
            const apiKeyInput = document.getElementById('api-key');

            let currentAgent = 'chat';
            
             const models = {
                'openai': ['gpt-4-turbo', 'gpt-4o', 'gpt-3.5-turbo'],
                'anthropics': ['claude-3-opus-20240229', 'claude-3-sonnet-20240229', 'claude-3-haiku-20240307'],
                'gemini': ['gemini-1.5-pro', 'gemini-1.5-flash', 'gemini-pro'],
                'groq': ['llama3-70b-8192', 'mixtral-8x7b-32768', 'gemma-7b-it']
            };

            function updateModels() {
                const p = providerSelect.value;
                if (models[p]) {
                    modelSelect.innerHTML = models[p].map(m => \`<option value="\${m}">\${m}</option>\`).join('');
                }
                const currentModel = modelSelect.value || 'Model';
                let displayName = currentModel.split('-').map(s => s.charAt(0).toUpperCase() + s.slice(1)).join(' ');
                displayName = displayName.replace('Gpt', 'GPT').replace('Claude', 'Claude').replace('Gemini', 'Gemini');
                if (displayName.length > 15) displayName = displayName.substring(0, 12) + '...';
                
                document.querySelector('#model-btn').innerHTML = \`\${displayName} <span>⌄</span>\`;
            }

            providerSelect.addEventListener('change', updateModels);
            modelSelect.addEventListener('change', updateModels);
            
            modelBtn.onclick = () => settingsPanel.classList.toggle('open');
            
            saveConfigBtn.onclick = () => {
                vscode.postMessage({
                    command: 'saveConfig',
                    provider: providerSelect.value,
                    model: modelSelect.value,
                    apiKey: apiKeyInput.value
                });
                settingsPanel.classList.remove('open');
                updateModels();
            };

            clearBtn.onclick = () => {
                chatHistory.innerHTML = '';
            };

            instructionInput.addEventListener('input', function() {
                this.style.height = 'auto';
                this.style.height = (this.scrollHeight) + 'px';
            });

            function submit() {
                const text = instructionInput.value.trim();
                if (!text) return;
                
                const starterArea = document.getElementById('starter-area');
                if (starterArea) starterArea.style.display = 'none';
                
                const welcome = document.getElementById('welcome-screen');
                if (welcome) welcome.remove();
                
                const userDiv = document.createElement('div');
                userDiv.className = 'user-message';
                userDiv.textContent = text;
                chatHistory.appendChild(userDiv);
                
                instructionInput.value = '';
                instructionInput.style.height = 'auto';
                scrollToBottom();
                
                vscode.postMessage({
                    command: 'runAgent',
                    agent: currentAgent,
                    instruction: text
                });
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
            
            window.addEventListener('keydown', (e) => {
                if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'l') {
                    e.preventDefault();
                    instructionInput.focus();
                }
            });

            function scrollToBottom() {
                chatHistory.scrollTop = chatHistory.scrollHeight;
            }

            window.addEventListener('message', event => {
                const msg = event.data;
                
                if (msg.command === 'loadConfig') {
                    if(msg.provider) providerSelect.value = msg.provider;
                     updateModels(); 
                    if(msg.model) modelSelect.value = msg.model;
                    if(msg.apiKey) apiKeyInput.value = msg.apiKey;
                }

                if (msg.command === 'agentResponse') {
                    const result = msg.data || {};
                    if (result.success) {
                        const responseDiv = document.createElement('div');
                        responseDiv.className = 'agent-message';
                        let content = typeof result.data === 'string' ? result.data : JSON.stringify(result.data);
                        content = content.replace(/\\*\\*(.*?)\\*\\*/g, '<strong>$1</strong>');
                        content = content.replace(/\`(.*?)\`/g, '<code style="background:#333;padding:2px 4px;border-radius:3px;">$1</code>');
                        
                        responseDiv.innerHTML = \`<strong>LISA</strong><br>\${content}\`;
                        chatHistory.appendChild(responseDiv);
                    } else {
                        const errDiv = document.createElement('div');
                        errDiv.className = 'agent-message';
                        errDiv.style.color = '#ef4444';
                        errDiv.textContent = \`Error: \${result.error || 'Unknown'}\`;
                        chatHistory.appendChild(errDiv);
                    }
                    scrollToBottom();
                }
            });

            vscode.postMessage({ command: 'requestConfig' });
            
            if (window.webkitSpeechRecognition || window.SpeechRecognition) {
                 const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
                 const recognition = new SpeechRecognition();
                 recognition.continuous = false;
                 
                 micBtn.onclick = () => {
                    if (micBtn.classList.contains('listening')) {
                        recognition.stop();
                    } else {
                        recognition.start();
                    }
                 };
                 recognition.onstart = () => {
                     micBtn.classList.add('listening');
                     micBtn.style.color = '#ef4444';
                 };
                 recognition.onend = () => {
                     micBtn.classList.remove('listening');
                     micBtn.style.color = '';
                 };
                 recognition.onresult = (e) => {
                     const t = e.results[0][0].transcript;
                     instructionInput.value += (instructionInput.value ? ' ' : '') + t;
                 };
            } else {
                micBtn.style.display = 'none';
            }
          </script>
        </body>
      </html>
    `;
    }

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

    public postMessage(message: any) {
        this._panel.webview.postMessage(message);
    }

    public static onMessage: (message: any) => void;
}
