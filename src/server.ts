process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0';
import {
    createConnection,
    ProposedFeatures,
    InitializeParams,
    InitializeResult,
    TextDocuments,
    TextDocumentSyncKind
} from 'vscode-languageserver/node';

import { TextDocument as TextDoc } from 'vscode-languageserver-textdocument';

// eslint-disable-next-line @typescript-eslint/no-var-requires
const JiraClient = require('jira-client'); // Using require to avoid type issues
import * as fs from 'fs';
import * as path from 'path';

const logFile = path.resolve(__dirname, '../server.log');
function debugLog(msg: string) {
    fs.appendFileSync(logFile, `[${new Date().toISOString()}] ${msg}\n`);
}

debugLog('Server process started');

// Load environment variables
import { GITLAB_TOKEN, GITLAB_HOST, JIRA_HOST, JIRA_USERNAME, JIRA_API_TOKEN, OPENAI_API_KEY, GEMINI_API_KEY, GROQ_API_KEY, ANTHROPIC_API_KEY } from './config';
import { Gitlab } from '@gitbeaker/node';
import OpenAI from 'openai';
import { GoogleGenerativeAI } from '@google/generative-ai';
import Anthropic from '@anthropic-ai/sdk';

// Initialize API clients
const gitlab = new Gitlab({ token: GITLAB_TOKEN, host: GITLAB_HOST });
const jira = new JiraClient({
    protocol: 'https',
    host: JIRA_HOST,
    username: JIRA_USERNAME,
    password: JIRA_API_TOKEN,
    apiVersion: '2',
    strictSSL: false
});
const openai = new OpenAI({ apiKey: OPENAI_API_KEY });

/**
 * CONFIGURATION STATE
 */
interface McpConfig {
    provider: 'openai' | 'groq' | 'gemini' | 'claude';
    apiKey: string;
    model: string;
}

let currentConfig: McpConfig = {
    provider: 'openai',
    apiKey: OPENAI_API_KEY,
    model: 'gpt-4-turbo-preview'
};

