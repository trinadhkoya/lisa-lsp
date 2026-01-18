import * as path from 'path';
import * as fs from 'fs';
import { ExtensionContext, commands, window, workspace, ProgressLocation, OutputChannel, TextEditor, Uri, languages, WorkspaceEdit } from 'vscode';
import { LanguageClient, LanguageClientOptions, ServerOptions, TransportKind } from 'vscode-languageclient/node';
import { AgentPanel } from './panels/AgentPanel';
import { LisaCodeActionProvider } from './providers/CodeActionProvider';

let client: LanguageClient;
let outputChannel: OutputChannel;

// Helper to find existing tests
async function getTestContext(editor: TextEditor): Promise<{ existingTestContent: string; fileStructureInfo: string }> {
    const activeUri = editor.document.uri;
    const wsFolder = workspace.getWorkspaceFolder(activeUri);
    if (!wsFolder) return { existingTestContent: '', fileStructureInfo: '' };

    const dir = path.dirname(activeUri.fsPath);
    let existingTestContent = '';
    let foundPath = '';

    // 1. Check for EXACT matching test file (sibling or in __tests__)
    // e.g. foo.ts -> foo.test.ts or __tests__/foo.test.ts
    const fileName = path.basename(activeUri.fsPath);
    const nameNoExt = fileName.replace(/\.[^/.]+$/, "");
    const ext = fileName.split('.').pop();

    // Pattern: name.test.ext or name.spec.ext
    const candidates = [
        path.join(dir, `${nameNoExt}.test.${ext}`),
        path.join(dir, `${nameNoExt}.spec.${ext}`),
        path.join(dir, '__tests__', `${nameNoExt}.test.${ext}`),
        path.join(dir, '__tests__', `${nameNoExt}.spec.${ext}`),
        path.join(dir, 'tests', `${nameNoExt}.test.${ext}`),
        path.join(dir, 'tests', `${nameNoExt}.spec.${ext}`)
    ];

    for (const p of candidates) {
        if (fs.existsSync(p)) {
            existingTestContent = fs.readFileSync(p, 'utf-8');
            foundPath = p;
            break;
        }
    }

    // Structure Info
    const sourceRelative = workspace.asRelativePath(activeUri);
    const testRelative = foundPath ? workspace.asRelativePath(foundPath) : 'New File (autodetect)';
    const fileStructureInfo = `Source File: ${sourceRelative}\nTarget Test File: ${testRelative}`;

    return { existingTestContent, fileStructureInfo };
}

// Helper to handle test generation response
async function handleTestGenerationResponse(res: any) {
    if (res.success && res.action === 'generateTests' && res.data) {
        const activeUri = window.activeTextEditor?.document.uri;
        if (activeUri) {
            const dir = path.dirname(activeUri.fsPath);
            const fileName = path.basename(activeUri.fsPath);
            const nameNoExt = fileName.replace(/\.[^/.]+$/, "");
            const ext = fileName.split('.').pop();

            let targetPath = '';

            // 1. Check if we already have an existing test file to OVERWRITE/UPDATE
            const candidates = [
                path.join(dir, `${nameNoExt}.test.${ext}`),
                path.join(dir, `${nameNoExt}.spec.${ext}`),
                path.join(dir, '__tests__', `${nameNoExt}.test.${ext}`),
                path.join(dir, '__tests__', `${nameNoExt}.spec.${ext}`),
                path.join(dir, 'tests', `${nameNoExt}.test.${ext}`)
            ];

            for (const p of candidates) {
                if (fs.existsSync(p)) {
                    targetPath = p;
                    break;
                }
            }

            // 2. If no existing file, decide where to create new one
            if (!targetPath) {
                // Prefer __tests__ folder if it exists or if we should create it
                const testsDir = path.join(dir, '__tests__');
                if (fs.existsSync(testsDir)) {
                    targetPath = path.join(testsDir, `${nameNoExt}.test.${ext}`);
                } else {
                    // Check 'tests' folder
                    const simpleTestsDir = path.join(dir, 'tests');
                    if (fs.existsSync(simpleTestsDir)) {
                        targetPath = path.join(simpleTestsDir, `${nameNoExt}.test.${ext}`);
                    } else {
                        // Create __tests__ by default
                        try {
                            fs.mkdirSync(testsDir);
                            targetPath = path.join(testsDir, `${nameNoExt}.test.${ext}`);
                        } catch {
                            // Fallback to sibling
                            targetPath = path.join(dir, `${nameNoExt}.test.${ext}`);
                        }
                    }
                }
            }

            // Write File (Full content from Server)
            try {
                fs.mkdirSync(path.dirname(targetPath), { recursive: true });
                fs.writeFileSync(targetPath, res.data);

                // Open Document
                const doc = await workspace.openTextDocument(targetPath);
                await window.showTextDocument(doc);
                window.showInformationMessage(`Tests generated/updated at: ${workspace.asRelativePath(targetPath)}`);
            } catch (err) {
                window.showErrorMessage(`Failed to write test file: ${err}`);
            }
            return true;
        }
    }
    return false;
}

