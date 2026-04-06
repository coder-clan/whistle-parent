---
inclusion: auto
---

# Coding Best Practices

## Language and Build

- Java source level: 8 (default profile), 17 (Spring Boot 3/4 profiles). Write code compatible with Java 8 unless the target profile is explicitly Spring Boot 3+.
- Build tool: Maven. Multi-module project (`whistle-parent` → `whistle-api`, `whistle`, example modules).
- Run tests with `mvn test -pl whistle` from the project root. The surefire plugin includes `**/*Test.java`, `**/*Tests.java`, and `**/*Properties.java`.
- Property-based tests use jqwik 1.7.4. Name PBT classes with a `Properties` suffix so surefire picks them up.

## Code Style

- Annotate thread-safe classes with `@ThreadSafe` from `net.jcip.annotations`.
- Prefer constructor injection for required dependencies. Use `@Autowired(required = false)` only for genuinely optional beans.
- Keep classes in the existing package structure: `org.coderclan.whistle` (core), `org.coderclan.whistle.api` (public API), `org.coderclan.whistle.rdbms`, `org.coderclan.whistle.mongodb`, `org.coderclan.whistle.exception`.
- Javadoc author tags use the obfuscated email format: `@author aray(dot)chou(dot)cn(at)gmail(dot)com`.

## Comments and Javadoc

- Add Javadoc to all public and protected classes, interfaces, methods, and constructors. Package-private and private members only need Javadoc when the intent is non-obvious.
- Javadoc must include `@param`, `@return`, and `@throws` tags where applicable. Omit tags only when the meaning is self-evident from the name (e.g., a simple getter).
- Use inline comments (`//`) to explain *why*, not *what*. If the code needs a comment to explain what it does, consider refactoring first.
- Add a brief class-level Javadoc describing the responsibility of each class. Reference related classes with `{@link}` tags.
- Do not add noise comments that restate the code (e.g., `// increment counter` above `counter++`).
- Keep comments up to date. A stale comment is worse than no comment.

## Spring Boot Auto-Configuration

- Register auto-configuration classes in both `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (Boot 3+) and `META-INF/spring.factories` (Boot 2).
- Use `@ConditionalOnClass`, `@ConditionalOnBean`, and `@ConditionalOnMissingBean` to guard beans that depend on optional libraries (JDBC drivers, MongoDB, Jackson variants).
- Configuration ordering: use `@AutoConfigureAfter` when a configuration depends on beans from another auto-configuration.

## Logging

Use SLF4J levels in this order: TRACE → DEBUG → INFO → WARN → ERROR.

- TRACE: Very fine-grained diagnostic output — method entry/exit, intermediate variable values, SQL being executed. Use at method entry points and before/after key operations to make debug easier.
- DEBUG: Expected/recoverable situations and operational detail useful during development — feature probe results, serialization output, SQL parameters, cache hits/misses.
- INFO: Normal operational milestones — startup/shutdown events, configuration applied, table created, application name resolved.
- WARN: Unexpected but non-fatal situations — optional feature unavailable, connection failure during a probe, fallback path taken, deprecated usage detected.
- ERROR: Failures that affect core functionality — event persistence failure, unrecoverable state, data corruption.

Always include relevant context variables in log messages using SLF4J placeholders (`{}`). Never concatenate strings in log arguments.

## Error Handling

- Custom exceptions extend `RuntimeException` and live in `org.coderclan.whistle.exception`.
- Provide at least three constructors: no-arg, `(String message)`, and `(String message, Throwable cause)`.
- Log at the appropriate level before throwing or swallowing exceptions.
- Never silently swallow exceptions. At minimum, log at DEBUG level.

## Serialization

- The project supports both Jackson 2 (`com.fasterxml.jackson`) and Jackson 3 (`tools.jackson`). Both are `optional` dependencies.
- Serializer implementations must handle `toJson` and `toEventContent` round-trip faithfully. Write a jqwik property test for any new serializer.
- Use `@Provide` methods to generate arbitrary test data. Prefer `Arbitraries.create()` with `UUID.randomUUID()` for ID fields.

## Database / Persistence

- RDBMS persistenters extend `AbstractRdbmsEventPersistenter`. Each subclass provides dialect-specific SQL via abstract methods (`getConfirmSql`, `getCreateTableSql`, `getRetrieveSql`).
- Use `DataSourceUtils.getConnection(dataSource)` to participate in Spring-managed transactions. Never close connections obtained this way.
- Table and column names are configurable via `WhistleConfigurationProperties`.

## Property-Based Testing (jqwik)

- Each property test class targets a specific correctness property. Name the class descriptively: `{Feature}{Property}Properties.java`.
- Tag properties with `@Tag("Feature: {feature-name}, Property N: {description}")` for traceability back to spec requirements.
- Keep generators (`@Provide` methods) in the same class as the properties that use them.
- Use `assert` statements (not JUnit assertions) inside `@Property` methods — this is the existing project convention.
- Bound generated values to realistic ranges (e.g., epoch seconds 0 to ~2100 for `Instant`).

## General

- Favor immutability. Use `Collections.unmodifiableMap` / `unmodifiableList` for collections exposed from constructors.
- Avoid raw types. Parameterize all generic types (`Event<C>`, `EventType<C>`).
- Keep public API surface minimal. The `whistle-api` module defines the contracts; implementation details stay in `whistle`.
- When adding a new `EventType` or `EventConsumer`, remember that Whistle does not support producing and consuming the same event type in one application.
