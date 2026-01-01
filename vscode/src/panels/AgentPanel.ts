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
        // Tip: Install the es6-string-html VS Code extension to enable code highlighting below
        return /*html*/ `
      <!DOCTYPE html>
      <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>LISA Agent Manager</title>
          <style>
            body {
                background-color: var(--vscode-editor-background);
                color: var(--vscode-editor-foreground);
                font-family: var(--vscode-font-family);
                padding: 20px;
            }
            .container {
                display: flex;
                flex-direction: column;
                gap: 15px;
            }
            label {
                font-weight: bold;
                margin-bottom: 5px;
                display: block;
            }
            select, textarea, input {
                width: 100%;
                background-color: var(--vscode-input-background);
                color: var(--vscode-input-foreground);
                border: 1px solid var(--vscode-input-border);
                padding: 8px;
                font-family: inherit;
            }
            button {
                background-color: var(--vscode-button-background);
                color: var(--vscode-button-foreground);
                border: none;
                padding: 10px;
                cursor: pointer;
                font-weight: bold;
            }
            button:hover {
                background-color: var(--vscode-button-hoverBackground);
            }
            .card {
                background-color: var(--vscode-editor-inactiveSelectionBackground);
                padding: 10px;
                border-radius: 4px;
            }
          </style>
        </head>
        <body>
          <h2>🤖 LISA Agent Manager</h2>
          <div class="container">
            
            <!-- Agent Selection -->
            <div>
                <label>Choose Agent Capability</label>
                <select id="agent-type">
                    <option value="chat">💬 General Chat</option>
                    <option value="generateTests">🧪 QA Automation (Test Gen)</option>
                    <option value="addJsDoc">📝 Documentation Expert</option>
                    <option value="refactor">🛠️ Code Refactorer</option>
                </select>
            </div>

            <!-- Context Info -->
            <div class="card">
                <label>Current Context</label>
                <div id="context-info" style="font-size: 0.9em; opacity: 0.8;">
                    No active editor detected.
                </div>
            </div>

            <!-- Instruction Input -->
            <div>
                <label>Instructions / Prompt</label>
                <textarea id="instruction" rows="4" placeholder="Enter specific instructions (e.g. 'Use Jest', 'Focus on edge cases')..."></textarea>
            </div>

            <button id="run-btn">Run Agent</button>
            
            <div id="status"></div>

          </div>

          <script>
            const vscode = acquireVsCodeApi();
            const runBtn = document.getElementById('run-btn');
            const agentSelect = document.getElementById('agent-type');
            const instructionInput = document.getElementById('instruction');

            // Listen for context updates from extension
            window.addEventListener('message', event => {
                const message = event.data;
                if (message.command === 'updateContext') {
                    document.getElementById('context-info').innerText = message.text;
                }
            });

            runBtn.addEventListener('click', () => {
                const agent = agentSelect.value;
                const instruction = instructionInput.value;
                
                vscode.postMessage({
                    command: 'runAgent',
                    agent: agent,
                    instruction: instruction
                });
            });
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

    public static onMessage: (message: any) => void;
}