async function callAi(messages: any[], responseFormat?: any) {
    const { provider, apiKey, model } = currentConfig;

    if (provider === 'openai' || provider === 'groq') {
        const baseURL = provider === 'groq' ? 'https://api.groq.com/openai/v1' : undefined;
        const client = new OpenAI({ apiKey, baseURL });
        const completion = await client.chat.completions.create({
            messages,
            model,
            response_format: responseFormat
        });
        return completion.choices[0].message.content || '';
    }

    if (provider === 'gemini') {
        const genAI = new GoogleGenerativeAI(apiKey);
        const genModel = genAI.getGenerativeModel({ model: model || 'gemini-1.5-flash' });
        const systemPrompt = messages.find(m => m.role === 'system')?.content || '';
        const userPrompt = messages.find(m => m.role === 'user')?.content || '';
        const fullPrompt = systemPrompt ? `${systemPrompt}\n\nUser: ${userPrompt}` : userPrompt;
        const result = await genModel.generateContent(fullPrompt);
        return result.response.text();
    }

    if (provider === 'claude') {
        const anthropic = new Anthropic({ apiKey });
        const system = messages.find(m => m.role === 'system')?.content;
        const userMessages = messages.filter(m => m.role !== 'system').map(m => ({
            role: m.role === 'user' ? 'user' as const : 'assistant' as const,
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
const connection = createConnection(ProposedFeatures.all); // No watchDog needed with node version

// Create a simple text document manager.
const documents: TextDocuments<TextDoc> = new TextDocuments(TextDoc);

documents.listen(connection);

// The server capabilities are sent back to the client during the initialize request.
connection.onInitialize((params: InitializeParams): InitializeResult => {
    debugLog('Received initialize request');
    return {
        capabilities: {
            textDocumentSync: TextDocumentSyncKind.Incremental,
        }
    };
});

// Handle the initialized notification from client
connection.onInitialized(() => {
    debugLog('Received initialized notification');
    connection.console.log('Client initialized');
});

// Update configuration (API Key, Model, Provider)
connection.onRequest('lisa/updateConfig', (config: McpConfig) => {
    currentConfig = { ...currentConfig, ...config };
    debugLog(`Config updated: ${JSON.stringify({ ...currentConfig, apiKey: '***' })}`);
    return { success: true };
});

/**
 * TOOL HELPERS
 */

async function performCodeReview(params: { uri?: string; mrUrl?: string; branchName?: string }) {
    const { uri, mrUrl, branchName } = params;
    debugLog(`Review logic: URI=${uri}, URL=${mrUrl}, Branch=${branchName}`);

    let projectId: string | number | undefined = process.env.GITLAB_PROJECT_ID;
    let mrIid: string | number | undefined = process.env.GITLAB_MR_IID;

    if (mrUrl) {
        const urlPath = mrUrl.replace(/^https?:\/\/[^\/]+\//, '');
        const match = urlPath.match(/(.+)\/-\/merge_requests\/(\d+)/);
        if (match) {
            projectId = match[1];
            mrIid = match[2];
        } else {
            const simpleMatch = mrUrl.match(/\/merge_requests\/(\d+)/);
            if (simpleMatch) mrIid = simpleMatch[1];
        }
    } else if (branchName && projectId) {
        const mrs = await gitlab.MergeRequests.all({ projectId, sourceBranch: branchName, state: 'opened' });
        if (mrs.length > 0) mrIid = mrs[0].iid;
    }

    if (!projectId || !mrIid) {
        throw new Error('Unable to identify Merge Request. Please provide a full MR URL or branch name.');
    }

    const mrChanges = await gitlab.MergeRequests.changes(projectId, Number(mrIid));
    const diffs = (mrChanges.changes || []).map((c: any) => `File: ${c.new_path}\nDiff:\n${c.diff}`).join('\n\n');

    if (!diffs || diffs.trim() === '') return { success: true, message: 'No changes found to review.' };

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

async function createJiraIssue(payload: { summary: string; description: string; projectKey: string }) {
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

async function createGitLabMR(payload: { sourceBranch: string; targetBranch: string; title: string }) {
    const projectId = process.env.GITLAB_PROJECT_ID;
    if (!projectId) throw new Error('GitLab project ID not set');
    const mr = await gitlab.MergeRequests.create(projectId, payload.sourceBranch, payload.targetBranch, payload.title);
    return { success: true, mrId: mr.iid };
}

/**
 * REQUEST HANDLERS
 */

// Agentic Execute Handler: Understands natural language commands
// Helper to extract code from context
function getCodeContext(context: any): string {
    return context?.selection || context?.fileContent || '';
}

async function generateTests(context: any) {
    const code = getCodeContext(context);
    if (!code) return { success: false, error: 'No code selected or file is empty.' };

    const existingTestContent = context.existingTestContent || '';
    const fileStructureInfo = context.fileStructureInfo || '';

    const systemPrompt = `You are a QA automation expert. Generate comprehensive unit tests for the provided code.
    
    RULES:
    1. Use the testing framework appropriate for the language (e.g., Jest/Mocha for JS/TS, JUnit for Java).
    2. Return ONLY the code.
    3. ${existingTestContent ? `Follow the coding style, imports, and structure of this EXISTING test file found in the project:\n\n${existingTestContent}\n\n` : ''}
    4. ${fileStructureInfo ? `File Structure Context:\n${fileStructureInfo}` : ''}
    5. Ensure imports are correct based on the file structure provided. Do not use placeholders like "path/to/module" if you can infer the relative path.`;

    const tests = await callAi([
        { role: 'system', content: systemPrompt },
        { role: 'user', content: code }
    ]);
    return { success: true, message: 'Tests generated.', data: tests, action: 'generateTests' };
}

async function addJsDoc(context: any) {
    const code = getCodeContext(context);
    if (!code) return { success: false, error: 'No code selected.' };

    const documentedCode = await callAi([
        { role: 'system', content: 'You are a documentation expert. Add JSDoc/Docstrings to the provided code. Document all parameters, return types, and exceptions. Return the FULL code with comments added.' },
        { role: 'user', content: code }
    ]);
    return { success: true, message: 'Documentation added.', data: documentedCode, action: 'addJsDoc' };
}

async function refactorCode(context: any, instruction: string) {
    const code = getCodeContext(context);
    if (!code) return { success: false, error: 'No code selected.' };

    const refactored = await callAi([
        { role: 'system', content: `Refactor the code based on the user's instruction. Maintain logic but improve structure/performance/readability. Return ONLY the code.` },
        { role: 'user', content: `Code:\n${code}\n\nInstruction: ${instruction}` }
    ]);
    return { success: true, message: 'Code refactored.', data: refactored, action: 'refactor' };
}

// Agentic Execute Handler: Understands natural language commands
connection.onRequest('lisa/execute', async (params: any) => {
    // Support both legacy string command and new object format
    const command = typeof params === 'string' ? params : params.command;
    const context = typeof params === 'string' ? {} : params.context;

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
                4. "generateTests": {}
                5. "addJsDoc": {}
                6. "refactor": { "instruction": "user instruction" }
                
                Return ONLY a raw JSON object with "action" and "params". 
                Do NOT use markdown code blocks or backticks.
                If "review" and a URL is found, put it in "mrUrl". 
                If no obvious action, return { "action": "unknown", "params": {} }.`
            },
            { role: 'user', content: command }
        ]); // Removed strict json_object mode for compatibility

        // Clean up potential markdown code blocks if the model adds them
        const cleanJson = (interpretation || '{}').replace(/```json/g, '').replace(/```/g, '').trim();
        const res = JSON.parse(cleanJson);
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
            case 'generateTests': return await generateTests(context);
            case 'addJsDoc': return await addJsDoc(context);
            case 'refactor': return await refactorCode(context, res.params.instruction || command);
            default: return await callAi([{ role: 'user', content: command }]); // Fallback to chat
        }
    } catch (err) {
        connection.console.error(`Execute error: ${err}`);
        return { success: false, error: err instanceof Error ? err.message : String(err) };
    }
});

connection.onRequest('lisa/review', (params) => performCodeReview(typeof params === 'string' ? { uri: params } : params));
connection.onRequest('lisa/jiraCreate', (params) => createJiraIssue(params));
connection.onRequest('lisa/gitlabMR', (params) => createGitLabMR(params));

// Listen on the standard input/output streams.
connection.listen();
