"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0';
const node_1 = require("vscode-languageserver/node");
const vscode_languageserver_textdocument_1 = require("vscode-languageserver-textdocument");
// eslint-disable-next-line @typescript-eslint/no-var-requires
const JiraClient = require('jira-client'); // Using require to avoid type issues
const fs = __importStar(require("fs"));
const path = __importStar(require("path"));
const logFile = path.resolve(__dirname, '../server.log');
function debugLog(msg) {
    fs.appendFileSync(logFile, `[${new Date().toISOString()}] ${msg}\n`);
}
debugLog('Server process started');
// Load environment variables
const config_1 = require("./config");
const node_2 = require("@gitbeaker/node");
const openai_1 = __importDefault(require("openai"));
const generative_ai_1 = require("@google/generative-ai");
const sdk_1 = __importDefault(require("@anthropic-ai/sdk"));
// Initialize API clients
const gitlab = new node_2.Gitlab({ token: config_1.GITLAB_TOKEN, host: config_1.GITLAB_HOST });
const jira = new JiraClient({
    protocol: 'https',
    host: config_1.JIRA_HOST,
    username: config_1.JIRA_USERNAME,
    password: config_1.JIRA_API_TOKEN,
    apiVersion: '2',
    strictSSL: false
});
const openai = new openai_1.default({ apiKey: config_1.OPENAI_API_KEY });
let currentConfig = {
    provider: 'openai',
    apiKey: config_1.OPENAI_API_KEY,
    model: 'gpt-4-turbo-preview'
};
async function callAi(messages, responseFormat) {
    const { provider, apiKey, model } = currentConfig;
    if (provider === 'openai' || provider === 'groq') {
        const baseURL = provider === 'groq' ? 'https://api.groq.com/openai/v1' : undefined;
        const client = new openai_1.default({ apiKey, baseURL });
        const completion = await client.chat.completions.create({
            messages,
            model,
            response_format: responseFormat
        });
        return completion.choices[0].message.content || '';
    }
    if (provider === 'gemini') {
        const genAI = new generative_ai_1.GoogleGenerativeAI(apiKey);
        const genModel = genAI.getGenerativeModel({ model: model || 'gemini-1.5-flash' });
        const systemPrompt = messages.find(m => m.role === 'system')?.content || '';
        const userPrompt = messages.find(m => m.role === 'user')?.content || '';
        const fullPrompt = systemPrompt ? `${systemPrompt}\n\nUser: ${userPrompt}` : userPrompt;
        const result = await genModel.generateContent(fullPrompt);
        return result.response.text();
    }
    if (provider === 'claude') {
        const anthropic = new sdk_1.default({ apiKey });
        const system = messages.find(m => m.role === 'system')?.content;
        const userMessages = messages.filter(m => m.role !== 'system').map(m => ({
            role: m.role === 'user' ? 'user' : 'assistant',
            content: m.content
        }));
        const response = await anthropic.messages.create({
            model,
            system,
            messages: userMessages,
            max_tokens: 4096
        });
        // Handle different content types in Anthropic response
        const textContent = response.content.find(c => c.type === 'text');
        return textContent && 'text' in textContent ? textContent.text : '';
    }
    throw new Error(`Unsupported provider: ${provider}`);
}
// Create a connection for the server. The client (IDE) will connect to this.
const connection = (0, node_1.createConnection)(node_1.ProposedFeatures.all); // No watchDog needed with node version
// Create a simple text document manager.
const documents = new node_1.TextDocuments(vscode_languageserver_textdocument_1.TextDocument);
documents.listen(connection);
// The server capabilities are sent back to the client during the initialize request.
connection.onInitialize((params) => {
    debugLog('Received initialize request');
    return {
        capabilities: {
            textDocumentSync: node_1.TextDocumentSyncKind.Incremental,
        }
    };
});
// Handle the initialized notification from client
connection.onInitialized(() => {
    debugLog('Received initialized notification');
    connection.console.log('Client initialized');
});
// Update configuration (API Key, Model, Provider)
connection.onRequest('lisa/updateConfig', (config) => {
    currentConfig = { ...currentConfig, ...config };
    debugLog(`Config updated: ${JSON.stringify({ ...currentConfig, apiKey: '***' })}`);
    return { success: true };
});
/**
 * TOOL HELPERS
 */
