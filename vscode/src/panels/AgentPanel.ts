import { Disposable, Webview, WebviewPanel, window, Uri, ViewColumn } from "vscode";
import { getUri } from "../utilities/getUri";
import { getNonce } from "../utilities/getNonce";

/**
 * This class manages the state and behavior of AgentPanel webview panels.
 *
 * It contains all the data and methods for:
 * - Creating and rendering AgentPanel webview panels
 * - Properly cleaning up and disposing of webview resources when the panel is closed
 * - Setting the HTML (and by proxy CSS/JavaScript) content of the webview panel
 */
export class AgentPanel {
    public static currentPanel: AgentPanel | undefined;
    private readonly _panel: WebviewPanel;
    private _disposables: Disposable[] = [];

    private constructor(panel: WebviewPanel, extensionUri: Uri) {
        this._panel = panel;

        // Set an event listener to listen for when the panel is disposed (i.e. when the user closes
        // the panel or when the panel is closed programmatically)
        this._panel.onDidDispose(() => this.dispose(), null, this._disposables);

        // Set the HTML content for the webview panel
        this._panel.webview.html = this._getWebviewContent(this._panel.webview, extensionUri);

        // Set an event listener to listen for messages passed from the webview context
        this._setWebviewMessageListener(this._panel.webview);
    }

    /**
     * Renders the current webview panel if it exists otherwise a new webview panel
     * will be created and displayed.
     *
     * @param extensionUri The URI of the directory containing the extension
     */
    public static render(extensionUri: Uri) {
        if (AgentPanel.currentPanel) {
            // If the webview panel already exists reveal it
            AgentPanel.currentPanel._panel.reveal(ViewColumn.One);
        } else {
            // If a webview panel does not already exist create and show a new one
            const panel = window.createWebviewPanel(
                // Panel view type
                "showAgentPanel",
                // Panel title
                "LISA Agent Manager",
                // The editor column the panel should be displayed in
                ViewColumn.One,
                // Extra panel configurations
                {
                    // Enable JavaScript in the webview
                    enableScripts: true,
                    // Restrict the webview to only load resources from the `out` and `webview-ui/build` directories
                    localResourceRoots: [Uri.joinPath(extensionUri, "out"), Uri.joinPath(extensionUri, "webview-ui/build")],
                }
            );

            AgentPanel.currentPanel = new AgentPanel(panel, extensionUri);
        }
    }

    /**
     * Cleans up and disposes of webview resources when the webview panel is closed.
     */
    public dispose() {
        AgentPanel.currentPanel = undefined;

        // Dispose of the current webview panel
        this._panel.dispose();

        // Dispose of all disposables (i.e. commands) for the current webview panel
        while (this._disposables.length) {
            const disposable = this._disposables.pop();
            if (disposable) {
                disposable.dispose();
            }
        }
    }

