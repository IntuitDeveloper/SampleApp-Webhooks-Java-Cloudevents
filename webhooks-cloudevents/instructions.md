# Webhooks CloudEvents Migration — Prompt Library Instructions

## Directory Structure

```
webhooks-cloudevents/
├── prompt-template.md          # The prompt with {{variables}} — DO NOT edit for per-run changes
├── prompt-config-java.json     # Java/Spring Boot config — all variable values
├── merge-prompt.js             # Build script — resolves {{vars}}, generates ready-prompt.md
├── instructions.md             # This file — usage, testing, adding new languages
└── ready-prompt.md             # Generated output — paste this into the AI (gitignored)
```

## Quick Start

```bash
# Generate the resolved prompt for Java
node merge-prompt.js

# Or specify a different config
node merge-prompt.js prompt-config-nodejs.json
```

The script will:
1. Read `prompt-template.md` and the specified config
2. Replace all `{{variables}}` with config values
3. Write `ready-prompt.md`
4. Report resolved/unresolved variable counts

## Usage

1. **Copy the contents of `ready-prompt.md`**
2. **Paste into your AI coding assistant** (Windsurf Cascade, Cursor, GitHub Copilot Chat, etc.)
3. The AI will execute Tasks 0-6 sequentially, waiting for your confirmation between tasks

## Testing Checklist

After running the prompt against a codebase, verify:

### Task 0 — References & Framework
- [ ] All reference URLs resolved (no `{{variables}}` in output)
- [ ] Framework correctly detected from build files
- [ ] SDK version identified

### Task 1 — Analysis
- [ ] All 7 analysis items reported with file:line citations
- [ ] Signature verification method identified
- [ ] Content-Type handling checked
- [ ] Data field usage assessed

### Task 1.5 — State Detection
- [ ] Correct state classified (LEGACY ONLY / CLOUDEVENTS ONLY / DUAL FORMAT / NO HANDLER)
- [ ] Routing table followed — irrelevant tasks skipped

### Task 2 — SDK & Parsing
- [ ] SDK version verified or upgrade suggested
- [ ] Before/after code snippets provided for each change
- [ ] getData() handling recommendation given (a/b/c)

### Task 2.5 — Idempotency
- [ ] Deduplication implementation provided
- [ ] Placement: after signature verification, before downstream processing
- [ ] Storage approach appropriate for the application

### Task 3 — Dual-Format
- [ ] Format detection logic provided (CloudEvents array vs legacy object)
- [ ] Normalization model matches the required field table
- [ ] Both parsers route correctly

### Task 4 — Content-Type (415)
- [ ] Endpoint checked for consumes restriction
- [ ] Fix provided if needed, or confirmed as already handled

### Task 5 — Testing
- [ ] curl commands are copy-pastable with shell variables
- [ ] HMAC signature generation command included
- [ ] All test scenarios covered: CloudEvents, legacy, idempotency, negatives
- [ ] Expected status codes and log output stated

### Task 6 — Rollout
- [ ] Deploy → Enable → Monitor → Revert → Cleanup steps provided
- [ ] Revert mechanism is one-click (Developer Portal toggle)

## Adding a New Language

1. Copy `prompt-config-java.json` to `prompt-config-<language>.json`
2. Update all values:
   - `language_framework` — e.g., "Node.js / Express"
   - `sdk_source` — e.g., "https://github.com/intuit/QuickBooks-V3-NodeJS-SDK"
   - `sdk_minimum_version` — check the SDK repo for CloudEvents support
   - `sdk_cloudevents_class` — the class/type name in that SDK
   - `sdk_data_package` / `sdk_service_package` — package paths
   - `content_type_fix` — framework-specific fix for 415
   - `typing_system` — e.g., "TypeScript interfaces"
3. Run `node merge-prompt.js prompt-config-<language>.json`
4. Verify `ready-prompt.md` has zero unresolved variables

## Prompt Versions

| Version | Date | Changes |
|---|---|---|
| v3.1 | 2026-03-13 | Initial prompt — single file, all {{vars}} |
| v3.2 | 2026-03-13 | Added: Task 0 (refs/framework), Task 1.5 (state routing), Task 2.5 (idempotency), ROPE examples, hardcoded URLs, error response guidance |
| v4.0 | 2026-03-13 | 3-file architecture (template + config + merge script), all values configurable, cross-language support |
