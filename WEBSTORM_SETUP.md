# WebStorm Setup Guide for LISA Plugin

## What Was Fixed

The plugin wasn't working in WebStorm due to several issues:

1. **Hardcoded server path** - The path to server.js was hardcoded to a specific location
2. **Missing WebStorm dependencies** - The plugin didn't declare support for JavaScript/TypeScript
3. **Platform compatibility** - Configuration needed updates for WebStorm support

## Installation Steps

### 1. Build the Plugin

```bash
cd /Users/trinadhkoya/Desktop/mcp-lsp
npm run build  # Build the TypeScript server
cd intellij
./gradlew buildPlugin -x buildSearchableOptions
```

The plugin will be built at: `intellij/build/distributions/LISA AI Assistant-1.0.13.zip`

### 2. Install in WebStorm

1. Open WebStorm
2. Go to **Settings/Preferences** → **Plugins**
3. Click the gear icon ⚙️ → **Install Plugin from Disk...**
4. Select the zip file: `intellij/build/distributions/LISA AI Assistant-1.0.13.zip`
5. Restart WebStorm

### 3. Configure Server Path (Optional)

By default, the plugin looks for the server at: `~/Desktop/mcp-lsp/dist/server.js`

If your server is in a different location, set the environment variable:

```bash
export LISA_SERVER_PATH="/path/to/your/mcp-lsp/dist/server.js"
```

Then restart WebStorm.

### 4. Verify Installation

1. Open any file in WebStorm (e.g., a `.ts` or `.js` file)
2. Go to **Tools** → **LISA** → **Execute LISA Command**
3. Try a command like: "review this code"

## Troubleshooting

### Server Not Starting

**Check the logs:**
- Server logs: `/Users/trinadhkoya/Desktop/mcp-lsp/server.log`
- WebStorm logs: **Help** → **Show Log in Finder**

**Common issues:**
- Server not built: Run `npm run build` in the mcp-lsp directory
- Node not installed: Install Node.js v18+
- Wrong path: Set `LISA_SERVER_PATH` environment variable

### Commands Not Working

1. Check if server is initialized:
   ```bash
   tail -f /Users/trinadhkoya/Desktop/mcp-lsp/server.log
   ```
   
2. Look for errors like:
   - "404 Not Found" - Check your GitLab credentials in `.env`
   - "API Key missing" - Configure AI provider via **Tools** → **LISA** → **Configure LISA Provider**

### Plugin Not Loading

- Verify WebStorm version is 2025.2.5 or higher (plugin requires build 252+)
- Check if JavaScript plugin is enabled in WebStorm
- Try reinstalling the plugin

## Environment Variables

Make sure your `.env` file exists at `/Users/trinadhkoya/Desktop/mcp-lsp/.env`:

```env
GITLAB_TOKEN=your_token
GITLAB_HOST=https://gitlab.com
JIRA_HOST=your-instance.atlassian.net
JIRA_USERNAME=your-email@company.com
JIRA_API_TOKEN=your_jira_token
OPENAI_API_KEY=sk-...
GEMINI_API_KEY=AIzaSy...
GROQ_API_KEY=gsk_...
ANTHROPIC_API_KEY=sk-ant-...
```

## Testing the Setup

1. Open a TypeScript/JavaScript file in WebStorm
2. Open **Tools** → **LISA** → **Execute LISA Command**
3. Try these commands:
   - "hello" (basic test)
   - "review this code" (with a file open)
   - "create a jira ticket for testing" (if JIRA is configured)

## Next Steps

- Set up keyboard shortcuts for LISA commands in **Settings** → **Keymap**
- Customize the AI provider in **Tools** → **LISA** → **Configure LISA Provider**
- Check the LISA Agent Manager tool window on the right side
