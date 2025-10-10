#!/bin/sh

# Script to run integration tests for sttp-ai library
# Usage:
# ANTHROPIC_API_KEY=... OPENAI_API_KEY=...  ./run-integration-tests.sh
#
# Or set the OPENAI_API_KEY and ANTHROPIC_API_KEY environment variables and run:
#   ./run-integration-tests.sh
#
# Or create a .env file with the API keys and run:
#   ./run-integration-tests.sh

set -e

# Check if .env file exists and source it
if [ -f ".env" ]; then
  echo "Reading API keys from .env file"
  while read -r line; do
    line="$(echo "${line%%#*}" | xargs)"
    [ -n "$line" ] && export "$line"
  done < .env
fi

# Check which API keys are available
OPENAI_SET=false
ANTHROPIC_SET=false

if [ -n "${OPENAI_API_KEY}" ]; then
    OPENAI_SET=true
    echo "✓ OPENAI_API_KEY is set"
else
    echo "⚠️  OPENAI_API_KEY is not set - OpenAI tests will be skipped"
fi

if [ -n "${ANTHROPIC_API_KEY}" ]; then
    ANTHROPIC_SET=true
    echo "✓ ANTHROPIC_API_KEY is set"
else
    echo "⚠️  ANTHROPIC_API_KEY is not set - Claude tests will be skipped"
fi

if [ "$OPENAI_SET" = false ] && [ "$ANTHROPIC_SET" = false ]; then
    echo ""
    echo "Usage:"
    echo "  1. Create .env file with OPENAI_API_KEY and ANTHROPIC_API_KEY"
    echo "  2. Or set environment variables: export OPENAI_API_KEY=... ANTHROPIC_API_KEY=..."
    echo "  3. Or pass OpenAI key as argument: ./run-integration-tests.sh your-key-here"
    echo ""
    echo "📝 Note: Without API keys, all integration tests will be skipped (not failed)"
    exit 0
fi

echo ""
echo "🧪 Running integration tests..."

# Run the integration tests
sbt "testOnly *OpenAIIntegrationSpec *ClaudeIntegrationSpec"

echo ""
if [ "$OPENAI_SET" = true ] || [ "$ANTHROPIC_SET" = true ]; then
    echo "✅ Integration tests completed!"
else
    echo "✅ Integration tests skipped (no API keys provided)"
fi
