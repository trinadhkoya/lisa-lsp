# LISA IntelliJ Plugin Agent Capabilities

## 1. Direct Code Editing (Refactor & Documentation)
Instead of streaming responses to the chat window, the Agent automatically applies changes directly to the source code when the intent is `refactor` or `addJsDoc`.

### Implementation Logic
- **Trigger**: `agent == "refactor"` or `agent == "addJsDoc"`
- **Action**: `WriteCommandAction`
- **Behavior**:
  - If text is **selected**: Replaces the selection with the Agent's output.
  - If **no selection**: Inserts the Agent's output at the current cursor position.
- **Feedback**: Displays a success message ("Code updated in editor successfully!") in the chat instead of the raw code.

## 2. Smart Test Generation
The Agent intelligently manages unit test files, ensuring a clean project structure.

### Implementation Logic
- **Trigger**: `agent == "generateTests"`
- **Action**: `WriteCommandAction`
- **Behavior**:
  1. **Identify Context**: Locates the currently active file (e.g., `UserService.kt`).
  2. **Locate/Create Test Directory**: Checks for a `__tests__` directory in the same folder.
     - *If missing*: Automatically creates the `__tests__` directory.
  3. **Determine Test Filename**: Appends `Test` to the filename (e.g., `UserServiceTest.kt`).
  4. **Create File**: Creates the test file inside `__tests__`.
  5. **Write Content**: Writes the generated test code into the new file.
  6. **Open Editor**: Automatically opens the newly created test file for the user.
- **Feedback**: Displays "Tests generated in [path]" in the chat.

## 3. Communication Bridge (JCEF)
- Uses `JBCefBrowser` to host the webview.
- Messages are dispatched via `window.postMessage` (Javascript) from Kotlin.
- Use `ApplicationManager.getApplication().invokeLater` for UI updates.
- Use `ReadAction` and `WriteCommandAction` for PSI/file access.
