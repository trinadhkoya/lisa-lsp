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
const fs = __importStar(require("fs"));
const path = __importStar(require("path"));
const node_2 = require("@gitbeaker/node");
const openai_1 = __importDefault(require("openai"));
const generative_ai_1 = require("@google/generative-ai");
const sdk_1 = __importDefault(require("@anthropic-ai/sdk"));
const config_1 = require("./config");
// eslint-disable-next-line @typescript-eslint/no-var-requires
const JiraClient = require('jira-client');
// --- Logging Utility ---
const logFile = path.resolve(__dirname, '../server.log');
function debugLog(msg) {
    const timestamp = new Date().toISOString();
    fs.appendFileSync(logFile, `[${timestamp}] ${msg}\n`);
}
debugLog('Server process started (Refactored Version)');
let currentConfig = {
    provider: 'openai',
    apiKey: config_1.OPENAI_API_KEY,
    model: 'gpt-4-turbo-preview'
};
class OpenAiProvider {
    client = null;
    currentKey = '';
    baseURL;
    constructor(baseURL) {
        this.baseURL = baseURL;
    }
    async generate(messages, model, apiKey) {
        if (!this.client || this.currentKey !== apiKey) {
            debugLog(`Initializing OpenAI client (BaseURL: ${this.baseURL || 'Default'})`);
            this.client = new openai_1.default({ apiKey, baseURL: this.baseURL });
            this.currentKey = apiKey;
        }
        const completion = await this.client.chat.completions.create({
            messages: messages.map(m => ({ role: m.role, content: m.content })),
            model: model
        });
        return completion.choices[0].message.content || '';
    }
}
class GeminiProvider {
    async generate(messages, model, apiKey) {
        debugLog('Initializing Gemini request');
        const genAI = new generative_ai_1.GoogleGenerativeAI(apiKey);
        const genModel = genAI.getGenerativeModel({ model: model || 'gemini-1.5-flash' });
        const systemPrompt = messages.find(m => m.role === 'system')?.content || '';
        const userPrompt = messages.find(m => m.role === 'user')?.content || '';
        const fullPrompt = systemPrompt ? `${systemPrompt}\n\nUser: ${userPrompt}` : userPrompt;
        const result = await genModel.generateContent(fullPrompt);
        return result.response.text();
    }
}
class ClaudeProvider {
    client = null;
    currentKey = '';
    async generate(messages, model, apiKey) {
        if (!this.client || this.currentKey !== apiKey) {
            debugLog('Initializing Claude client');
            this.client = new sdk_1.default({ apiKey });
            this.currentKey = apiKey;
        }
        const system = messages.find(m => m.role === 'system')?.content;
        const userMessages = messages
            .filter(m => m.role !== 'system')
            .map(m => ({
            role: m.role === 'user' ? 'user' : 'assistant',
            content: m.content
        }));
        const response = await this.client.messages.create({
            model: model,
            system: system,
            messages: userMessages,
            max_tokens: 4096
        });
        const textContent = response.content.find(c => c.type === 'text');
        return textContent && 'text' in textContent ? textContent.text : '';
    }
}
// --- Provider Factory ---
const providers = {
    'openai': new OpenAiProvider(),
    'groq': new OpenAiProvider('https://api.groq.com/openai/v1'),
    'gemini': new GeminiProvider(),
    'claude': new ClaudeProvider()
};
async function callAi(messages) {
    const { provider, apiKey, model } = currentConfig;
    debugLog(`callAi: provider=${provider}, model=${model}, hasKey=${!!apiKey}`);
    if (!apiKey)
        throw new Error(`API Key for ${provider} is missing. Please configure it.`);
    const handler = providers[provider];
    if (!handler)
        throw new Error(`Unsupported provider: ${provider}`);
    try {
        const result = await handler.generate(messages, model, apiKey);
        debugLog(`callAi: Success (len=${result.length})`);
        return result;
    }
    catch (error) {
        debugLog(`callAi ERROR: ${error}`);
        throw error;
    }
}
// --- External Service Clients ---
const gitlab = new node_2.Gitlab({ token: config_1.GITLAB_TOKEN, host: config_1.GITLAB_HOST });
const jira = new JiraClient({
    protocol: 'https',
    host: config_1.JIRA_HOST,
    username: config_1.JIRA_USERNAME,
    password: config_1.JIRA_API_TOKEN,
    apiVersion: '2',
    strictSSL: false
});
// --- Logic Handlers ---
async function handleGenerateTests(code, context) {
    if (!code)
        throw new Error('No code context provided.');
    const existingTest = context.existingTestContent || '';
    const fileStructure = context.fileStructureInfo || '';
    const systemPrompt = `You are a QA automation expert. Generate comprehensive unit tests.
    RULES:
    1. Use simple, standard frameworks (Jest, JUnit, etc.).
    2. Return ONLY the code.
    3. ${existingTest ? `Follow style of:\n${existingTest}\n` : ''}
    4. ${fileStructure ? `File Context:\n${fileStructure}` : ''}`;
    return await callAi([
        { role: 'system', content: systemPrompt },
        { role: 'user', content: code }
    ]);
}
async function handleAddJsDoc(code) {
    if (!code)
        throw new Error('No code context provided.');
    return await callAi([
        { role: 'system', content: 'You are a documentation expert. Add JSDoc/Docstrings. Return ONLY the FULL code with comments.' },
        { role: 'user', content: code }
    ]);
}
async function handleRefactor(code, instruction) {
    if (!code)
        throw new Error('No code context provided.');
    return await callAi([
        { role: 'system', content: 'Refactor code based on instructions. Improve structure/readability. Return ONLY the code.' },
        { role: 'user', content: `Code:\n${code}\n\nInstruction: ${instruction}` }
    ]);
}
// --- Server Setup ---
const connection = (0, node_1.createConnection)(node_1.ProposedFeatures.all);
const documents = new node_1.TextDocuments(vscode_languageserver_textdocument_1.TextDocument);
documents.listen(connection);
connection.onInitialize((params) => {
    debugLog('Initialized');
    return {
        capabilities: {
            textDocumentSync: node_1.TextDocumentSyncKind.Incremental,
            executeCommandProvider: {
                commands: ['lisa.chat']
            }
        }
    };
});
// --- LSP Request Handlers ---
connection.onRequest('lisa/updateConfig', (config) => {
    currentConfig = { ...currentConfig, ...config };
    debugLog(`Config updated: ${currentConfig.provider} / ${currentConfig.model}`);
    return { success: true };
});
connection.onRequest('lisa/configure', (config) => {
    currentConfig = { ...currentConfig, ...config };
    debugLog(`Config updated (via configure): ${currentConfig.provider} / ${currentConfig.model}`);
    return { success: true };
});
connection.onExecuteCommand(async (params) => {
    debugLog(`ExecuteCommand: ${params.command}`);
    if (params.command === 'lisa.chat') {
        const [prompt, context] = params.arguments || [];
        const userPrompt = String(prompt || '');
        const ctx = context || {};
        try {
            // Check for explicit commands in prompt first
            // This allows the chat interface to trigger actions naturally
            // NOTE: Ideally we would use the intent classification again here
            // reusing the logic from the old lisa/execute
            return await callAi([{ role: 'user', content: userPrompt }]);
        }
        catch (err) {
            const msg = err instanceof Error ? err.message : String(err);
            debugLog(`Command Error: ${msg}`);
            return `ERROR: ${msg}`;
        }
    }
    return null;
});
// Legacy/Rich Request Handler (Backwards Compatibility & Fancy Features)
connection.onRequest('lisa/execute', async (params) => {
    const command = typeof params === 'string' ? params : params.command;
    const context = typeof params === 'string' ? {} : params.context;
    const requestId = params.requestId || Date.now().toString();
    debugLog(`lisa/execute: ${command}`);
    try {
        // AI Intent Classification
        const classification = await callAi([
            {
                role: 'system',
                content: `Classify the user intent into JSON:
                { "action": "generateTests" | "addJsDoc" | "refactor" | "chat", "params": {} }
                For "refactor", include "instruction".
                Default to "chat". Return ONLY raw JSON.`
            },
            { role: 'user', content: command }
        ]);
        let intent;
        try {
            intent = JSON.parse(classification.replace(/```json|```/g, '').trim());
        }
        catch {
            intent = { action: 'chat' };
        }
        debugLog(`Intent: ${JSON.stringify(intent)}`);
        let result;
        const code = context.selection || context.fileContent || '';
        switch (intent.action) {
            case 'generateTests':
                result = await handleGenerateTests(code, context);
                break;
            case 'addJsDoc':
                result = await handleAddJsDoc(code);
                break;
            case 'refactor':
                result = await handleRefactor(code, intent.params?.instruction || command);
                break;
            default:
                result = await callAi([{ role: 'user', content: command }]);
        }
        // Return structured result for client
        const responseData = { success: true, data: result, action: intent.action };
        // Notify client (legacy pattern)
        connection.sendNotification('lisa/executeResult', { requestId, result: responseData });
        return { acknowledged: true, requestId };
    }
    catch (err) {
        const errorMsg = err instanceof Error ? err.message : String(err);
        debugLog(`lisa/execute error: ${errorMsg}`);
        connection.sendNotification('lisa/executeResult', {
            requestId,
            result: { success: false, error: errorMsg }
        });
        return { acknowledged: true, error: true };
    }
});
connection.listen();
