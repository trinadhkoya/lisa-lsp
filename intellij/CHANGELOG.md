<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# lisa-intellij-plugin Changelog

## [1.1.17] - 2026-01-18
### Added
- **Smart Test Generation**: Scans for existing test files (`__tests__`, `.test.ts`, `.spec.kt`) and merges new test cases into them instead of overwriting.
- **Direct Code Editing (IntelliJ)**: Implemented `WriteCommandAction` for `refactor` and `addJsDoc` commands to modify editor content directly.
- **UI UX**: Added a "Thinking..." indicator in the chat panel to show when the agent is processing.
- **Compatibility**: Downgraded IntelliJ build requirement to `232` (2023.2+) for broader compatibility.

## [Unreleased]

## [1.0.3] - 2026-01-01

### Added

- Added `Configure LISA Provider` action to set API keys and provider settings.

## [1.0.2] - 2026-01-01

### Fixed

- Updated LSP method name to `lisa/execute`.
- Removed experimental and deprecated template code to improve marketplace compatibility.

## [1.0.1] - 2026-01-01

### Added

- Updated branding to LISA AI Assistant (New name and logo).

## [1.0.0] - 2026-01-01

### Added

- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)

[Unreleased]: https://github.com/trinadhkoya/lisa-intellij-plugin/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/trinadhkoya/lisa-intellij-plugin/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/trinadhkoya/lisa-intellij-plugin/commits/v1.0.0