async function performCodeReview(params) {
    const { uri, mrUrl, branchName } = params;
    debugLog(`Review logic: URI=${uri}, URL=${mrUrl}, Branch=${branchName}`);
    let projectId = process.env.GITLAB_PROJECT_ID;
    let mrIid = process.env.GITLAB_MR_IID;
    if (mrUrl) {
        const urlPath = mrUrl.replace(/^https?:\/\/[^\/]+\//, '');
        const match = urlPath.match(/(.+)\/-\/merge_requests\/(\d+)/);
        if (match) {
            projectId = match[1];
            mrIid = match[2];
        }
        else {
            const simpleMatch = mrUrl.match(/\/merge_requests\/(\d+)/);
            if (simpleMatch)
                mrIid = simpleMatch[1];
        }
    }
    else if (branchName && projectId) {
        const mrs = await gitlab.MergeRequests.all({ projectId, sourceBranch: branchName, state: 'opened' });
        if (mrs.length > 0)
            mrIid = mrs[0].iid;
    }
    if (!projectId || !mrIid) {
        throw new Error('Unable to identify Merge Request. Please provide a full MR URL or branch name.');
    }
    const mrChanges = await gitlab.MergeRequests.changes(projectId, Number(mrIid));
    const diffs = (mrChanges.changes || []).map((c) => `File: ${c.new_path}\nDiff:\n${c.diff}`).join('\n\n');
    if (!diffs || diffs.trim() === '')
        return { success: true, message: 'No changes found to review.' };
    const reviewComment = await callAi([
        {
            role: 'system',
            content: 'Review code changes like a senior dev: concise, punchy, and high-impact. Focus ONLY on critical bugs, security risks, or major performance wins. Keep it "miniskirt" style: short enough to be interesting, long enough to cover the context.'
        },
        { role: 'user', content: diffs }
    ]);
    await gitlab.MergeRequestNotes.create(projectId, Number(mrIid), `### 🤖 AI Code Review\n\n${reviewComment}`);
    return { success: true, message: `AI review posted for MR ${mrIid}.` };
}
async function createJiraIssue(payload) {
    const issue = await jira.addNewIssue({
        fields: {
            project: { key: payload.projectKey },
            summary: payload.summary,
            description: payload.description,
            issuetype: { name: 'Task' }
        }
    });
    return { success: true, issueKey: issue.key };
}
async function createGitLabMR(payload) {
    const projectId = process.env.GITLAB_PROJECT_ID;
    if (!projectId)
        throw new Error('GitLab project ID not set');
    const mr = await gitlab.MergeRequests.create(projectId, payload.sourceBranch, payload.targetBranch, payload.title);
    return { success: true, mrId: mr.iid };
}
/**
 * REQUEST HANDLERS
 */
// Agentic Execute Handler: Understands natural language commands
connection.onRequest('lisa/execute', async (command) => {
    debugLog(`Agentic execute: ${command}`);
    try {
        const interpretation = await callAi([
            {
                role: 'system',
                content: `You are LISA (Localization & Intelligence Support Assistant), a developer tool. 
                Map the user command to one of these actions:
                1. "review": { "mrUrl": "URL", "branchName": "branch" }
                2. "jiraCreate": { "summary": "text", "description": "text", "projectKey": "key" }
                3. "gitlabMR": { "sourceBranch": "text", "targetBranch": "text", "title": "text" }
                
                Return ONLY a JSON object with "action" and "params". 
                If "review" and a URL is found, put it in "mrUrl". 
                If no obvious action, return { "action": "unknown", "params": {} }.`
            },
            { role: 'user', content: command }
        ], { type: "json_object" });
        const res = JSON.parse(interpretation || '{}');
        debugLog(`Interpreted: ${JSON.stringify(res)}`);
        switch (res.action) {
            case 'review': return await performCodeReview(res.params);
            case 'jiraCreate':
                const jiraParams = {
                    ...res.params,
                    projectKey: res.params.projectKey || process.env.JIRA_PROJECT_KEY || 'DOVS'
                };
                return await createJiraIssue(jiraParams);
            case 'gitlabMR': return await createGitLabMR(res.params);
            default: throw new Error(`I am LISA. I didn't understand that command. Try "review [MR URL]" or "create jira ticket for [task]".`);
        }
    }
    catch (err) {
        connection.console.error(`Execute error: ${err}`);
        return { success: false, error: err instanceof Error ? err.message : String(err) };
    }
});
connection.onRequest('lisa/review', (params) => performCodeReview(typeof params === 'string' ? { uri: params } : params));
connection.onRequest('lisa/jiraCreate', (params) => createJiraIssue(params));
connection.onRequest('lisa/gitlabMR', (params) => createGitLabMR(params));
// Listen on the standard input/output streams.
connection.listen();
