import * as net from 'net';
import {
    createMessageConnection,
    StreamMessageReader,
    StreamMessageWriter
} from 'vscode-jsonrpc/node';

// Start the LSP server as a child process (using bun)
import { spawn } from 'child_process';

const serverProcess = spawn('node', ['dist/server.js', '--stdio'], {
    cwd: process.cwd(),
    stdio: ['pipe', 'pipe', 'inherit']
});

const connection = createMessageConnection(
    new StreamMessageReader(serverProcess.stdout),
    new StreamMessageWriter(serverProcess.stdin)
);

connection.listen();

async function test() {
    // Initialize the LSP connection
    const initRes = await connection.sendRequest('initialize', {
        processId: null,
        rootUri: null,
        capabilities: {}
    });
    console.log('Initialize response:', initRes);
    // Notify server that client is initialized
    connection.sendNotification('initialized', {});

    // 1. Update Config to use Claude
    console.log('Updating config to use Claude...');
    await connection.sendRequest('mcp/updateConfig', {
        provider: 'claude',
        apiKey: process.env.ANTHROPIC_API_KEY,
        model: 'claude-3-5-sonnet-20240620'
    });

    // 2. Agentic execute command with Claude
    console.log('Testing mcp/execute with Claude: "review MR 1307"...');
    const agentRes = await connection.sendRequest('mcp/execute', 'review this ticket https://gitlab.com/inspire1/digital-platform/mobile/wl/mobile/-/merge_requests/1307');
    console.log('Agentic response:', agentRes);

    // 2. JIRA issue creation
    console.log('Sending mcp/jiraCreate request...');
    const jiraRes = await connection.sendRequest('mcp/jiraCreate', {
        summary: 'Demo issue from LSP client',
        description: 'Automatically created by MCP LSP client script.',
        projectKey: process.env.JIRA_PROJECT_KEY || 'PROJ'
    });
    console.log('JIRA response:', jiraRes);

    // 3. GitLab MR creation
    console.log('Sending mcp/gitlabMR request...');
    const mrRes = await connection.sendRequest('mcp/gitlabMR', {
        sourceBranch: process.env.GITLAB_SOURCE_BRANCH || 'feature/demo',
        targetBranch: process.env.GITLAB_TARGET_BRANCH || 'main',
        title: 'Demo MR from LSP client'
    });
    console.log('GitLab MR response:', mrRes);

    // Close connection and server
    connection.dispose();
    serverProcess.kill();
}

test().catch(err => {
    console.error('Error during test:', err);
    connection.dispose();
    serverProcess.kill();
});
