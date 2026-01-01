import { config } from 'dotenv';

// Load .env file variables into process.env
// Suppress dotenv logging to stdout as it breaks LSP protocol
const stdoutWrite = process.stdout.write;
// eslint-disable-next-line @typescript-eslint/no-explicit-any, @typescript-eslint/no-unused-vars
process.stdout.write = ((...args: any[]) => true) as any;
config();
process.stdout.write = stdoutWrite;

export const GITLAB_TOKEN = process.env.GITLAB_TOKEN || '';
export const GITLAB_HOST = process.env.GITLAB_HOST || 'https://gitlab.com';
export const JIRA_HOST = process.env.JIRA_HOST || '';
export const JIRA_USERNAME = process.env.JIRA_USERNAME || '';
export const JIRA_API_TOKEN = process.env.JIRA_API_TOKEN || '';
export const OPENAI_API_KEY = process.env.OPENAI_API_KEY || '';
export const GEMINI_API_KEY = process.env.GEMINI_API_KEY || '';
export const GROQ_API_KEY = process.env.GROQ_API_KEY || '';
export const ANTHROPIC_API_KEY = process.env.ANTHROPIC_API_KEY || '';
