# 🤖 LISA: Localization & Intelligence Support Assistant

LISA is a powerful, cross-IDE **Language Server Protocol (LSP)** backend designed to automate the developer workflow. She bridges the gap between your code editor, GitLab Merge Requests, JIRA Tickets, and the world's most advanced AI models.

---

## 🌟 Key Features

*   **🧠 Agentic Natural Language Control**: Type commands like *"review MR 1307"* or *"create jira for login bug"* and let LISA handle the logic.
*   **🔌 Multi-AI Provider Support**: Switch instantly between **OpenAI**, **Claude**, **Gemini**, and **Groq**.
*   **Smart GitLab Integration**: LISA identifies project paths and MR IIDs directly from URLs or branch names.
*   **🎟️ JIRA Automation**: Create tickets without leaving your IDE using natural language.
*   **🛠️ Cross-IDE Compatibility**: Works with VS Code, WebStorm/IntelliJ, and any other LSP-compliant editor.

---

## 🚀 Getting Started (Step-by-Step)

### 1. Prerequisites (Node or Bun?)
LISA is flexible! You can choose your engine:
*   **Recommended (Stable)**: **Node.js** (v18 or higher). Use this for the final IDE extension.
*   **Fastest (Developer Choice)**: **Bun**. Use this if you want 10x faster installations and execution during development.

> [!TIP]
> If you have **Bun** installed, use it! If not, standard **Node.js** works perfectly. You do **not** need both.

### 2. Installation
Open your terminal and run:
```bash
# Clone the repository
git clone <your-repo-url>
cd lisa-lsp

# Install dependencies
bun install   # or npm install
```

### 3. Configuration (The "Spoon-Fed" Way)
LISA needs credentials to talk to GitLab, JIRA, and AI.
1.  In the root folder, create a file named `.env`.
2.  Copy and paste the template below, replacing the values with your own keys:

```env
# GITLAB SETTINGS
GITLAB_TOKEN=your_personal_access_token
GITLAB_HOST=https://gitlab.com
GITLAB_PROJECT_ID=12345678  # Find this on your project home page

# JIRA SETTINGS
JIRA_HOST=your-instance.atlassian.net
JIRA_USERNAME=your-email@company.com
JIRA_API_TOKEN=your_jira_api_token
JIRA_PROJECT_KEY=PROJ

# AI PROVIDER KEYS
OPENAI_API_KEY=sk-...
GEMINI_API_KEY=AIzaSy...
GROQ_API_KEY=gsk_...
ANTHROPIC_API_KEY=sk-ant-...
```

### 4. Build the Server
You must compile the TypeScript code into JavaScript before running:
```bash
bun run build  # or npm run build
```

### 5. Start the Server
LISA communicates via Standard I/O (Stdio):
```bash
bun run start  # or npm start
```

---

## ⚡ Usage in your IDE

LISA is a **backend server**. To use her in your editor, you need a thin "Client Wrapper".

*   **VS Code**: See our [VS Code Integration Guide](./IDE_INTEGRATION.md) to add a LISA command palette.
*   **WebStorm**: Follow the [JetBrains Guide](./IDE_INTEGRATION.md) to register the LSP server.

### Example LISA Commands:
Once integrated, you can trigger `lisa/execute` and type:
*   `review this MR https://gitlab.com/.../-/merge_requests/1307`
*   `create a jira ticket for investigating the checkout crash`
*   `create an MR from feature/ui to develop titled "Update button styles"`

---

## 🔍 Troubleshooting

| Issue | Solution |
| :--- | :--- |
| **"Access Blocked" (Zscaler)** | Your corporate proxy might block Groq/Gemini. Use OpenAI or an internal proxy endpoint. |
| **"Not Found" (404)** | Double-check your `GITLAB_PROJECT_ID` and MR URL segments. |
| **"Forbidden" (403)** | Ensure your JIRA token has "Write" permissions for your project. |

---

## 🛠️ Developer Guide

Want to expand LISA's brain?
*   **Source Code**: Located in `src/server.ts`.
*   **Request Handlers**: Look for `connection.onRequest('lisa/...')`.
*   **AI Logic**: Centralized in the `callAi()` helper function.

---

**Happy Coding with LISA!** 🚀
