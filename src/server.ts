process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0';
import {
    createConnection,
    ProposedFeatures,
    InitializeParams,
    InitializeResult,
    TextDocuments,
    TextDocumentSyncKind,
    ExecuteCommandParams
} from 'vscode-languageserver/node';

import { TextDocument as TextDoc } from 'vscode-languageserver-textdocument';
import * as fs from 'fs';
import * as path from 'path';
import { Gitlab } from '@gitbeaker/node';
import OpenAI from 'openai';
import { GoogleGenerativeAI } from '@google/generative-ai';
import Anthropic from '@anthropic-ai/sdk';
import { GITLAB_TOKEN, GITLAB_HOST, JIRA_HOST, JIRA_USERNAME, JIRA_API_TOKEN, OPENAI_API_KEY } from './config';

// eslint-disable-next-line @typescript-eslint/no-var-requires
const JiraClient = require('jira-client');

// --- Logging Utility ---
const logFile = path.resolve(__dirname, '../server.log');
function debugLog(msg: string) {
    const timestamp = new Date().toISOString();
    fs.appendFileSync(logFile, `[${timestamp}] ${msg}\n`);
}

debugLog('Server process started (Refactored Version)');

// --- Configuration & Types ---
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

interface AiMessage {
    role: 'system' | 'user' | 'assistant';
    content: string;
}

// --- AI Provider Pattern ---
interface AiProvider {
    generate(messages: AiMessage[], model: string, apiKey: string): Promise<string>;
}

class OpenAiProvider implements AiProvider {
    private client: OpenAI | null = null;
    private currentKey: string = '';
    private baseURL?: string;

    constructor(baseURL?: string) {
        this.baseURL = baseURL;
    }

    async generate(messages: AiMessage[], model: string, apiKey: string): Promise<string> {
        if (!this.client || this.currentKey !== apiKey) {
            debugLog(`Initializing OpenAI client (BaseURL: ${this.baseURL || 'Default'})`);
            this.client = new OpenAI({ apiKey, baseURL: this.baseURL });
            this.currentKey = apiKey;
        }

        const completion = await this.client.chat.completions.create({
            messages: messages.map(m => ({ role: m.role, content: m.content })),
            model: model
        });
        return completion.choices[0].message.content || '';
    }
}

class GeminiProvider implements AiProvider {
    async generate(messages: AiMessage[], model: string, apiKey: string): Promise<string> {
        debugLog('Initializing Gemini request');
        const genAI = new GoogleGenerativeAI(apiKey);
        const genModel = genAI.getGenerativeModel({ model: model || 'gemini-1.5-flash' });

        const systemPrompt = messages.find(m => m.role === 'system')?.content || '';
        const userPrompt = messages.find(m => m.role === 'user')?.content || '';
        const fullPrompt = systemPrompt ? `${systemPrompt}\n\nUser: ${userPrompt}` : userPrompt;

        const result = await genModel.generateContent(fullPrompt);
        return result.response.text();
    }
}

class ClaudeProvider implements AiProvider {
    private client: Anthropic | null = null;
    private currentKey: string = '';

    async generate(messages: AiMessage[], model: string, apiKey: string): Promise<string> {
        if (!this.client || this.currentKey !== apiKey) {
            debugLog('Initializing Claude client');
            this.client = new Anthropic({ apiKey });
            this.currentKey = apiKey;
        }

        const system = messages.find(m => m.role === 'system')?.content;
        const userMessages = messages
            .filter(m => m.role !== 'system')
            .map(m => ({
                role: m.role === 'user' ? 'user' as const : 'assistant' as const,
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
const providers: Record<string, AiProvider> = {
    'openai': new OpenAiProvider(),
    'groq': new OpenAiProvider('https://api.groq.com/openai/v1'),
    'gemini': new GeminiProvider(),
    'claude': new ClaudeProvider()
};

async function callAi(messages: AiMessage[]): Promise<string> {
    const { provider, apiKey, model } = currentConfig;
    debugLog(`callAi: provider=${provider}, model=${model}, hasKey=${!!apiKey}`);

    if (!apiKey) throw new Error(`API Key for ${provider} is missing. Please configure it.`);

    const handler = providers[provider];
    if (!handler) throw new Error(`Unsupported provider: ${provider}`);

    try {
        const result = await handler.generate(messages, model, apiKey);
        debugLog(`callAi: Success (len=${result.length})`);
        return result;
    } catch (error) {
        debugLog(`callAi ERROR: ${error}`);
        throw error;
    }
}

// --- External Service Clients ---
const gitlab = new Gitlab({ token: GITLAB_TOKEN, host: GITLAB_HOST });
const jira = new JiraClient({
    protocol: 'https',
    host: JIRA_HOST,
    username: JIRA_USERNAME,
    password: JIRA_API_TOKEN,
    apiVersion: '2',
    strictSSL: false
});

// --- Logic Handlers ---

async function handleGenerateTests(code: string, context: any) {
    if (!code) throw new Error('No code context provided.');

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

async function handleAddJsDoc(code: string) {
    if (!code) throw new Error('No code context provided.');
    return await callAi([
        { role: 'system', content: 'You are a documentation expert. Add JSDoc/Docstrings. Return ONLY the FULL code with comments.' },
        { role: 'user', content: code }
    ]);
}

async function handleRefactor(code: string, instruction: string) {
    if (!code) throw new Error('No code context provided.');
    return await callAi([
        { role: 'system', content: 'Refactor code based on instructions. Improve structure/readability. Return ONLY the code.' },
        { role: 'user', content: `Code:\n${code}\n\nInstruction: ${instruction}` }
    ]);
}

// --- Server Setup ---
const connection = createConnection(ProposedFeatures.all);
const documents = new TextDocuments(TextDoc);
documents.listen(connection);

connection.onInitialize((params: InitializeParams): InitializeResult => {
    debugLog('Initialized');
    return {
        capabilities: {
            textDocumentSync: TextDocumentSyncKind.Incremental,
            executeCommandProvider: {
                commands: ['lisa.chat']
            }
        }
    };
});

// --- LSP Request Handlers ---

connection.onRequest('lisa/updateConfig', (config: Partial<McpConfig>) => {
    currentConfig = { ...currentConfig, ...config };
    debugLog(`Config updated: ${currentConfig.provider} / ${currentConfig.model}`);
    return { success: true };
});

connection.onRequest('lisa/configure', (config: Partial<McpConfig>) => {
    currentConfig = { ...currentConfig, ...config };
    debugLog(`Config updated (via configure): ${currentConfig.provider} / ${currentConfig.model}`);
    return { success: true };
});

connection.onExecuteCommand(async (params: ExecuteCommandParams) => {
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
        } catch (err) {
            const msg = err instanceof Error ? err.message : String(err);
            debugLog(`Command Error: ${msg}`);
            return `ERROR: ${msg}`;
        }
    }
    return null;
});

// Legacy/Rich Request Handler (Backwards Compatibility & Fancy Features)
connection.onRequest('lisa/execute', async (params: any) => {
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
        } catch {
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

    } catch (err) {
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
