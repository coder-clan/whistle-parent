# Implementation Plan: Probe-Based Lock Feature Detection

## Overview

Replace version-based detection of SQL locking clause support with a probe-based approach. Create `LockFeatureProbe`, refactor `AbstractRdbmsEventPersistenter` to use probes and own all locking logic, simplify subclasses to provide only base queries, add configurable transaction timeout, and remove all version parsing code.

## Tasks

- [x] 1. Create LockFeatureProbe utility class
  - [x] 1.1 Create `LockFeatureProbe.java` in `whistle/src/main/java/org/coderclan/whistle/rdbms/`
    - Implement `public static boolean probeFeature(DataSource dataSource, String tableName, String clause)`
    - Use `SELECT * FROM <table> WHERE 1=0 FOR UPDATE <clause>` as probe SQL
    - Open a separate connection via `dataSource.getConnection()`, set autoCommit false
    - Roll back on success, catch `SQLException` and return false on failure
    - Close connection via try-with-resources regardless of outcome
    - Log clause name and exception message at DEBUG level on probe failure
    - Log at ERROR level if DataSource cannot provide a connection
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 2.4, 2.5, 7.1_

  - [x] 1.2 Write property test: Probe is side-effect-free (Property 1)
    - **Property 1: Probe is side-effect-free**
    - **Validates: Requirements 1.4, 2.1, 2.3**
    - Create `LockFeatureProbeProperties.java` in test directory
    - Use embedded H2 DataSource; insert random rows, run `probeFeature` with arbitrary clause strings, assert row count unchanged

  - [x] 1.3 Write property test: Probe always returns boolean, never throws
    - **Property 1 (corollary): Probe never throws**
    - **Validates: Requirements 2.4, 2.5**
    - For any clause string (valid or invalid), `probeFeature` returns true or false without throwing

- [x] 2. Add `retrieveTransactionTimeout` to WhistleConfigurationProperties
  - [x] 2.1 Add `retrieveTransactionTimeout` field to `WhistleConfigurationProperties.java`
    - Add `private int retrieveTransactionTimeout = 5` with getter and setter
    - Property path: `org.coderclan.whistle.retrieve-transaction-timeout`
    - _Requirements: 9.1, 9.2_

- [x] 3. Refactor AbstractRdbmsEventPersistenter
  - [x] 3.1 Remove version-based detection code from `AbstractRdbmsEventPersistenter.java`
    - Remove `initMetaData()` method
    - Remove `detectSkipLockedSupport()` method
    - Remove `versionGreaterThan()` method
    - Remove `dbProductName` and `dbProductVersion` fields
    - Remove `getRetrieveSql(int count, boolean skipLockedSupported)` abstract method
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

  - [x] 3.2 Add new abstract methods and probe-based constructor logic
    - Add `protected abstract String getBaseRetrieveSql(int count)` — subclasses return SELECT/WHERE/LIMIT without locking clause or ORDER BY
    - Add `protected abstract String getOrderedBaseRetrieveSql(int count)` — subclasses return SELECT/WHERE/ORDER BY/LIMIT without locking clause
    - Add `supportsNowait` private field alongside existing `supportsSkipLocked`
    - Add `retrieveTransactionTimeout` private field, received via constructor parameter
    - Update constructor signature to accept `int retrieveTransactionTimeout`
    - Implement constructor flow: `createTable()` → probe SKIP LOCKED → probe NOWAIT → `buildRetrieveSql()`
    - Implement `private String buildRetrieveSql(int count)`: if supportsSkipLocked → baseQuery + `FOR UPDATE SKIP LOCKED`; else if supportsNowait → baseQuery + `FOR UPDATE NOWAIT`; else → orderedBaseQuery + `FOR UPDATE`
    - Make `supportsSkipLocked`, `supportsNowait`, and `retrieveSql` final (set once, never mutated)
    - Log selected locking strategy at INFO level during startup
    - If `retrieveTransactionTimeout` is 0, log at INFO level that timeout is disabled
    - _Requirements: 1.1, 1.2, 3.1, 3.2, 3.3, 3.4, 4.1, 4.2, 4.3, 4.4, 5.3, 7.2, 7.3, 8.1, 8.2, 8.3, 9.6_

  - [x] 3.3 Add query timeout to `retrieveUnconfirmedEvent`
    - Call `statement.setQueryTimeout(this.retrieveTransactionTimeout)` before executing the retrieve SQL
    - Catch `SQLException` from timeout and return empty list, logging at ERROR level with timeout value
    - _Requirements: 9.3, 9.4, 9.5_

  - [x] 3.4 Write property test: Locking clause priority selection (Property 2)
    - **Property 2: Locking clause priority selection**
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 4.1, 4.2, 4.3**
    - For any combination of `(supportsSkipLocked, supportsNowait)`, verify `buildRetrieveSql` selects the correct clause following strict priority

  - [x] 3.5 Write property test: Retrieve SQL always contains FOR UPDATE (Property 3)
    - **Property 3: Retrieve SQL always contains FOR UPDATE**
    - **Validates: Requirements 4.1, 4.2, 4.3**
    - For any combination of probe results, the constructed retrieveSql always contains the substring `FOR UPDATE`

