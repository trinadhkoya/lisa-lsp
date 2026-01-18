#!/bin/bash

# Get the total commit count
COMMIT_COUNT=$(git rev-list --count HEAD)
NEW_VERSION="1.1.$COMMIT_COUNT"

echo "Updating project versions to $NEW_VERSION..."

# Update root package.json
# Using sed for compatibility and simplicity without extra jq dependency if possible, 
# but jq is safer. Assuming sed for now as per minimal dependency preference, 
# or specific node script if preferred. Let's use node for reliable JSON handling.

node -e "
    const fs = require('fs');
    
    function updateJson(path) {
        if (!fs.existsSync(path)) return;
        const pkg = JSON.parse(fs.readFileSync(path, 'utf8'));
        pkg.version = '$NEW_VERSION';
        fs.writeFileSync(path, JSON.stringify(pkg, null, 2) + '\n');
        console.log('Updated ' + path);
    }

    updateJson('./package.json');
    updateJson('./vscode/package.json');
"

# Update IntelliJ gradle.properties
GRADLE_PROP="./intellij/gradle.properties"
if [ -f "$GRADLE_PROP" ]; then
    # Use perl for in-place editing to handle MacOS sed differences if needed, or simple sed
    # MacOS sed requires empty extension for -i
    sed -i '' "s/^pluginVersion = .*/pluginVersion = $NEW_VERSION/" "$GRADLE_PROP"
    echo "Updated $GRADLE_PROP"
fi

echo "Version updated to $NEW_VERSION"
