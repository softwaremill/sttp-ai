## Claude Code Sub-Agent

This project includes a specialized **code-duplication-finder** sub-agent for use with [Claude Code](https://claude.com/code). The agent analyzes the codebase for duplicate code at file, function, and block levels using JetBrains MCP tools.

### Usage Examples

**Analyze streaming modules:**
```
Use code-duplication-finder to analyze the streaming modules for duplicated code patterns
```

**Compare specific modules:**
```
Use code-duplication-finder to compare openai/ and claude/ modules for parallel implementations
```

**Find specific patterns:**
```
Use code-duplication-finder to find duplicate error handling logic across the codebase
```

The agent provides a structured report with:
- Executive summary with statistics
- Grouped duplicates (file/function/block level)
- Prioritized refactoring recommendations
- Specific file paths and line numbers

Configuration: `.claude/agents/code-duplication-finder.md`
