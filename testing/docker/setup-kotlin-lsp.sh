#!/bin/bash

set -e

LATEST_TAG_URL="https://api.github.com/repos/Kotlin/kotlin-lsp/tags"
TARGET_DIR="/app/kotlin-lsp"
ZIP_FILE="$TARGET_DIR/kotlin-lsp.zip"
EXECUTABLE="$TARGET_DIR/kotlin-lsp.sh"

fetch_latest_kotlin_lsp_tag() {
    local str=$(curl -s "$LATEST_TAG_URL" | \
        jq -r '[.[].name 
                 | select(startswith("kotlin-lsp/")) 
                 | split("/")[1]] 
                | sort_by(sub("v";"";"") | split(".") | map(tonumber)) 
                | last')

    if [ -z "$str" ] || [ "$str" = "null" ]; then
        echo "Error: Could not fetch latest tag"
        exit 1
    fi

    latest_tag="${str#v}"

    echo "$latest_tag"
}

download_and_extract() {
    local version=$1
    local download_url="https://download-cdn.jetbrains.com/kotlin-lsp/$version/kotlin-$version.zip"

    echo "Downloading Kotlin LSP server version $version..."
    echo "Download URL: $download_url"

    curl -L -o "$ZIP_FILE" "$download_url"

    if [ ! -f "$ZIP_FILE" ]; then
        echo "Error: Failed to download zip file"
        exit 1
    fi

    echo "Extracting Kotlin LSP server..."

    cd "$TARGET_DIR"
    unzip -q kotlin-lsp.zip

    rm kotlin-lsp.zip

    if [ -f "$EXECUTABLE" ]; then
        chmod +x "$EXECUTABLE"
        echo "Kotlin LSP server setup completed successfully"
    else
        echo "Error: kotlin-lsp.sh not found after extraction"
        exit 1
    fi
}

main() {
    mkdir -p "$TARGET_DIR"

    if [ -f "$EXECUTABLE" ]; then
        echo "Kotlin LSP server already exists, skipping download"
        return 0
    fi

    local latest_version=$(fetch_latest_kotlin_lsp_tag)
    download_and_extract "$latest_version"
}

main "$@"
