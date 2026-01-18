# LISA Plugin Development Protocols

This document outlines the standard operating procedures and "lessons learned" for developing the LISA plugins (IntelliJ and VSCode).

## 1. Cross-Platform Parity (CRITICAL)
**Rule:** Any user-facing change (UI update, new feature, bug fix) applied to one platform **MUST** be applied to the other immediately.
- **UI Consistency:** Both plugins must share the same "Premium" aesthetic (Dark mode, Inter font, Animations/Bubbles).
- **Feature Parity:** If `AgentToolWindowFactory.kt` gets a new capability (e.g., config saving), `AgentPanel.ts` must get the equivalent.

## 2. Robust Path Handling
**Rule:** NEVER hardcode file paths (e.g., relying solely on `~/Desktop` or `~/Documents`).
- **Discovery Strategy:** Always implement a multi-step discovery logic:
    1.  Environment Variable (e.g., `LISA_SERVER_PATH`).
    2.  Standard User Locations (Documents, Desktop).
    3.  Dev-Relative Paths (e.g., `../dist/server.js` relative to project root).
- **Validation:** Always check `file.exists()` before attempting to launch a process. Fail with a clear, descriptive error message if missing.

## 3. Error Handling & Stability
- **LSP Connections:** Handle "Stream closed" or connection drops gracefully. This usually means the underlying process crashed or failed to start.
- **Reflection/API Compatibility:** When using internal IDE APIs (like IntelliJ's `LspServerImpl`), assume APIs might change between versions. Use robust, inspecting reflection (finding methods by name/signature) rather than rigid `getMethod` calls.
- **Logging:** Ensure errors are logged to the IDE's event log or a visible debug console, not just swallowed.

## 4. Versioning
- **Bump Versions:** Always increment the version number after a successful set of changes.
    - IntelliJ: `gradle.properties` -> `pluginVersion`
    - VSCode: `package.json` -> `version`

## 5. UI/UX Standards
- **Theme:** "Premium Dark" is the standard.
    - Application Background: `#1e1f22` (IntelliJ standard)
    - Panel Background: `#2b2d30`
    - Accents: `#3574f0` (Blue)
- **Welcome Screen:** Must feature the "Hero Art" (Animated Bubbles) and clear "Bring Your Own Key" call to action.
- **Input Area:** Auto-expanding text areas with clear start/stop buttons.

---
*Created: Jan 2026*
*Ref: "Stream Closed" Debugging Session*
