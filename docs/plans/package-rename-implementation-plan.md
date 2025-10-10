# Package Renaming Implementation Plan: sttp.openai → sttp.ai.openai

## Overview

This plan implements **Option A** (no backward compatibility) from [GitHub Issue #418](https://github.com/softwaremill/sttp-ai/issues/418), renaming `sttp.openai.*` to `sttp.ai.openai.*` to achieve package naming consistency with the Claude module (`sttp.ai.claude.*`).

**Scope:**
- ~150 Scala files with package declarations
- ~140 files in core (86 main, 54 test)
- ~10 files in streaming modules
- Build configuration (organization, artifact names)
- Documentation (README, CLAUDE.md, examples)
- **Excluded:** Migration guide, CI/CD (as requested)

**Version Impact:** This is a breaking change requiring a major version bump (1.0.0).

---

## Phase 1: Physical Directory Migration

**Objective:** Move all source files from `sttp/openai/` to `sttp/ai/openai/` directory structure.

### 1.1 Core Module - Main Sources (86 files)
```bash
# Create new directory structure
mkdir -p core/src/main/scala/sttp/ai/openai

# Move entire openai directory tree
git mv core/src/main/scala/sttp/openai/* core/src/main/scala/sttp/ai/openai/
```

**Affected directories:**
- `core/src/main/scala/sttp/openai/` → `core/src/main/scala/sttp/ai/openai/`
  - Including 29 subdirectories: json/, requests/admin/, requests/assistants/, etc.

### 1.2 Core Module - Test Sources (54 files)
```bash
# Create new test directory structure
mkdir -p core/src/test/scala/sttp/ai/openai

# Move entire test directory tree
git mv core/src/test/scala/sttp/openai/* core/src/test/scala/sttp/ai/openai/
```

### 1.3 Streaming Modules
For each streaming module (fs2, zio, akka, pekko, ox):

```bash
# Main sources
mkdir -p streaming/{module}/src/main/scala/sttp/ai/openai/streaming/{module}
git mv streaming/{module}/src/main/scala/sttp/openai/streaming/{module}/* \
     streaming/{module}/src/main/scala/sttp/ai/openai/streaming/{module}/

# Test sources
mkdir -p streaming/{module}/src/test/scala/sttp/ai/openai/streaming/{module}
git mv streaming/{module}/src/test/scala/sttp/openai/streaming/{module}/* \
     streaming/{module}/src/test/scala/sttp/ai/openai/streaming/{module}/
```

**Modules to process:** fs2, zio, akka, pekko, ox

### 1.4 Validation
```bash
# Verify no files remain in old location
find . -path "*/sttp/openai/*" -type f -name "*.scala"
# Should return: 0 files

# Verify files exist in new location
find . -path "*/sttp/ai/openai/*" -type f -name "*.scala" | wc -l
# Should return: ~150 files
```

---

## Phase 2: Package Declaration Updates

**Objective:** Update all `package sttp.openai` declarations to `package sttp.ai.openai`.

### 2.1 Global Package Declaration Update
```bash
# Update all package declarations in Scala files
find . -name "*.scala" -type f -exec sed -i '' 's/^package sttp\.openai/package sttp.ai.openai/g' {} +
```

### 2.2 Manual Verification of Edge Cases
Check files with complex package declarations:
- Nested package statements
- Package objects
- Files with multiple package declarations

### 2.3 Validation
```bash
# Verify no old package declarations remain
grep -r "^package sttp\.openai" --include="*.scala" .
# Should return: 0 results

# Verify new package declarations exist
grep -r "^package sttp\.ai\.openai" --include="*.scala" . | wc -l
# Should return: ~150 results

# Check streaming packages specifically
grep -r "^package sttp\.ai\.openai\.streaming" --include="*.scala" streaming/
# Should return results for all 5 streaming modules
```

---

## Phase 3: Import Statement Updates

**Objective:** Update all `import sttp.openai` statements to `import sttp.ai.openai`.

### 3.1 Core Module Imports
```bash
# Update all imports in core module
find core/src -name "*.scala" -type f -exec sed -i '' 's/import sttp\.openai\./import sttp.ai.openai./g' {} +
find core/src -name "*.scala" -type f -exec sed -i '' 's/import sttp\.openai\*/import sttp.ai.openai*/g' {} +
```

### 3.2 Streaming Module Imports
```bash
# Update imports in all streaming modules
find streaming -name "*.scala" -type f -exec sed -i '' 's/import sttp\.openai\./import sttp.ai.openai./g' {} +
find streaming -name "*.scala" -type f -exec sed -i '' 's/import sttp\.openai\*/import sttp.ai.openai*/g' {} +
```

### 3.3 Examples Module Imports
```bash
# Update imports in examples (5 files)
find examples/src -name "*.scala" -type f -exec sed -i '' 's/import sttp\.openai\./import sttp.ai.openai./g' {} +
find examples/src -name "*.scala" -type f -exec sed -i '' 's/import sttp\.openai\*/import sttp.ai.openai*/g' {} +
```

### 3.4 Validation
```bash
# Verify no old imports remain
grep -r "import sttp\.openai\." --include="*.scala" . | grep -v "sttp\.ai\.openai"
# Should return: 0 results

# Verify new imports exist
grep -r "import sttp\.ai\.openai" --include="*.scala" . | wc -l
# Should return: ~125+ results
```

---

## Phase 4: Build Configuration Updates

**Objective:** Update organization, artifact names, and module references in build files.

### 4.1 Update build.sbt

**Line 12:** Update organization
```scala
// OLD: organization := "com.softwaremill.sttp.openai"
// NEW:
organization := "com.softwaremill.sttp.ai"
```

**Line 17:** Update root project name
```scala
// OLD: name := "sttp-openai"
// NEW:
name := "sttp-ai"
```

**Line 122:** Update mdoc task output path
```scala
// OLD: (docs.jvm(scala3.head) / mdoc).toTask(" --out target/sttp-openai-docs").value
// NEW:
(docs.jvm(scala3.head) / mdoc).toTask(" --out target/sttp-ai-docs").value
```

**Line 129:** Update docs module name
```scala
// OLD: moduleName := "sttp-openai-docs"
// NEW:
moduleName := "sttp-ai-docs"
```

### 4.2 Validation
```bash
# Compile all modules to verify build configuration
sbt clean compile

# Expected: Successful compilation across all modules
```

---

## Phase 5: Documentation Updates

**Objective:** Update all documentation files with new package names.

### 5.1 README.md (64 occurrences)

Update scala-cli dependency declarations:
```scala
// OLD: //> using dep com.softwaremill.sttp.openai::core:0.3.10
// NEW: //> using dep com.softwaremill.sttp.ai::core:1.0.0

// OLD: //> using dep com.softwaremill.sttp.openai::fs2:0.3.10
// NEW: //> using dep com.softwaremill.sttp.ai::fs2:1.0.0
```

Update all import statements in code examples:
```scala
// OLD: import sttp.openai.*
// NEW: import sttp.ai.openai.*

// OLD: import sttp.openai.streaming.fs2.*
// NEW: import sttp.ai.openai.streaming.fs2.*
```

**Automated update:**
```bash
sed -i '' 's/com\.softwaremill\.sttp\.openai::/com.softwaremill.sttp.ai::/g' README.md
sed -i '' 's/import sttp\.openai\./import sttp.ai.openai./g' README.md
sed -i '' 's/import sttp\.openai\*/import sttp.ai.openai*/g' README.md
```

### 5.2 CLAUDE.md (9 occurrences)

Update project overview and examples:
```bash
sed -i '' 's/sttp-openai/sttp-ai/g' CLAUDE.md
sed -i '' 's/sttp\.openai/sttp.ai.openai/g' CLAUDE.md
```

Manually review sections:
- Project Overview
- Project Structure
- Examples referring to OpenAI package paths

### 5.3 INTEGRATION_TESTING.md (1 occurrence)
```bash
sed -i '' 's/com\.softwaremill\.sttp\.openai/com.softwaremill.sttp.ai/g' INTEGRATION_TESTING.md
sed -i '' 's/sttp\.openai/sttp.ai.openai/g' INTEGRATION_TESTING.md
```

### 5.4 model_update_scripts/README.md
```bash
sed -i '' 's/sttp\.openai/sttp.ai.openai/g' model_update_scripts/README.md
```

### 5.5 Examples - Scala-cli Dependencies
Update dependency declarations in example files:

**Files to update:**
- `examples/src/main/scala/examples/ChatProxy.scala`
- `examples/src/main/scala/examples/StrictStructuredFunctionCallingExample.scala`

```bash
find examples/src/main/scala -name "*.scala" -exec sed -i '' \
  's/com\.softwaremill\.sttp\.openai::/com.softwaremill.sttp.ai::/g' {} +
```

### 5.6 Validation
```bash
# Verify no old references remain in documentation
grep -r "sttp\.openai" --include="*.md" . | grep -v "sttp\.ai\.openai"
# Should return: 0 results (except historical mentions in CHANGELOG if exists)

# Verify no old artifact IDs in examples
grep -r "com\.softwaremill\.sttp\.openai::" --include="*.scala" examples/
# Should return: 0 results
```

---

## Phase 6: Comprehensive Testing & Validation

**Objective:** Ensure all changes compile, tests pass, and code is properly formatted.

### 6.1 Clean Build
```bash
sbt clean
sbt compile
```

**Expected:** All modules compile successfully without errors.

### 6.2 Run All Tests
```bash
sbt test
```

**Expected:** All unit tests pass (excluding integration tests).

### 6.3 Run Integration Tests (if API keys available)
```bash
# Set environment variables
export OPENAI_API_KEY="your-key"
export ANTHROPIC_API_KEY="your-key"

# Run integration tests
sbt "testOnly *OpenAIIntegrationSpec"
```

### 6.4 Code Formatting
```bash
# Format all code (CRITICAL!)
sbt scalafmtAll

# Verify formatting
sbt scalafmtCheck
sbt Test/scalafmtCheck
```

**Alternative (if JetBrains MCP available):**
Use `mcp__jetbrains__reformat_file` for each modified file.

### 6.5 Documentation Compilation
```bash
sbt compileDocumentation
```

**Expected:** README.md examples compile successfully with mdoc.

### 6.6 Verify Examples Run
```bash
# Test an example (requires OPENAI_KEY)
cd examples/src/main/scala/examples
scala-cli run ChatProxy.scala
```

---

## Phase 7: Final Verification Checklist

### 7.1 Directory Structure Audit
```bash
# Ensure old directories are completely removed
find . -path "*/src/*/scala/sttp/openai" -type d
# Should return: 0 directories

# Verify new structure exists
find . -path "*/src/*/scala/sttp/ai/openai" -type d | wc -l
# Should return: ~10+ directories
```

### 7.2 Package/Import Audit
```bash
# No old package declarations
grep -r "^package sttp\.openai[^.]" --include="*.scala" .
# Result: 0

# No old import statements
grep -r "import sttp\.openai[^.]" --include="*.scala" . | grep -v "ai.openai"
# Result: 0
```

### 7.3 Build Configuration Audit
```bash
# Check organization in build.sbt
grep "organization :=" build.sbt
# Should show: com.softwaremill.sttp.ai

# Check for any remaining "sttp-openai" references
grep -r "sttp-openai" build.sbt
# Result: 0 (except comments if any)
```

### 7.4 Documentation Audit
```bash
# Check all .md files
grep -r "com\.softwaremill\.sttp\.openai::" --include="*.md" .
# Result: 0

# Check scala-cli directives in examples
grep "//> using dep" examples/src/main/scala/examples/*.scala
# All should reference: com.softwaremill.sttp.ai::
```

---

## Risk Mitigation & Rollback Strategy

### Potential Issues

1. **Git mv complications:** If directories aren't empty, git mv may fail
   - **Solution:** Use `git mv -f` or manually move and `git add`

2. **Sed variations:** macOS vs Linux sed syntax differences
   - **macOS:** `sed -i ''`
   - **Linux:** `sed -i`
   - **Solution:** Adjust commands for target platform

3. **Compilation errors:** Missing import updates or package mismatches
   - **Solution:** Run `sbt compile` after each phase; fix errors before proceeding

4. **Formatting issues:** Scalafmt may fail on malformed code
   - **Solution:** Fix syntax errors first, then format

### Rollback Strategy

If critical issues arise:
```bash
# Rollback git changes
git reset --hard HEAD

# Or revert specific commits
git revert <commit-hash>
```

**Recommendation:** Create a feature branch before starting:
```bash
git checkout -b feature/package-rename-sttp-ai-openai
```

---

## Execution Summary

**Total Phases:** 7
**Estimated Files Affected:** ~180
- Scala files: ~150
- Build files: 2 (build.sbt, Dependencies.scala)
- Documentation files: 4+ (.md files)
- Example files: 5

**Critical Success Factors:**
1. ✅ Execute phases sequentially with validation after each
2. ✅ Run `sbt scalafmtAll` after Phase 6
3. ✅ Ensure all tests pass before finalizing
4. ✅ Use git commits between phases for safety

**Final Deliverable:**
A fully renamed codebase with `sttp.ai.openai.*` package structure, consistent with the Claude module, ready for 1.0.0 release.