    /**
     * Defines and returns the HTML that should be rendered within the webview panel.
     *
     * @remarks This is also the place where references to the CSS and JavaScript files
     * are created and inserted into the webview HTML.
     *
     * @param webview A reference to the extension webview
     * @param extensionUri The URI of the directory containing the extension
     * @returns A template string literal containing the HTML that should be rendered within the webview panel
     */
    private _getWebviewContent(webview: Webview, extensionUri: Uri) {
        // Use codicons
        const codiconsUri = webview.asWebviewUri(Uri.joinPath(extensionUri, 'node_modules', '@vscode/codicons', 'dist', 'codicon.css'));

        return /*html*/ `
      <!DOCTYPE html>
      <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <link href="\${codiconsUri}" rel="stylesheet" />
          <title>LISA Agent</title>
          <style>
            :root {
                --bg-color: var(--vscode-editor-background);
                --fg-color: var(--vscode-editor-foreground);
                --border-color: var(--vscode-widget-border);
                --item-hover: var(--vscode-list-hoverBackground);
                --input-bg: var(--vscode-input-background);
                --input-fg: var(--vscode-input-foreground);
                --primary: var(--vscode-button-background);
                --secondary: var(--vscode-descriptionForeground);
                --success: var(--vscode-charts-green);
                --error: var(--vscode-charts-red);
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
                background-color: var(--vscode-sideBar-background);
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
                background-color: var(--vscode-editorWidget-background);
                border-radius: 6px;
                font-size: 13px;
            }
            .step-item:hover {
                 border-color: var(--focus-border, #007fd4);
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
            .step-status {
                font-size: 11px;
                padding: 2px 6px;
                border-radius: 10px;
                background-color: var(--item-hover);
            }

            /* Input Area (Bottom) */
            .input-container {
                padding: 16px;
                border-top: 1px solid var(--border-color);
                background-color: var(--vscode-sideBar-background);
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
            .input-wrapper:focus-within {
                border-color: var(--focus-border, #007fd4);
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
                background-color: var(--vscode-badge-background);
                color: var(--vscode-badge-foreground);
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
                width: 28px;
                height: 28px;
                display: flex;
                align-items: center;
                justify-content: center;
                cursor: pointer;
            }
            button.send-btn:hover { opacity: 0.9; }

            /* Settings Overlay */
            .settings-overlay {
                position: absolute;
                top: 50px;
                right: 16px;
                width: 280px;
                background-color: var(--vscode-editorWidget-background);
                border: 1px solid var(--border-color);
                border-radius: 8px;
                box-shadow: 0 4px 12px rgba(0,0,0,0.25);
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

            /* Log/Terminal Style blocks */
            .log-block {
                background-color: rgba(0,0,0,0.2);
                border-radius: 4px;
                padding: 8px 12px;
                font-family: 'Consolas', 'Courier New', monospace;
                font-size: 12px;
                margin-top: 4px;
                border-left: 3px solid var(--secondary);
                white-space: pre-wrap;
            }

        </style>
        </head>
        <body>
          
          <header>
            <h2><span class="codicon codicon-hubot"></span> LISA Agent</h2>
            <div class="header-controls">
                <button class="icon-btn" id="toggle-settings" title="Configuration">
                    <span class="codicon codicon-settings-gear"></span>
                </button>
            </div>
          </header>

          <div id="settings-panel" class="settings-overlay">
                <div style="display:flex; justify-content:space-between; margin-bottom:10px;">
                    <strong>Configuration</strong>
                    <span class="codicon codicon-close" id="close-settings" style="cursor:pointer;"></span>
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
              <!-- Initial Greeting -->
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
                    <button class="send-btn" id="run-btn">
                        <span class="codicon codicon-arrow-up"></span>
                    </button>
                </div>
            </div>
            <div style="font-size:11px; color:var(--secondary); margin-top:8px; display:flex; justify-content:space-between;">
                <span id="context-indicator">No Context</span>
                <span>LISA v1.0.8</span>
            </div>
          </div>

          <script>
            const vscode = acquireVsCodeApi();
            
            // DOM Elements
            const chatHistory = document.getElementById('chat-history');
            const instructionInput = document.getElementById('instruction');
            const runBtn = document.getElementById('run-btn');
            const pills = document.querySelectorAll('.pill');
            const contextIndicator = document.getElementById('context-indicator');
            
            const settingsPanel = document.getElementById('settings-panel');
            const toggleSettings = document.getElementById('toggle-settings');
            const closeSettings = document.getElementById('close-settings');
            const saveConfig = document.getElementById('save-config-btn');
            const providerSelect = document.getElementById('provider-select');
            const modelSelect = document.getElementById('model-select');
            const apiKeyInput = document.getElementById('api-key');

            let currentAgent = 'chat';

            // --- Config Logic ---
            const models = {
                'openai': ['gpt-4-turbo', 'gpt-4o', 'gpt-3.5-turbo'],
                'anthropics': ['claude-3-opus-20240229', 'claude-3-sonnet-20240229', 'claude-3-haiku-20240307'],
                'gemini': ['gemini-1.5-pro', 'gemini-1.5-flash', 'gemini-pro'],
                'groq': ['llama3-70b-8192', 'mixtral-8x7b-32768', 'gemma-7b-it']
            };

            function updateModels() {
                const p = providerSelect.value;
                modelSelect.innerHTML = (models[p] || []).map(m => \`<option value="\${m}">\${m}</option>\`).join('');
            }
            providerSelect.addEventListener('change', updateModels);
            updateModels();

            toggleSettings.onclick = () => settingsPanel.classList.toggle('open');
            closeSettings.onclick = () => settingsPanel.classList.remove('open');

            saveConfig.onclick = () => {
                vscode.postMessage({
                    command: 'saveConfig',
                    provider: providerSelect.value,
                    model: modelSelect.value,
                    apiKey: apiKeyInput.value
                });
                settingsPanel.classList.remove('open');
                addStep('Config Saved', \`Provider: \${providerSelect.value}\`, 'check');
            };

            // --- Chat Logic ---
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

                // Add User Message
                const userDiv = document.createElement('div');
                userDiv.className = 'user-request';
                userDiv.innerHTML = \`<div class="label">YOU</div><div class="content">\${text}</div>\`;
                chatHistory.appendChild(userDiv);
                
                // Clear Input
                instructionInput.value = '';

                // Indicate Processing
                const loadingId = 'loading-' + Date.now();
                const loadingStep = createStepElement('Running Agent...', \`\${currentAgent} is processing...\`, 'loading', loadingId);
                chatHistory.appendChild(loadingStep);
                scrollToBottom();

                // Send to Extension
                vscode.postMessage({
                    command: 'runAgent',
                    agent: currentAgent,
                    instruction: text
                });

                // (Simulate completion for UI feedback for now, actual response comes via message)
                // Real implementation would wait for "Agent Finished" message to remove loading.
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

                div.innerHTML = \`
                    <div class="step-icon">\${icon}</div>
                    <div class="step-content">
                        <div class="step-title">\${title}</div>
                        \${detail ? \`<div class="step-detail">\${detail}</div>\` : ''}
                    </div>
                \`;
                return div;
            }

            function scrollToBottom() {
                chatHistory.scrollTop = chatHistory.scrollHeight;
            }

            // --- Listeners ---
            window.addEventListener('message', event => {
                const msg = event.data;
                if(msg.command === 'updateContext') {
                    contextIndicator.innerText = msg.text.split('\\n')[0]; // Simple file name
                }
                if(msg.command === 'loadConfig') {
                    if(msg.provider) providerSelect.value = msg.provider;
                    updateModels();
                    if(msg.model) modelSelect.value = msg.model;
                    if(msg.apiKey) apiKeyInput.value = msg.apiKey;
                }
                // Handle Agent Responses (If extension sends generic messages back)
                // For now, extensions use window.showInformationMessage, but we should bridge that.
            });

            // Init
            vscode.postMessage({ command: 'requestConfig' });

          </script>
        </body>
      </html>
    `;
    }

    /**
     * Sets up an event listener to listen for messages passed from the webview context.
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

    public postMessage(message: any) {
        this._panel.webview.postMessage(message);
    }

    public static onMessage: (message: any) => void;
}
