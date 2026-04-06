# Requirements Document

## Introduction

This document specifies the requirements for replacing version-based detection of SQL locking clause support (`SKIP LOCKED`, `NOWAIT`) with a probe-based approach. At startup, the system executes real SQL against the database to determine which locking clauses are supported, eliminating all version parsing logic and working universally across any database. A configurable transaction timeout protects against deadlocks during event retrieval.

## Glossary

- **Probe**: A lightweight SQL statement executed at startup to test whether the database accepts a specific locking clause. Uses `WHERE 1=0` to touch zero rows and always rolls back.
- **LockFeatureProbe**: A utility class responsible for executing probe SQL statements against the database.
- **AbstractRdbmsEventPersistenter**: The base class for all RDBMS-based event persistenters. Owns locking clause logic including probing, strategy selection, and SQL construction.
- **Subclass**: A database-specific implementation (H2, MySQL, PostgreSQL, Oracle) that extends AbstractRdbmsEventPersistenter.
- **Locking_Clause**: One of `SKIP LOCKED`, `NOWAIT`, or plain `FOR UPDATE`, appended to the retrieve SQL.
- **Base_Query**: The database-specific SELECT/WHERE/LIMIT portion of the retrieve SQL, without any locking clause.
- **Ordered_Base_Query**: A variant of Base_Query that includes `ORDER BY update_time, id` for deterministic ordering, used as fallback.
- **Retrieve_SQL**: The final pre-built SQL string combining Base_Query (or Ordered_Base_Query) with the selected Locking_Clause.
- **retrieveTransactionTimeout**: A configurable integer (in seconds) that sets the JDBC query timeout on the `Statement` used in `retrieveUnconfirmedEvent`. Default: 5 seconds. Protects against deadlocks when `SKIP LOCKED` is not available.

## Requirements

### Requirement 1: Probe Execution

**User Story:** As a developer, I want the system to probe the database for locking clause support at startup, so that it uses the strongest available locking strategy without relying on fragile version parsing.

#### Acceptance Criteria

1. WHEN the AbstractRdbmsEventPersistenter is constructed, THE LockFeatureProbe SHALL execute a probe for `SKIP LOCKED` support against the event table
2. WHEN the AbstractRdbmsEventPersistenter is constructed, THE LockFeatureProbe SHALL execute a probe for `NOWAIT` support against the event table
3. WHEN a probe is executed, THE LockFeatureProbe SHALL use `SELECT * FROM <table> WHERE 1=0 FOR UPDATE <clause>` as the probe SQL
4. WHEN a probe is executed, THE LockFeatureProbe SHALL roll back the transaction after execution

### Requirement 2: Probe Safety

**User Story:** As a system operator, I want probes to never modify data or leak connections, so that startup detection is safe in production environments.

#### Acceptance Criteria

1. THE LockFeatureProbe SHALL use `WHERE 1=0` in probe SQL to guarantee zero rows are selected or locked
2. THE LockFeatureProbe SHALL close the database connection via try-with-resources after each probe, regardless of outcome
3. WHEN a probe SQL execution succeeds, THE LockFeatureProbe SHALL roll back the transaction before closing the connection
4. WHEN a probe SQL execution fails with an SQLException, THE LockFeatureProbe SHALL return false and log the clause name and exception message at DEBUG level
5. IF the DataSource cannot provide a connection, THEN THE LockFeatureProbe SHALL return false and log the exception at ERROR level

### Requirement 3: Locking Strategy Selection

**User Story:** As a developer, I want the system to automatically select the strongest available locking clause, so that concurrent event retrieval is as efficient as possible.

#### Acceptance Criteria

1. WHEN `SKIP LOCKED` is supported, THE AbstractRdbmsEventPersistenter SHALL select `SKIP LOCKED` as the Locking_Clause
2. WHEN `SKIP LOCKED` is not supported and `NOWAIT` is supported, THE AbstractRdbmsEventPersistenter SHALL select `NOWAIT` as the Locking_Clause
3. WHEN neither `SKIP LOCKED` nor `NOWAIT` is supported, THE AbstractRdbmsEventPersistenter SHALL select plain `FOR UPDATE` as the Locking_Clause
4. THE AbstractRdbmsEventPersistenter SHALL enforce the priority order: `SKIP LOCKED` > `NOWAIT` > plain `FOR UPDATE`

### Requirement 4: Retrieve SQL Construction

**User Story:** As a developer, I want the base class to own all locking clause logic and SQL construction, so that subclasses only provide database-specific query syntax.

#### Acceptance Criteria