- [x] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Simplify subclass implementations
  - [x] 5.1 Refactor `H2EventPersistenter`
    - Replace `getRetrieveSql(int, boolean)` with `getBaseRetrieveSql(int)` and `getOrderedBaseRetrieveSql(int)`
    - `getBaseRetrieveSql`: return `SELECT ... WHERE ... LIMIT n` (no locking clause, no ORDER BY)
    - `getOrderedBaseRetrieveSql`: return `SELECT ... WHERE ... ORDER BY update_time, id LIMIT n` (no locking clause)
    - Update constructor to pass `retrieveTransactionTimeout`
    - _Requirements: 5.1, 5.2_

  - [x] 5.2 Refactor `MysqlEventPersistenter`
    - Replace `getRetrieveSql(int, boolean)` with `getBaseRetrieveSql(int)` and `getOrderedBaseRetrieveSql(int)`
    - `getBaseRetrieveSql`: return `SELECT ... WHERE ... LIMIT n` (no locking clause, no ORDER BY)
    - `getOrderedBaseRetrieveSql`: return `SELECT ... WHERE ... ORDER BY update_time, id LIMIT n` (no locking clause)
    - Update constructor to pass `retrieveTransactionTimeout`
    - _Requirements: 5.1, 5.2_

  - [x] 5.3 Refactor `PostgresqlEventPersistenter`
    - Replace `getRetrieveSql(int, boolean)` with `getBaseRetrieveSql(int)` and `getOrderedBaseRetrieveSql(int)`
    - `getBaseRetrieveSql`: return `SELECT ... WHERE ... LIMIT n` (no locking clause, no ORDER BY)
    - `getOrderedBaseRetrieveSql`: return `SELECT ... WHERE ... ORDER BY update_time, id LIMIT n` (no locking clause)
    - Update constructor to pass `retrieveTransactionTimeout`
    - _Requirements: 5.1, 5.2_

  - [x] 5.4 Refactor `OracleEventPersistenter`
    - Replace `getRetrieveSql(int, boolean)` with `getBaseRetrieveSql(int)` and `getOrderedBaseRetrieveSql(int)`
    - `getBaseRetrieveSql`: return `SELECT ... WHERE ...` (no LIMIT — Oracle uses FETCH FIRST or ROWNUM, no locking clause)
    - `getOrderedBaseRetrieveSql`: return `SELECT ... WHERE ... ORDER BY update_time, id` (with FETCH FIRST, no locking clause)
    - Update constructor to pass `retrieveTransactionTimeout`
    - _Requirements: 5.1, 5.2_

  - [x] 5.5 Write property test: Base queries contain no locking clauses (Property 4)
    - **Property 4: Base queries contain no locking clauses**
    - **Validates: Requirements 5.1, 5.2**
    - For any positive count value, `getBaseRetrieveSql(count)` and `getOrderedBaseRetrieveSql(count)` do not contain `FOR UPDATE`, `SKIP LOCKED`, or `NOWAIT`

- [x] 6. Update WhistleConfiguration bean wiring
  - [x] 6.1 Update `WhistleConfiguration.java` bean factory methods
    - Pass `this.properties.getRetrieveTransactionTimeout()` to each RDBMS persistenter constructor (H2, MySQL, PostgreSQL, Oracle)
    - _Requirements: 9.2, 9.6_

- [x] 7. Checkpoint - Ensure all tests pass
  - [x] 7.1 Write property test: Query timeout is always applied (Property 5)
    - **Property 5: Query timeout is always applied**
    - **Validates: Requirements 9.1, 9.2, 9.3**
    - For any positive `retrieveTransactionTimeout` value, verify `setQueryTimeout` is called with that value before query execution
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Property tests use jqwik 1.7.4 (already on test classpath) and embedded H2
- Test classes must follow `**/*Properties.java` naming for Surefire discovery
- All code is Java, targeting the `whistle` module