export async function activate(context: ExtensionContext) {
    try {
        outputChannel = window.createOutputChannel('LISA AI Assistant');
        outputChannel.appendLine('LISA Extension activating...');
        console.log('LISA Extension activating...');

        // 1. Register Commands *Immediately*
        context.subscriptions.push(
            commands.registerCommand('lisa.execute', async () => {
                if (!client || !client.isRunning()) {
                    window.showErrorMessage('LISA Server is not running. Check Output channel.');
                    return;
                }

                // Capture Context (Selection or File)
                const editor = window.activeTextEditor;
                const contextData = {
                    selection: editor ? editor.document.getText(editor.selection) : '',
                    fileContent: editor ? editor.document.getText() : '',
                    languageId: editor ? editor.document.languageId : '',
                    uri: editor ? editor.document.uri.toString() : '',
                    ...(editor ? await getTestContext(editor) : {})
                };

                const command = await window.showInputBox({
                    prompt: 'What do you want LISA to do?',
                    placeHolder: 'e.g. "Generate tests for this code", "Add JSDoc", "Refactor to be cleaner"'
                });

                if (command) {
                    await window.withProgress({
                        location: ProgressLocation.Notification,
                        title: 'LISA is working...',
                        cancellable: false
                    }, async () => {
                        try {
                            const response = await client.sendRequest('lisa/execute', {
                                command,
                                context: contextData
                            });

                            // Handle Smart Actions (e.g. Generate Tests)
                            const res: any = response;
                            const handled = await handleTestGenerationResponse(res);
                            if (handled) return;

                            window.showInformationMessage(`LISA: ${JSON.stringify(response)}`);
                        } catch (error) {
                            outputChannel.appendLine(`LISA Error: ${error}`);
                            window.showErrorMessage(`LISA Error: ${error}`);
                        }
                    });
                }
            })
        );

        context.subscriptions.push(
            commands.registerCommand('lisa.setProvider', async () => {
                if (!client || !client.isRunning()) {
                    window.showErrorMessage('LISA Server is not running. Cannot update config.');
                    return;
                }
                const provider = await window.showQuickPick(['openai', 'groq', 'gemini', 'claude'], { placeHolder: 'Select AI Provider' });
                if (!provider) return;

                const apiKey = await window.showInputBox({ prompt: `Enter ${provider} API Key`, password: true });
                if (!apiKey) return;

                const models: Record<string, string[]> = {
                    'openai': ['gpt-5-2025-08-07', 'gpt-4o', 'o3'],
                    'groq': ['grok-4', 'llama-3.3-70b-versatile', 'mistral-saba-24b'],
                    'gemini': ['gemini-3-flash', 'gemini-3-pro', 'gemini-2.5-pro'],
                    'claude': ['claude-opus-4-5-20251101', 'claude-sonnet-4-5-20250929', 'claude-haiku-4-5-20251001']
                };

                const model = await window.showQuickPick(models[provider] || [], {
                    placeHolder: `Select ${provider} Model`,
                    canPickMany: false
                });
                if (!model) return;

                try {
                    // Save to persistent storage
                    await context.globalState.update('lisaProvider', provider);
                    await context.globalState.update('lisaModel', model);
                    await context.secrets.store('lisaApiKey', apiKey);

                    await client.sendRequest('lisa/updateConfig', { provider, apiKey, model });
                    window.showInformationMessage(`LISA updated to use ${provider}`);
                } catch (error) {
                    outputChannel.appendLine(`Failed to update LISA config: ${error}`);
                    window.showErrorMessage(`Failed to update LISA config: ${error}`);
                }
            })
        );

        // 4. Register Code Actions
        context.subscriptions.push(
            languages.registerCodeActionsProvider('*', new LisaCodeActionProvider(), {
                providedCodeActionKinds: LisaCodeActionProvider.providedCodeActionKinds
            })
        );

        // 5. Code Action Command Handler


        context.subscriptions.push(
            commands.registerCommand('lisa.openAgentManager', async () => {
                AgentPanel.render(context.extensionUri);

                // 1. Send context
                const editor = window.activeTextEditor;
                if (editor && AgentPanel.currentPanel) {
                    const fileName = editor.document.uri.path.split('/').pop();
                    const selection = editor.selection.isEmpty ? 'No selection' : `${editor.document.getText(editor.selection).length} chars selected`;
                    AgentPanel.currentPanel.postMessage({
                        command: 'updateContext',
                        text: `File: ${fileName}\nSelection: ${selection}`
                    });
                }

                // 2. Config
                if (AgentPanel.currentPanel) {
                    const savedProvider = context.globalState.get<string>('lisaProvider');
                    const savedModel = context.globalState.get<string>('lisaModel');
                    const savedApiKey = await context.secrets.get('lisaApiKey');

                    AgentPanel.currentPanel.postMessage({
                        command: 'loadConfig',
                        provider: savedProvider,
                        model: savedModel,
                        apiKey: savedApiKey
                    });
                }
            })
        );

        // 6. Project Context Command (Permission First)
        context.subscriptions.push(
            commands.registerCommand('lisa.readProject', async () => {
                // 1. Ask Permission
                const selection = await window.showInformationMessage(
                    "Allow LISA to scan and index your project files? This will read file names and structure to provide better context.",
                    "Yes", "No"
                );

                if (selection !== 'Yes') {
                    return;
                }

                // 2. Scan Project
                await window.withProgress({
                    location: ProgressLocation.Notification,
                    title: "LISA: Scanning Project...",
                    cancellable: false
                }, async () => {
                    const files = await workspace.findFiles('**/*', '{**/node_modules/**,**/.git/**,**/dist/**,**/out/**}');
                    const fileList = files.map(f => workspace.asRelativePath(f)).join('\n');

                    // 3. Send to Agent Panel
                    if (AgentPanel.currentPanel) {
                        window.showWarningMessage("LISA Agent Panel is not open.");
                    }
                });
            }),

            commands.registerCommand('lisa.runAction', async (args: any) => {
                if (!client || !client.isRunning()) {
                    window.showErrorMessage('LISA Server is not running.');
                    return;
                }

                const { action, file, selection, language } = args;
                // action: 'refactor' | 'addDocs' | 'generateTests'

                const contextData = {
                    selection: selection || '',
                    fileContent: file ? fs.readFileSync(file, 'utf-8') : '',
                    languageId: language || 'plaintext',
                    uri: file ? Uri.file(file).toString() : '',
                    // ...(file ? await getTestContext(...) : {}) // Maybe add test context if needed
                };

                let prompt = '';
                let actionOverride = '';

                if (action === 'refactor') {
                    prompt = `Refactor this code: ${selection}`;
                    actionOverride = 'refactor';
                } else if (action === 'addDocs') {
                    prompt = 'Add JSDoc documentation';
                    actionOverride = 'addJsDoc'; // Map 'addDocs' -> 'addJsDoc' for consistency
                } else if (action === 'generateTests') {
                    prompt = 'Generate unit tests for this code';
                    actionOverride = 'generateTests';
                }

                if (prompt) {
                    await window.withProgress({
                        location: ProgressLocation.Notification,
                        title: `LISA: Running ${actionOverride}...`,
                        cancellable: false
                    }, async () => {
                        try {
                            const response: any = await client.sendRequest('lisa/execute', {
                                command: prompt,
                                context: contextData
                            });

                            // Reuse logic from lisa.execute / message handler

                            // 1. Generate Tests
                            const res: any = response;
                            const handledTest = await handleTestGenerationResponse(res);
                            if (handledTest) return;

                            // 2. Inline Edits
                            if (actionOverride === 'refactor' || actionOverride === 'addJsDoc') {
                                if (res.success && res.data) {
                                    // Apply to editor
                                    const editor = window.visibleTextEditors.find(e => e.document.uri.fsPath === file) || window.activeTextEditor;
                                    if (editor) {
                                        const edit = new WorkspaceEdit();
                                        let replacementText = res.data;

                                        // Markdown stripping logic
                                        if (replacementText.startsWith('```') && replacementText.endsWith('```')) {
                                            const lines = replacementText.split('\n');
                                            replacementText = lines.slice(1, -1).join('\n');
                                        } else if (replacementText.startsWith('```')) {
                                            const lines = replacementText.split('\n');
                                            if (lines[lines.length - 1].trim() === '```') {
                                                replacementText = lines.slice(1, -1).join('\n');
                                            } else {
                                                replacementText = lines.slice(1).join('\n');
                                            }
                                        }

                                        if (!editor.selection.isEmpty) {
                                            edit.replace(editor.document.uri, editor.selection, replacementText);
                                        } else {
                                            edit.insert(editor.document.uri, editor.selection.active, replacementText);
                                        }

                                        await workspace.applyEdit(edit);
                                        window.showInformationMessage(`LISA: Applied ${actionOverride}`);
                                    }
                                } else {
                                    window.showErrorMessage(`LISA Failed: ${res.error || 'Unknown error'}`);
                                }
                            }

                        } catch (e) {
                            window.showErrorMessage(`LISA Error: ${e}`);
                        }
                    });
                }
            })
        );

        // Handle Messages from Webview
        AgentPanel.onMessage = async (message: any) => {
            if (message.command === 'saveConfig') {
                const { provider, model, apiKey } = message;
                try {
                    await context.globalState.update('lisaProvider', provider);
                    await context.globalState.update('lisaModel', model);
                    await context.secrets.store('lisaApiKey', apiKey);

                    if (client && client.isRunning()) {
                        await client.sendRequest('lisa/updateConfig', { provider, apiKey, model });
                    }
                    window.showInformationMessage(`LISA Config Saved: ${provider} / ${model}`);
                } catch (e) {
                    window.showErrorMessage(`Failed to save config: ${e}`);
                }
                return;
            }

            if (message.command === 'requestConfig') {
                // Resend config if requested (e.g. reload)
                const savedProvider = context.globalState.get<string>('lisaProvider');
                const savedModel = context.globalState.get<string>('lisaModel');
                const savedApiKey = await context.secrets.get('lisaApiKey');
                if (AgentPanel.currentPanel) {
                    AgentPanel.currentPanel.postMessage({
                        command: 'loadConfig',
                        provider: savedProvider,
                        model: savedModel,
                        apiKey: savedApiKey
                    });
                }
                return;
            }

            if (message.command === 'readProject') {
                commands.executeCommand('lisa.readProject');
                return;
            }

            if (message.command === 'getContext') {
                let editor = window.activeTextEditor;
                if (!editor && window.visibleTextEditors.length > 0) {
                    editor = window.visibleTextEditors[0];
                }

                if (editor && AgentPanel.currentPanel) {
                    const fileName = path.basename(editor.document.uri.fsPath);
                    const content = editor.document.getText();
                    const language = editor.document.languageId;

                    AgentPanel.currentPanel.postMessage({
                        command: 'setContext',
                        file: fileName,
                        content: content,
                        language: language
                    });
                } else {
                    window.showWarningMessage("No active editor found to attach.");
                }
                return;
            }

            if (message.command === 'runAgent') {
                const { agent, instruction } = message;

                // Get fresh context
                const editor = window.activeTextEditor;
                const contextData = {
                    selection: editor ? editor.document.getText(editor.selection) : '',
                    fileContent: editor ? editor.document.getText() : '',
                    languageId: editor ? editor.document.languageId : '',
                    uri: editor ? editor.document.uri.toString() : '',
                    ...(editor ? await getTestContext(editor) : {})
                };

                // Determine final command/action
                let lspCommand = instruction;
                let actionOverride = undefined;

                // If user attached context explicitly, use it TO AUGMENT the auto-context
                if (message.attachedContext) {
                    contextData.fileContent = message.attachedContext.content; // Prefer explicit
                    contextData.languageId = message.attachedContext.language || contextData.languageId;
                    // We can also add a flag if server needs to know it's explicit
                }

                if (agent === 'generateTests') {
                    lspCommand = "Generate tests";
                    actionOverride = 'generateTests';
                } else if (agent === 'addJsDoc') {
                    lspCommand = "Add JSDoc";
                    actionOverride = 'addJsDoc';
                } else if (agent === 'refactor') {
                    lspCommand = instruction; // Refactor uses instruction directly
                    actionOverride = 'refactor';
                }

                // Call Server
                if (client && client.isRunning()) {
                    try {
                        let prompt = instruction;
                        if (agent === 'generateTests') prompt = 'Generate unit tests for this code';
                        if (agent === 'addJsDoc') prompt = 'Add JSDoc documentation';
                        if (agent === 'refactor') prompt = `Refactor this code: ${instruction}`;

                        const response: any = await client.sendRequest('lisa/execute', {
                            command: prompt,
                            context: contextData
                        });

                        // Send Response back to Webview
                        if (AgentPanel.currentPanel) {
                            AgentPanel.currentPanel.postMessage({
                                command: 'agentResponse',
                                data: response
                            });
                        }

                        // Handle File Creation for Tests
                        const res: any = response;
                        const handledTest = await handleTestGenerationResponse(res);
                        if (handledTest) return;

                        // Handle Inline Edits (Refactor / JSDoc)
                        if (actionOverride === 'refactor' || actionOverride === 'addJsDoc') {
                            if (res.success && res.data) {
                                const editor = window.activeTextEditor;
                                if (editor) {
                                    const edit = new WorkspaceEdit();
                                    // Assuming the response data IS the new code or a diff.
                                    // For simplicity, if it's a full file replacement or block replacement:
                                    // Ideally LSP returns a TextEdit, but if it returns string:

                                    // If we sent selection, replace selection. If no selection, replace file?
                                    // contextData.selection

                                    let replacementText = res.data;
                                    // Strip markdown code blocks if present
                                    if (replacementText.startsWith('```') && replacementText.endsWith('```')) {
                                        const lines = replacementText.split('\n');
                                        replacementText = lines.slice(1, -1).join('\n');
                                    } else if (replacementText.startsWith('```')) { // Sometimes ends with ```typescript
                                        const lines = replacementText.split('\n');
                                        // Find the last line with ```
                                        if (lines[lines.length - 1].trim() === '```') {
                                            replacementText = lines.slice(1, -1).join('\n');
                                        } else {
                                            replacementText = lines.slice(1).join('\n');
                                        }
                                    }

                                    if (!editor.selection.isEmpty) {
                                        edit.replace(editor.document.uri, editor.selection, replacementText);
                                    } else {
                                        // Append or replace? For Add JSDoc to specific function, it's hard without range. 
                                        // For now, let's assume it replaces the whole file if no selection? Or inserts at cursor?
                                        // Safer: Insert at cursor
                                        edit.insert(editor.document.uri, editor.selection.active, replacementText);
                                    }

                                    await workspace.applyEdit(edit);
                                    window.showInformationMessage(`LISA: Applied ${actionOverride}`);
                                    return; // Do NOT send chat response
                                }
                            }
                        }

                        // Send Response back to Webview (Default behavior)
                        if (AgentPanel.currentPanel) {
                            AgentPanel.currentPanel.postMessage({
                                command: 'agentResponse',
                                data: response
                            });
                        }

                    } catch (e) {
                        if (AgentPanel.currentPanel) {
                            AgentPanel.currentPanel.postMessage({
                                command: 'agentResponse',
                                data: { success: false, error: String(e) }
                            });
                        }
                        window.showErrorMessage(`Agent Failed: ${e}`);
                    }
                } else {
                    window.showErrorMessage("LISA Server is not running.");
                }
            }
        };

        // 2. Start Request - Connect to Language Server
        try {
            // serverModule logic...
            let serverModule = context.asAbsolutePath(path.join('server', 'server.js')); // Prod path
            const devServerModule = context.asAbsolutePath(path.join('..', 'dist', 'server.js')); // Dev path

            if (fs.existsSync(devServerModule)) {
                outputChannel.appendLine(`Found dev server: ${devServerModule}`);
                serverModule = devServerModule;
            } else if (!fs.existsSync(serverModule)) {
                outputChannel.appendLine(`Server not found at ${serverModule} or ${devServerModule}`);
                window.showErrorMessage(`LISA Server not found. Extension will not function fully.`);
                return;
            }

            outputChannel.appendLine(`Starting LISA server from: ${serverModule}`);

            const serverOptions: ServerOptions = {
                run: { module: serverModule, transport: TransportKind.stdio },
                debug: {
                    module: serverModule,
                    transport: TransportKind.stdio,
                    options: { execArgv: ['--nolazy', '--inspect=6009'] }
                }
            };

            const clientOptions: LanguageClientOptions = {
                documentSelector: [
                    { scheme: 'file', language: 'typescript' },
                    { scheme: 'file', language: 'javascript' },
                    { scheme: 'file', language: 'markdown' }
                ],
                synchronize: {
                    fileEvents: workspace.createFileSystemWatcher('**/.clientrc')
                },
                outputChannel: outputChannel
            };

            client = new LanguageClient('lisaLsp', 'LISA AI Assistant', serverOptions, clientOptions);
            await client.start();
            outputChannel.appendLine('LISA AI Assistant client started');

            // Restore saved configuration
            const savedProvider = context.globalState.get<string>('lisaProvider');
            const savedModel = context.globalState.get<string>('lisaModel');
            const savedApiKey = await context.secrets.get('lisaApiKey');

            if (savedProvider && savedModel && savedApiKey) {
                try {
                    await client.sendRequest('lisa/updateConfig', {
                        provider: savedProvider,
                        apiKey: savedApiKey,
                        model: savedModel
                    });
                    outputChannel.appendLine(`Restored LISA config for ${savedProvider}`);
                } catch (err) {
                    outputChannel.appendLine(`Failed to restore LISA config: ${err}`);
                }
            } else {
                outputChannel.appendLine('No saved LISA config found. User must configure.');
            }

        } catch (err) {
            outputChannel.appendLine(`Failed to activate LISA server: ${err}`);
            window.showErrorMessage(`LISA Extension Activation Error: ${err}`);
        }
    } catch (e) {
        console.error('LISA FATAL ERROR:', e);
        window.showErrorMessage(`LISA Extension failed to activate: ${e}`);
    }
}

export function deactivate(): Thenable<void> | undefined {
    if (!client) {
        return undefined;
    }
    return client.stop();
}
