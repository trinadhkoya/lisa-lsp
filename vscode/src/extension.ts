import * as path from 'path';
import * as fs from 'fs';
import { ExtensionContext, commands, window, workspace, ProgressLocation, OutputChannel } from 'vscode';
import { LanguageClient, LanguageClientOptions, ServerOptions, TransportKind } from 'vscode-languageclient/node';

let client: LanguageClient;
let outputChannel: OutputChannel;

export function activate(context: ExtensionContext) {
    outputChannel = window.createOutputChannel('LISA AI Assistant');

    // The server is implemented in node
    // Path to the server's main file (pointing to the project's dist/server.js)
    let serverModule = context.asAbsolutePath(path.join('..', 'dist', 'server.js'));

    // Check if the server module exists, if not, try alternative path
    if (!fs.existsSync(serverModule)) {
        serverModule = context.asAbsolutePath(path.join('out', 'server.js'));
    }

    if (!fs.existsSync(serverModule)) {
        window.showErrorMessage(`LISA LSP Server not found at ${serverModule}. Please ensure the server is built.`);
        return;
    }

    outputChannel.appendLine(`Starting LISA server from: ${serverModule}`);

    // Server options
    const serverOptions: ServerOptions = {
        run: { module: serverModule, transport: TransportKind.stdio },
        debug: {
            module: serverModule,
            transport: TransportKind.stdio,
            options: { execArgv: ['--nolazy', '--inspect=6009'] }
        }
    };

    // Client options
    const clientOptions: LanguageClientOptions = {
        documentSelector: [
            { scheme: 'file', language: 'typescript' },
            { scheme: 'file', language: 'javascript' },
            { scheme: 'file', language: 'markdown' }
        ],
        synchronize: {
            // Notify the server about file changes to '.clientrc files contained in the workspace
            fileEvents: workspace.createFileSystemWatcher('**/.clientrc')
        },
        outputChannel: outputChannel
    };

    // Create and start the language client
    client = new LanguageClient('lisaLsp', 'LISA AI Assistant', serverOptions, clientOptions);
    client.start();

    outputChannel.appendLine('LISA AI Assistant started');

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
                    try {
                        const response = await client.sendRequest('lisa/execute', command);
                        window.showInformationMessage(`LISA: ${JSON.stringify(response)}`);
                    } catch (error) {
                        outputChannel.appendLine(`LISA Error: ${error}`);
                        window.showErrorMessage(`LISA Error: ${error}`);
                    }
                });
            }
        })
    );

    // Command to update AI Provider Settings
    context.subscriptions.push(
        commands.registerCommand('lisa.setProvider', async () => {
            const provider = await window.showQuickPick(['openai', 'groq', 'gemini', 'claude'], { placeHolder: 'Select AI Provider' });
            if (!provider) return;

            const apiKey = await window.showInputBox({ prompt: `Enter ${provider} API Key`, password: true });
            if (!apiKey) return;

            const model = await window.showInputBox({
                prompt: 'Enter Model Name (e.g. gpt-4, claude-3-opus, gemini-pro)',
                value: provider === 'openai' ? 'gpt-4' : ''
            });
            if (!model) return;

            try {
                await client.sendRequest('lisa/updateConfig', { provider, apiKey, model });
                window.showInformationMessage(`LISA updated to use ${provider}`);
            } catch (error) {
                outputChannel.appendLine(`Failed to update LISA config: ${error}`);
                window.showErrorMessage(`Failed to update LISA config: ${error}`);
            }
        })
    );
}

export function deactivate(): Thenable<void> | undefined {
    if (!client) {
        return undefined;
    }
    return client.stop();
}