1. WHEN `SKIP LOCKED` is selected, THE AbstractRdbmsEventPersistenter SHALL construct Retrieve_SQL by appending `FOR UPDATE SKIP LOCKED` to the Base_Query
2. WHEN `NOWAIT` is selected, THE AbstractRdbmsEventPersistenter SHALL construct Retrieve_SQL by appending `FOR UPDATE NOWAIT` to the Base_Query
3. WHEN plain `FOR UPDATE` is selected, THE AbstractRdbmsEventPersistenter SHALL construct Retrieve_SQL by appending `FOR UPDATE` to the Ordered_Base_Query
4. THE AbstractRdbmsEventPersistenter SHALL build Retrieve_SQL once during construction and reuse it for all subsequent retrievals

### Requirement 5: Subclass Simplification

**User Story:** As a developer, I want subclasses to provide only the base SELECT query, so that locking logic is not duplicated across database-specific implementations.

#### Acceptance Criteria

1. THE Subclass SHALL implement `getBaseRetrieveSql(int count)` returning the SELECT/WHERE/LIMIT portion without any locking clause or ORDER BY
2. THE Subclass SHALL implement `getOrderedBaseRetrieveSql(int count)` returning the SELECT/WHERE/ORDER BY/LIMIT portion without any locking clause
3. WHEN the AbstractRdbmsEventPersistenter constructs Retrieve_SQL, THE AbstractRdbmsEventPersistenter SHALL call the Subclass methods to obtain the Base_Query or Ordered_Base_Query

### Requirement 6: Version Detection Removal

**User Story:** As a developer, I want all version-based detection logic removed, so that the codebase is simpler and not dependent on version string parsing.

#### Acceptance Criteria

1. THE AbstractRdbmsEventPersistenter SHALL NOT contain the `initMetaData()` method
2. THE AbstractRdbmsEventPersistenter SHALL NOT contain the `detectSkipLockedSupport()` method
3. THE AbstractRdbmsEventPersistenter SHALL NOT contain the `versionGreaterThan()` method
4. THE AbstractRdbmsEventPersistenter SHALL NOT contain the `dbProductName` or `dbProductVersion` fields
5. THE AbstractRdbmsEventPersistenter SHALL NOT contain the `getRetrieveSql(int count, boolean skipLockedSupported)` abstract method

### Requirement 7: Probe Independence and Thread Safety

**User Story:** As a developer, I want probe results to be immutable after construction, so that the system is thread-safe without synchronization.

#### Acceptance Criteria

1. THE LockFeatureProbe SHALL use a separate database connection for each probe execution
2. THE AbstractRdbmsEventPersistenter SHALL set `supportsSkipLocked` and `supportsNowait` fields once during construction and never mutate them afterward
3. THE AbstractRdbmsEventPersistenter SHALL set the `retrieveSql` field once during construction and never mutate it afterward

### Requirement 8: Constructor Execution Order

**User Story:** As a developer, I want a well-defined startup sequence, so that probes run after the table exists and before SQL is built.

#### Acceptance Criteria

1. WHEN the AbstractRdbmsEventPersistenter is constructed, THE AbstractRdbmsEventPersistenter SHALL execute `createTable()` before any probe execution
2. WHEN the AbstractRdbmsEventPersistenter is constructed, THE AbstractRdbmsEventPersistenter SHALL execute probes before building Retrieve_SQL
3. WHEN the AbstractRdbmsEventPersistenter is constructed, THE AbstractRdbmsEventPersistenter SHALL follow the sequence: createTable → probe SKIP LOCKED → probe NOWAIT → buildRetrieveSql

### Requirement 9: Transaction Timeout for Event Retrieval

**User Story:** As a system operator, I want a configurable transaction timeout on event retrieval queries, so that deadlocks from concurrent `FOR UPDATE` statements are bounded and do not block the retrier indefinitely.

#### Acceptance Criteria

1. THE WhistleConfigurationProperties SHALL expose a `retrieveTransactionTimeout` property with a default value of 5 seconds
2. THE WhistleConfigurationProperties SHALL allow the `retrieveTransactionTimeout` to be configured via `org.coderclan.whistle.retrieve-transaction-timeout`
3. WHEN `retrieveUnconfirmedEvent` executes, THE AbstractRdbmsEventPersistenter SHALL call `Statement.setQueryTimeout(retrieveTransactionTimeout)` on the Statement before executing the retrieve SQL
4. WHEN the query timeout is exceeded, THE AbstractRdbmsEventPersistenter SHALL catch the resulting `SQLException` and return an empty list, logging the exception at ERROR level with the timeout value
5. WHEN `retrieveTransactionTimeout` is 0, THE system SHALL disable the query timeout (JDBC spec: unlimited), and log at INFO level during startup that timeout is disabled
6. THE AbstractRdbmsEventPersistenter SHALL receive the `retrieveTransactionTimeout` value via its constructor
