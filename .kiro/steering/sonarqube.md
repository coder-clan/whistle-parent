---
inclusion: auto
---

# SonarQube Code Quality Standards

When writing or modifying Java code, follow these SonarQube-aligned rules to minimize issues reported by static analysis.

## Bug Prevention

- Never use `==` or `!=` to compare objects; use `.equals()` instead (except for null checks and enums).
- Always close `AutoCloseable` resources with try-with-resources.
- Do not ignore return values of methods like `String.replace()`, `File.delete()`, etc.
- Avoid `NullPointerException` risks: check for null before dereferencing, or use `Optional`.

## Code Smells

- No unused imports, variables, parameters, or private methods.
- No empty catch blocks — always log at minimum DEBUG level or rethrow.
- Extract duplicate string literals (3+ occurrences) into constants.
- Keep cognitive complexity per method at or below 15. Refactor complex methods into smaller helpers.
- Avoid deeply nested control flow (max 3 levels). Use early returns or extract methods.
- Do not use raw types in generics — always parameterize.
- Prefer `StringBuilder` over string concatenation in loops.
- Use `logger` (SLF4J) instead of `System.out` / `System.err` / `printStackTrace()`.

## Security Hotspots

- Never hard-code credentials, tokens, or passwords.
- Sanitize user input before using it in SQL, logging, or file paths.
- Use parameterized queries (PreparedStatement) — never concatenate user input into SQL strings.
- Avoid exposing stack traces or internal details in error responses.

## Reliability

- Override `hashCode()` when overriding `equals()`.
- Do not swallow `InterruptedException` — restore the interrupt flag with `Thread.currentThread().interrupt()`.
- Synchronize access to shared mutable state, or use concurrent data structures.
- Ensure `finally` blocks do not throw exceptions that mask the original exception.

## Maintainability

- Keep methods under 60 lines. Extract logic into well-named helper methods.
- Keep classes focused on a single responsibility.
- Limit method parameter count to 7. Use a parameter object for more.
- Avoid boolean parameters that change method behavior — split into two methods instead.
- Use `@Override` annotation on all overriding methods.
