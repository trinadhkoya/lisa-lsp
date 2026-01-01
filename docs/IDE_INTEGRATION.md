# LISA IDE Integration Guide

This guide explains how to connect the `lisa-lsp` server to **VS Code** and **WebStorm**.

## 1. VS Code Integration (TypeScript/JavaScript)

### Step 1: Create a new VS Code Extension
Use the Yeoman generator: `yo code` (choose TypeScript).

### Step 2: Add the Language Client dependency
```bash
npm install vscode-languageclient
```

### Step 3: Launch the Server in `extension.ts`
```typescript
import * as path from 'path';
import { ExtensionContext, commands, window, ProgressLocation } from 'vscode';
import { LanguageClient, LanguageClientOptions, ServerOptions, TransportKind } from 'vscode-languageclient/node';

let client: LanguageClient;

export function activate(context: ExtensionContext) {
    const serverModule = context.asAbsolutePath(path.join('out', 'server.js'));

    const serverOptions: ServerOptions = {
        run: { module: serverModule, transport: TransportKind.stdio },
        debug: { module: serverModule, transport: TransportKind.stdio }
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: 'typescript' }],
    };

    client = new LanguageClient('lisaLsp', 'LISA Language Server', serverOptions, clientOptions);
    client.start();

    // Register Agentic Command
    context.subscriptions.push(
        commands.registerCommand('lisa.execute', async () => {
            const command = await window.showInputBox({ 
                prompt: 'What do you want LISA to do?',
                placeHolder: 'e.g. "review this MR 1307" or "create jira ticket for login bug"'
            });
            
            if (command) {
                await window.withProgress({
                    location: ProgressLocation.Notification,
                    title: 'LISA is working...',
                    cancellable: false
                }, async () => {
                    const response = await client.sendRequest('lisa/execute', command);
                    window.showInformationMessage(JSON.stringify(response));
                });
            }
        })
    );

    // NEW: Command to update AI Provider Settings
    context.subscriptions.push(
        commands.registerCommand('lisa.setProvider', async () => {
            const provider = await window.showQuickPick(['openai', 'groq', 'gemini', 'claude'], { placeHolder: 'Select AI Provider' });
            const apiKey = await window.showInputBox({ prompt: `Enter ${provider} API Key`, password: true });
            const model = await window.showInputBox({ prompt: 'Enter Model Name (e.g. gpt-4, claude-3-opus, gemini-pro)', value: 'gpt-4' });

            if (provider && apiKey && model) {
                await client.sendRequest('lisa/updateConfig', { provider, apiKey, model });
                window.showInformationMessage(`LISA updated to use ${provider}`);
            }
        })
    );
}
```

---

## 2. WebStorm / JetBrains Integration (Kotlin)

### Step 1: Create an IntelliJ Platform Plugin
Use the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).

### Step 2: Define the LSP Server Support
```kotlin
class LisaLspServerDescriptor(project: Project, virtualFile: VirtualFile) : LspServerDescriptor(project, "LISA", virtualFile) {
    override fun isSupportedFile(file: VirtualFile) = file.extension == "ts" || file.extension == "js"

    override fun createCommandLine(): GeneralCommandLine {
        return GeneralCommandLine("node", "/absolute/path/to/lisa-lsp/dist/server.js", "--stdio")
            .withWorkDirectory(project.basePath)
    }
}
```

### ⚡ Quick Test in WebStorm (No Code Required)
If you just want to see LISA working in WebStorm right now:
1.  Open **WebStorm Settings** (`Cmd + ,`).
2.  Go to **Plugins** and install the **"LSP Support"** plugin (by krasa).
3.  Restart WebStorm.
4.  Go to **Settings** -> **Languages & Frameworks** -> **Language Server**.
5.  Click the **+** button to add a new server:
    -   **Extension**: `ts` (or `js`)
    -   **Server Path**: `node`
    -   **Server Args**: `/absolute/path/to/lisa-lsp/dist/server.js --stdio`
6.  Open any `.ts` file. You should see LISA status in the bottom right corner.
7.  Check `server.log` in your `lisa-lsp` folder to confirm connection!

---

## 3. Configuration

Ensure the `.env` file is in the root of the `lisa-lsp` folder. The server will automatically load it on startup.
