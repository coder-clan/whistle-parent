# Language Requirements

All output must be in English. This includes:

- Documents (specs, requirements, design docs, tasks, reports)
- User stories and acceptance criteria
- Code comments and Javadoc
- Commit messages
- Variable names, method names, and class names
- Log messages and error messages
- Chat responses

# Spec Document Update Strategy

When updating existing spec documents (requirements.md, design.md), follow the "append-then-consolidate" pattern:

1. **During active work**: Append new content as a self-contained addendum section at the end of the document. Do NOT scatter inline edits across the existing document. Small updates to existing sections (e.g., fixing a typo, updating a version number) are acceptable, but new requirements, new design sections, and new correctness properties should be appended.

2. **After all tasks are complete**: When the user explicitly requests it, consolidate the appended content into the main document body for long-term readability.

Rationale: The task execution agent reads these documents to understand what to implement. A self-contained appended section is faster to locate and less error-prone than inline edits scattered across a large file. This also avoids context window issues with large documents.

This rule does NOT apply to tasks.md — tasks are naturally sequential and should be appended as new items at the end.

# Error Handling in Design Documents

Every error handling section in a design document must specify the logging behavior for each error scenario. Include:

- The log level (DEBUG, INFO, WARN, ERROR)
- What information is logged (exception message, stack trace, context variables)

Guideline for log levels:
- DEBUG: Expected/recoverable situations (e.g., feature probe failures, optional feature not available)
- INFO: Normal operational events (e.g., startup results, configuration applied)
- WARN: Unexpected but non-fatal situations (e.g., connection failure during optional probe, missing resource)
- ERROR: Failures that affect core functionality (e.g., event persistence failure, unrecoverable state)

The implementation must match the logging specification in the design doc.
