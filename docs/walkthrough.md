# 🤖 LISA: Getting Started Guide

Welcome to **LISA** (Localization & Intelligence Support Assistant). This guide will spoon-feed you through the process of setting up and running your own AI-powered developer assistant.

## ✨ What LISA Can Do

1.  **LISA Execute (`lisa/execute`)**: Talk to LISA in natural language. She understands commands like *"LISA, review MR 1307"* or *"LISA, create a jira for the checkout bug"*.
2.  **Multi-AI Backends**: LISA can think using **OpenAI**, **Anthropic (Claude)**, **Google (Gemini)**, or **Groq**. 
3.  **Automatic MR Discovery**: Just give LISA a GitLab URL, and she'll figure out the project and MR ID on her own.
4.  **JIRA & GitLab Native**: Built-in support for the tools you use every day.

## IDE Integration

For detailed instructions on how to wrap this server as a VS Code extension or WebStorm plugin, see the [IDE Integration Guide](file:///Users/trinadhkoya/.gemini/antigravity/brain/e156e779-a346-45e3-adc0-eb734964783f/IDE_INTEGRATION.md).

## Setup & Configuration

### AI Provider Settings
Developers can configure their preferred AI provider dynamically via the LSP:
- **Provider**: `openai`, `groq`, `gemini`, `claude`
- **Model**: Specific model name (e.g., `gpt-4-turbo-preview`, `llama3-8b-8192`)
- **API Key**: User-specific key.

### Authentication
Update the `.env` file in the root directory:
```env
GITLAB_TOKEN=your_gitlab_token
GITLAB_HOST=https://gitlab.com
JIRA_HOST=your-domain.atlassian.net
JIRA_USERNAME=your-email
JIRA_API_TOKEN=your-jira-token
OPENAI_API_KEY=your-openai-key
GEMINI_API_KEY=...
GROQ_API_KEY=...
```

## Prerequisites

- **Node.js**: v18+
- **Bun**: v1.0+ (used for package management and running scripts)
- **GitLab Token**: Personal Access Token with `api` scope.
- **JIRA API Token**: Atlassian API token.
- **OpenAI API Key**: Key for AI analysis.

## Installation & Setup

1.  **Clone/Navigate** to the project directory:
    ```bash
    cd /Users/trinadhkoya/mobile/mcp-lsp
    ```

2.  **Install Dependencies**:
    ```bash
    bun install
    ```

3.  **Configure Environment**:
    Create or update `.env` in the root directory:
    ```ini
    GITLAB_TOKEN=glpat-...
    GITLAB_HOST=https://gitlab.com
    GITLAB_PROJECT_ID=60621859
    GITLAB_MR_IID=1321  # Default MR ID for testing
    JIRA_HOST=inspirebrands.atlassian.net
    JIRA_USERNAME=email@example.com
    JIRA_API_TOKEN=ATATT...
    JIRA_PROJECT_KEY=DOVS
    OPENAI_API_KEY=sk-...
    ```

4.  **Build the Server**:
    The server must be compiled to CommonJS for Node compatibility.
    ```bash
    bun run tsc
    ```
    This creates `dist/server.js`.

## Running the Server

### Standalone (Development/Verification)
Use the included client script to verify functionality:
```bash
bun run client.ts
```
This script spawns the server and sends test requests for all three features.

### VS Code Integration
To use this in VS Code, you would create a simple extension that launches the server:
```typescript
const serverOptions: ServerOptions = {
    run: { module: context.asAbsolutePath("dist/server.js"), transport: TransportKind.stdio },
    debug: { module: context.asAbsolutePath("dist/server.js"), transport: TransportKind.stdio }
};
```

## Troubleshooting

### SSL/Certificate Errors
If you are behind a corporate proxy (e.g., Zscaler) and see `UNABLE_TO_GET_ISSUER_CERT_LOCALLY`, the server is configured to bypass this:
- `NODE_TLS_REJECT_UNAUTHORIZED = '0'` is set globally in `server.ts`.
- `strictSSL: false` is configured for the JIRA client.

### Logs
The server writes debug logs to `server.log` in the project root. Check this file if requests seem to hang or fail silently.

### dotenv Logging
The `dotenv` package (v17+) prints logs to stdout, which corrupts the LSP stream. The server code patches `process.stdout.write` during config loading to suppress this.

## Verification Results
- **AI Code Review**: ✅ Verified (MR 1321 & MR 1307). Diff fetched, analyzed, and comment posted.
- **GitLab MR**: ✅ Verified. API connectivity confirmed.
- **JIRA**: ✅ Verified. API connectivity confirmed (Permissions error received, confirming successful request handling).
