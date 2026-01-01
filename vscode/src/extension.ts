import * as path from 'path';
import * as fs from 'fs';
import { ExtensionContext, commands, window, workspace, ProgressLocation, OutputChannel, TextEditor, Uri } from 'vscode';
import { LanguageClient, LanguageClientOptions, ServerOptions, TransportKind } from 'vscode-languageclient/node';
import { AgentPanel } from './panels/AgentPanel';

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

    // 1. Check for standard test folders or sibling test files
    const testPatterns = [
        activeUri.fsPath.replace(/\.(ts|js|jsx|tsx)$/, '.test.$1'),
        activeUri.fsPath.replace(/\.(ts|js|jsx|tsx)$/, '.spec.$1'),
        path.join(dir, '__tests__', path.basename(activeUri.fsPath).replace(/\.(ts|js|jsx|tsx)$/, '.test.$1')),
        path.join(dir, 'tests', path.basename(activeUri.fsPath).replace(/\.(ts|js|jsx|tsx)$/, '.test.$1'))
    ];

    // Try to find ANY test file in the directory to serve as a pattern if the specific test doesn't exist
    if (!testPatterns.some(p => fs.existsSync(p))) {
        try {
            const files = fs.readdirSync(dir);
            const anyTest = files.find(f => f.includes('.test.') || f.includes('.spec.'));
            if (anyTest) {
                testPatterns.push(path.join(dir, anyTest));
            } else {
                // Check subdir
                if (fs.existsSync(path.join(dir, '__tests__'))) {
                    const subFiles = fs.readdirSync(path.join(dir, '__tests__'));
                    if (subFiles.length > 0) testPatterns.push(path.join(dir, '__tests__', subFiles[0]));
                }
            }
        } catch (e) { }
    }

    for (const p of testPatterns) {
        if (fs.existsSync(p)) {
            existingTestContent = fs.readFileSync(p, 'utf-8');
            foundPath = p;
            break;
        }
    }

    // Structure Info for Imports
    const sourceRelative = workspace.asRelativePath(activeUri);
    const testRelative = foundPath ? workspace.asRelativePath(foundPath) : 'Unknown (New File)';
    const fileStructureInfo = `Source File: ${sourceRelative}\nTarget Test File Location: ${testRelative !== 'Unknown (New File)' ? testRelative : 'Should be placed alongside source or in __tests__ folder'}`;

    return { existingTestContent, fileStructureInfo };
}

// Helper to handle test generation response
async function handleTestGenerationResponse(res: any) {
    if (res.success && res.action === 'generateTests' && res.data) {
        const activeUri = window.activeTextEditor?.document.uri;
        if (activeUri) {
            // Determine Test File Path - Be Smarter about Location
            const wsFolder = workspace.getWorkspaceFolder(activeUri);
            let testPath = activeUri.fsPath.replace(/\.(ts|js|jsx|tsx)$/, '.test.$1'); // Default: sibling

            // Check for standard test folders
            if (wsFolder) {
                const fileDir = path.dirname(activeUri.fsPath);
                const possibleDirs = [
                    path.join(fileDir, '__tests__'),
                    path.join(fileDir, 'tests'),
                    path.join(wsFolder.uri.fsPath, '__tests__'),
                    path.join(wsFolder.uri.fsPath, 'tests')
                ];

                for (const dir of possibleDirs) {
                    if (fs.existsSync(dir)) {
                        const fileName = path.basename(activeUri.fsPath).replace(/\.(ts|js|jsx|tsx)$/, '.test.$1');
                        testPath = path.join(dir, fileName);
                        break;
                    }
                }
            }

            // Write File
            try {
                fs.mkdirSync(path.dirname(testPath), { recursive: true });
                fs.writeFileSync(testPath, res.data);

                // Open Document
                const doc = await workspace.openTextDocument(testPath);
                await window.showTextDocument(doc);
                window.showInformationMessage(`Tests generated at: ${workspace.asRelativePath(testPath)}`);
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
                    'openai': ['gpt-4', 'gpt-4-turbo', 'gpt-3.5-turbo'],
                    'groq': ['llama2-70b-4096', 'mixtral-8x7b-32768', 'gemma-7b-it'],
                    'gemini': ['gemini-pro', 'gemini-1.5-flash', 'gemini-1.5-pro'],
                    'claude': ['claude-3-opus-20240229', 'claude-3-sonnet-20240229', 'claude-3-haiku-20240307']
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


        // 3. Agent Manager UI
        // Import AgentPanel (ensure imports are added at top via VS Code auto-import or manual add)
        // Manual import injection needed if not present, but for replace tool strictness, I'll add logic here.

        context.subscriptions.push(
            commands.registerCommand('lisa.openAgentManager', async () => {
                AgentPanel.render(context.extensionUri);

                // 1. Send context (Current File/Selection)
                const editor = window.activeTextEditor;
                if (editor && AgentPanel.currentPanel) {
                    const fileName = editor.document.uri.path.split('/').pop();
                    const selection = editor.selection.isEmpty ? 'No selection' : `${editor.document.getText(editor.selection).length} chars selected`;
                    AgentPanel.currentPanel.postMessage({
                        command: 'updateContext',
                        text: `File: ${fileName}\nSelection: ${selection}`
                    });
                }

                // 2. Send Saved Configuration (Provider, Model, API Key)
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
                        const handled = await handleTestGenerationResponse(res);
                        if (handled) return;

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
