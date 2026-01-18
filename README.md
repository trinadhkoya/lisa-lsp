# LISA AI Assistant

> **L**anguage-**I**ntegrated **S**mart **A**gent

LISA is an AI-powered coding assistant that integrates seamlessly with VS Code and IntelliJ-based IDEs (WebStorm, IntelliJ IDEA, PyCharm, etc.). Get intelligent code assistance, generate tests, add documentation, and refactor code with the power of multiple AI providers.

![Version](https://img.shields.io/badge/version-1.1.0-blue)
![License](https://img.shields.io/badge/license-ISC-green)

---

## ✨ Features

- 💬 **AI Chat** - Ask questions and get intelligent responses about your code
- 🧪 **Test Generation** - Automatically generate unit tests for your code
- 📝 **Documentation** - Add JSDoc/documentation comments to your functions
- 🔄 **Code Refactoring** - Get AI-powered refactoring suggestions
- 🎨 **Modern UI** - Clean, intuitive interface inspired by modern design principles
- 🔌 **Multi-Provider Support** - Works with OpenAI, Google Gemini, Anthropic Claude, and Groq

## 🆕 What's New in v1.1.17

### 🧠 Smart Test Generation (Intelligent Merging)
LISA now understands your existing test suite structure!
- **Auto-Detection**: Automatically finds existing test files (e.g., `UserService.test.ts`, `ServiceSpec.kt`) or `__tests__` folders.
- **Smart Merging**: Instead of overwriting files, LISA **merges new test cases** into your existing test files, preserving your setup and imports.
- **Creation**: If no tests exist, it intelligently creates the file in the correct location (e.g., inside `__tests__` or alongside the source).

### ⚡ Direct Code Editing (IntelliJ)
Experience seamless AI integration directly in your editor.
- **Refactoring**: Select code and ask LISA to refactor it (e.g., "Extract this to a function"). The changes are applied **directly** to your editor—no copy-pasting required!
- **Documentation**: Ask to "Add JSDoc" or "Add Docs", and LISA will insert professional documentation comments instantly.

### 💭 Live "Thinking" Indicator
- LISA now shows a **"Thinking..."** status in the chat while processing your request, so you always know when it's working hard for you.

---

## 📦 Installation

### For VS Code

#### Option 1: Install from VSIX (Recommended)

1. **Download the Extension**
   - Download `lisa-vscode-1.1.0.vsix` from the [releases page](https://github.com/trinadhkoya/lisa-lsp/releases)

2. **Install in VS Code**
   ```bash
   # Via command line
   code --install-extension lisa-vscode-1.1.0.vsix
   ```
   
   **OR**
   
   - Open VS Code
   - Press `Cmd+Shift+P` (Mac) or `Ctrl+Shift+P` (Windows/Linux)
   - Type "Extensions: Install from VSIX"
   - Select the downloaded `.vsix` file

3. **Reload VS Code**
   - Restart VS Code or click "Reload" when prompted

#### Option 2: Install from VS Code Marketplace

*(Coming soon - extension will be published to the marketplace)*

---

### For IntelliJ-based IDEs

> Works with: IntelliJ IDEA, WebStorm, PyCharm, PhpStorm, GoLand, RubyMine, CLion, DataGrip, Rider, and Android Studio

#### Installation Steps

1. **Download the Plugin**
   - Download `LISA AI Assistant-1.1.0.zip` from the [releases page](https://github.com/trinadhkoya/lisa-lsp/releases)

2. **Install in Your IDE**
   - Open your IntelliJ-based IDE (e.g., WebStorm, IntelliJ IDEA)
   - Go to **Settings/Preferences** → **Plugins**
   - Click the **⚙️ gear icon** → **Install Plugin from Disk...**
   - Select the downloaded `.zip` file
   - Click **OK**

3. **Restart Your IDE**
   - Restart your IDE when prompted

4. **Verify Installation**
   - After restart, you should see the **LISA Agent** tool window on the right side
   - If not visible, go to **View** → **Tool Windows** → **LISA Agent**

---

## 🚀 Getting Started

### Initial Configuration

Both VS Code and IntelliJ versions require an API key to function. You need to configure your preferred AI provider.

#### Step 1: Open LISA

**VS Code:**
- Click the LISA icon in the Activity Bar (left sidebar)
- Or press `Cmd+Shift+P` and type "LISA: Open Chat"

**IntelliJ:**
- Click the **LISA Agent** tab on the right side
- Or go to **View** → **Tool Windows** → **LISA Agent**

#### Step 2: Configure Your AI Provider

1. Click the **⚙️ Settings** icon in the LISA panel
2. Select your preferred provider:
   - **OpenAI** (GPT-4, GPT-3.5)
   - **Google Gemini** (Gemini Pro, Gemini Flash)
   - **Anthropic** (Claude 3 Opus, Sonnet, Haiku)
   - **Groq** (Llama 3, Mixtral, Gemma)

3. Choose your model from the dropdown
4. Enter your API key
5. Click **Save Configuration**

#### Getting API Keys

- **OpenAI**: https://platform.openai.com/api-keys
- **Google Gemini**: https://makersuite.google.com/app/apikey
- **Anthropic**: https://console.anthropic.com/
- **Groq**: https://console.groq.com/

---

## 💡 Usage

### Chat Mode

Ask LISA anything about your code or programming in general.

**Example:**
```
You: How do I implement a binary search in JavaScript?
LISA: Here's an efficient binary search implementation...
```

### Generate Tests

1. Select the code you want to test
2. Click the **Test Gen** pill in LISA
3. Type what you want to test (e.g., "edge cases for empty arrays")
4. Press Enter or click Send

**Example:**
```
You: Test edge cases
LISA: Here are unit tests for your function...
```

### Add Documentation

1. Select a function or class
2. Click the **Docs** pill
3. LISA will generate JSDoc/documentation comments

**Example:**
```
You: Document this function
LISA: /**
 * Calculates the sum of two numbers
 * @param {number} a - First number
 * @param {number} b - Second number
 * @returns {number} The sum of a and b
 */
```

### Refactor Code

1. Select the code you want to refactor
2. Click the **Refactor** pill
3. Describe how you want to refactor it

**Example:**
```
You: Extract this into smaller functions
LISA: Here's a refactored version with better separation of concerns...
```

---

## 🎯 Tips & Best Practices

### For Best Results:

1. **Be Specific** - The more context you provide, the better the responses
2. **Select Code** - When asking about specific code, select it first
3. **Iterate** - Don't hesitate to ask follow-up questions
4. **Use Agent Modes** - Switch between Chat, Test Gen, Docs, and Refactor for specialized tasks

### Keyboard Shortcuts

**VS Code:**
- `Cmd+Shift+P` → "LISA: Open Chat" - Open LISA panel
- `Enter` - Send message
- `Shift+Enter` - New line in message

**IntelliJ:**
- `Enter` - Send message
- `Shift+Enter` - New line in message

---

## 🔧 Troubleshooting

### "LSP server returned null" Error

**Solution:**
1. Make sure you've configured your API key
2. Click the ⚙️ icon and re-enter your API key
3. Verify your API key is valid and has credits

### Plugin Not Showing Up (IntelliJ)

**Solution:**
1. Go to **View** → **Tool Windows** → **LISA Agent**
2. If still not visible, restart your IDE
3. Check **Settings** → **Plugins** to ensure LISA is enabled

### Extension Not Working (VS Code)

**Solution:**
1. Check the Output panel: **View** → **Output** → Select "LISA Language Server"
2. Restart VS Code
3. Reinstall the extension if issues persist

### API Key Issues

**Solution:**
1. Verify your API key is correct (no extra spaces)
2. Check your API provider's dashboard for usage limits
3. Ensure your API key has the necessary permissions

---

## 📝 Configuration

### Environment Variables (Optional)

You can also configure LISA using environment variables:

```bash
# Set your preferred provider
export LISA_PROVIDER=openai

# Set your API key
export OPENAI_API_KEY=your-api-key-here
export GEMINI_API_KEY=your-api-key-here
export ANTHROPIC_API_KEY=your-api-key-here
export GROQ_API_KEY=your-api-key-here
```

### Advanced Configuration

For advanced users, you can modify the LSP server settings:

**Location:** `~/.lisa/config.json` (created after first run)

```json
{
  "provider": "openai",
  "model": "gpt-4-turbo",
  "temperature": 0.7,
  "maxTokens": 2000
}
```

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the ISC License.

---

## 🔗 Links

- **GitHub Repository**: https://github.com/trinadhkoya/lisa-lsp
- **Issues**: https://github.com/trinadhkoya/lisa-lsp/issues
- **Releases**: https://github.com/trinadhkoya/lisa-lsp/releases

---

## 🎉 Changelog

### v1.1.0 (2026-01-02)

**Major UI/UX Redesign:**
- ✨ Complete modern UI with Antigravity-inspired aesthetic
- 🎨 Darker color palette with better contrast
- 💬 Modern message cards for conversations
- ⚡ Smooth animations and transitions
- 🎯 Enhanced input area with focus states
- ⚙️ Improved settings panel

**Technical Improvements:**
- ✅ Fixed LSP response handling using workspace/executeCommand
- 🔧 Better error handling and user feedback
- 🐛 Production-ready with hidden debug logs
- 📱 Responsive and polished interface

### v1.0.9 (2026-01-01)

- 🔧 Fixed null response issue
- ⚙️ Added configuration management
- 📊 Improved logging and debugging
- 🎨 Enhanced UI components

---

## 💬 Support

If you encounter any issues or have questions:

1. Check the [Troubleshooting](#-troubleshooting) section
2. Search [existing issues](https://github.com/trinadhkoya/lisa-lsp/issues)
3. Create a [new issue](https://github.com/trinadhkoya/lisa-lsp/issues/new) with:
   - Your IDE/Editor version
   - LISA version
   - Steps to reproduce the issue
   - Error messages (if any)

---

**Made with ❤️ by the LISA team**
