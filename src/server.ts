import {
    createConnection,
    ProposedFeatures,
    InitializeParams,
    InitializeResult,
    TextDocuments,
    TextDocumentSyncKind,
    DidChangeConfigurationNotification,
    DidChangeConfigurationParams,
    TextDocument
} from 'vscode-languageserver';

import { TextDocument as TextDoc } from 'vscode-languageserver-textdocument';

// Create a connection for the server. The client (IDE) will connect to this.
const connection = createConnection(ProposedFeatures.all);

// Create a simple text document manager.
const documents: TextDocuments<TextDoc> = new TextDocuments(TextDoc);

documents.listen(connection);

// The server capabilities are sent back to the client during the initialize request.
connection.onInitialize((params: InitializeParams): InitializeResult => {
    return {
        capabilities: {
            textDocumentSync: TextDocumentSyncKind.Incremental,
            // Additional capabilities (e.g., code actions) will be added later.
        }
    };
});

// Example of handling a custom request for code review.
connection.onRequest('mcp/review', async (uri: string) => {
    // Placeholder: In a real implementation we would call GitLab API.
    connection.console.log(`Received review request for ${uri}`);
    return { success: true, message: 'Review triggered (mock)' };
});

// Example of handling a custom request for creating a JIRA issue.
connection.onRequest('mcp/jiraCreate', async (payload: { summary: string; description: string; projectKey: string }) => {
    connection.console.log(`Creating JIRA issue: ${payload.summary}`);
    return { success: true, issueKey: 'PROJ-123' };
});

// Example of handling a custom request for creating a GitLab MR.
connection.onRequest('mcp/gitlabMR', async (payload: { sourceBranch: string; targetBranch: string; title: string }) => {
    connection.console.log(`Creating MR from ${payload.sourceBranch} to ${payload.targetBranch}`);
    return { success: true, mrId: 42 };
});

// Listen on the standard input/output streams.
connection.listen();
