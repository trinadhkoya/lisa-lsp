#!/bin/bash
set -e # Exit immediately if a command exits with a non-zero status.

# 1. Commit all pending work
echo "Committing changes..."
git add .
# Allow commit to fail if there's nothing to commit, but don't exit script
git commit -m "Release update" || echo "Nothing to commit, proceeding..."

# 2. Update versions (Syncs VS Code and IntelliJ to git commit count)
echo "Syncing versions..."
./update-version.sh

# 3. Build Verification (Fail fast)
echo "Verifying VS Code build..."
cd vscode
npm install
npm run compile
cd ..

echo "Verifying IntelliJ build..."
cd intellij
./gradlew buildPlugin
cd ..

# 4. Publish VS Code
echo "Publishing VS Code extension..."
# Bypass SSL errors
export NODE_TLS_REJECT_UNAUTHORIZED=0
# Try to unpublish, but don't fail the script if it doesn't exist
npx vsce unpublish trinadhkoya.lisa-vscode --force || echo "Unpublish skipped (extension might not exist)"
# Publish new version
cd vscode
npx vsce publish
cd ..

# 5. Publish IntelliJ
echo "Publishing IntelliJ plugin..."
# Expects PUBLISH_TOKEN to be set in environment
cd intellij
./gradlew publishPlugin
cd ..

echo "SUCCESS: Both extensions verified and published!"
