#!/bin/bash

# Bypass SSL verification for vsce (fixes 'unable to get local issuer certificate')
export NODE_TLS_REJECT_UNAUTHORIZED=0

# Update version first
./update-version.sh

# Confirm with user before proceeding with destructive action
echo "WARNING: This will UNPUBLISH the extension 'trinadhkoya.lisa-vscode', removing all versions and history."
read -p "Are you sure you want to proceed? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Operation cancelled."
    exit 1
fi

echo "Unpublishing extension..."
npx vsce unpublish trinadhkoya.lisa-vscode --force

echo "Packaging and Publishing new version..."
cd vscode
npm install
npm run vscode:prepublish
npx vsce publish --no-dependencies
cd ..

echo "Done! check marketplace for new version."
