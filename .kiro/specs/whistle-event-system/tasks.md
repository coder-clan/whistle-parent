# Implementation Plan: Spring Boot 4 / Java 25 Compatibility

## Overview

Implement the 4 design areas from the "Addendum: Spring Boot 4 / Java 25 Compatibility Design" to make Whistle compatible with Spring Boot 4.x (Spring Framework 7), Jackson 3, and Java 25. This covers auto-configuration class path migration, Jackson 2/3 dual-support, javax/jakarta annotation handling, and Spring Cloud Stream 5.0 verification.

## Tasks

- [x] 1. Auto-Configuration Class Path Migration (Design 1: B1, B2, M2)
  - [x] 1.1 Update WhistleConfiguration @AutoConfigureAfter to use string-based name attribute
    - Replace `@AutoConfigureAfter({DataSourceAutoConfiguration.class, WhistleMongodbConfiguration.class})` with `@AutoConfigureAfter(name = {"org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration", "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration", "org.coderclan.whistle.WhistleMongodbConfiguration"})`
    - Remove the `import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration` import statement
    - _Requirements: 15.6, 19.1, 19.4_

  - [x] 1.2 Update WhistleMongodbConfiguration @AutoConfigureAfter to use string-based name attribute
    - Replace `@AutoConfigureAfter(MongoAutoConfiguration.class)` with `@AutoConfigureAfter(name = {"org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration", "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration"})`
    - Remove the `import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration` import statement
    - Keep `@ConditionalOnClass(MongoCustomConversions.class)` unchanged (stable across versions)
    - _Requirements: 15.6, 19.2, 19.4_

  - [x] 1.3 Write property test for version-adaptive auto-configuration class resolution
    - **Property 28: Version-adaptive auto-configuration class resolution**
    - Verify that `@AutoConfigureAfter` annotations on both WhistleConfiguration and WhistleMongodbConfiguration specify both old (Spring Boot 2.x/3.x) and new (Spring Boot 4.x) package paths using string-based `name` attributes
    - **Validates: Requirements 15.6, 19.1, 19.2, 19.4**

- [x] 2. Checkpoint - Verify auto-configuration migration compiles
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. Jackson 2/3 Dual-Support (Design 2: B3, B4)
  - [x] 3.1 Create Jackson3EventContentSerializer class
    - Create `whistle/src/main/java/org/coderclan/whistle/Jackson3EventContentSerializer.java`
    - Implement `EventContentSerializer` interface using `tools.jackson.databind.ObjectMapper`
    - Use `tools.jackson.core.JacksonException` for exception handling
    - Mirror the structure of existing `JacksonEventContentSerializer` but with Jackson 3 APIs
    - _Requirements: 20.1, 20.4_

  - [x] 3.2 Create WhistleJacksonConfiguration with dual Jackson inner classes
    - Create `whistle/src/main/java/org/coderclan/whistle/WhistleJacksonConfiguration.java`
    - Outer class: `@Configuration` with NO Jackson imports, annotated with `@AutoConfigureBefore(name = "org.coderclan.whistle.WhistleConfiguration")` to ensure the serializer bean is available before persistenter beans that depend on it
    - Static inner class `Jackson2Configuration`: `@Configuration` + `@ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")` + `@Bean @ConditionalOnMissingBean(EventContentSerializer.class)` creating `JacksonEventContentSerializer`
    - Static inner class `Jackson3Configuration`: `@Configuration` + `@ConditionalOnClass(name = "tools.jackson.databind.ObjectMapper")` + `@Bean @ConditionalOnMissingBean(EventContentSerializer.class)` creating `Jackson3EventContentSerializer`
    - _Requirements: 20.1, 20.2, 20.5_

  - [x] 3.3 Add Jackson 2 and Jackson 3 as optional Maven dependencies
    - Add `com.fasterxml.jackson.core:jackson-databind` as `<optional>true</optional>` dependency in `whistle/pom.xml` (currently transitive via Spring Boot; making it explicit and optional ensures `JacksonEventContentSerializer` compiles)
    - Add `tools.jackson.databind:jackson-databind` as `<optional>true</optional>` dependency in `whistle/pom.xml` (required for `Jackson3EventContentSerializer` to compile; optional so it does not propagate to consumers)
    - _Requirements: 20.1, 20.2_

  - [x] 3.4 Remove eventContentSerializer() bean method from WhistleConfiguration
    - Remove the `eventContentSerializer(@Autowired ObjectMapper objectMapper)` method from `WhistleConfiguration`
    - Remove the `import com.fasterxml.jackson.databind.ObjectMapper` import statement
    - This eliminates Jackson 2 class references from WhistleConfiguration method signatures, preventing `NoClassDefFoundError` during reflection on Spring Boot 4
    - _Requirements: 20.3_

  - [x] 3.5 Register WhistleJacksonConfiguration in AutoConfiguration.imports
    - Add `org.coderclan.whistle.WhistleJacksonConfiguration` to `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
    - Add `org.coderclan.whistle.WhistleJacksonConfiguration` to `META-INF/spring.factories` for Spring Boot 2.x compatibility
    - _Requirements: 18.3, 20.5_

  - [x] 3.6 Write property test for Jackson version-adaptive serializer selection
    - **Property 29: Jackson version-adaptive serializer selection**
    - Verify that exactly one `EventContentSerializer` bean is activated based on classpath: Jackson 2 on Spring Boot 2.x/3.x, Jackson 3 on Spring Boot 4.x, and neither when a custom serializer is provided
    - **Validates: Requirements 20.1, 20.2, 20.5**

  - [x] 3.7 Write property test for Jackson 3 serialization round trip
    - **Property 30: Jackson 3 serialization round trip preserves EventContent**
    - Verify that for any valid EventContent object, serializing to JSON then deserializing back using Jackson 3 produces an object equal to the original
    - **Validates: Requirements 20.4**

  - [x] 3.8 Write property test for no Jackson class references in WhistleConfiguration
    - **Property 31: WhistleConfiguration has no Jackson class references in method signatures**
    - Use reflection to verify that no method in `WhistleConfiguration` has parameter types or return types referencing `com.fasterxml.jackson.*` or `tools.jackson.*`
    - **Validates: Requirements 20.3**

- [x] 4. Checkpoint - Verify Jackson dual-support compiles
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. javax/jakarta Annotation Handling (Design 3: M1)
  - [x] 5.1 Replace @PostConstruct with InitializingBean in WhistleConfiguration
    - Make `WhistleConfiguration` implement `InitializingBean` (in addition to existing `ApplicationContextAware`)
    - Rename `init()` method to `afterPropertiesSet()` (or have `afterPropertiesSet()` call the existing init logic)
    - Remove both `@PostConstruct` and `@jakarta.annotation.PostConstruct` annotations
    - Remove `import javax.annotation.PostConstruct` import statement
    - This ensures cross-version compatibility with Spring Boot 2.x, 3.x, and 4.x without any javax/jakarta annotation dependency
    - _Requirements: 16.1, 16.2, 16.6_

  - [x] 5.2 Write property test for functional programming model verification
    - **Property 32: Functional programming model for Spring Cloud Stream**
    - Verify that Whistle source code does not reference `@EnableBinding`, `@StreamListener`, or `RabbitAutoConfiguration` classes
    - **Validates: Requirements 21.1, 21.2, 21.4**

- [x] 6. Final checkpoint - Ensure all changes compile and tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 7. Maven Profile-Based Multi-Version Verification
  - [ ] 7.1 Add Spring Boot 2/3/4 Maven profiles to root pom.xml
    - Add three profiles: `spring-boot-2` (default), `spring-boot-3`, `spring-boot-4`
    - Each profile sets `spring-boot.version`, `spring-cloud.version`, `maven.compiler.source`, `maven.compiler.target`, and `spring-boot-maven-plugin.version`
    - Add `spring-boot-dependencies` BOM import in dependencyManagement to override parent-managed versions
  - [ ] 7.2 Update example project POMs to use property-based spring-boot-maven-plugin version
    - Replace hardcoded `<version>2.6.4</version>` in spring-boot-maven-plugin with `${spring-boot-maven-plugin.version}`
    - Applies to: whistle-example-producer, whistle-example-consumer
  - [ ] 7.3 Verify compilation under each profile
    - Run `mvn compile -Pspring-boot-2`, `mvn compile -Pspring-boot-3`, `mvn compile -Pspring-boot-4`

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Design 4 (Spring Cloud Stream 5.0 Compatibility) requires no code changes; verification is covered by Property 32 in task 5.2
- Each task references specific requirements for traceability
- Property tests validate the 5 new correctness properties (28-32) from the design addendum
- The implementation language is Java, matching the existing codebase

---

## Addendum: Bugfix — Retrieve SQL Missing ORDER BY for SKIP LOCKED and NOWAIT Paths

### Overview

Fix `AbstractRdbmsEventPersistenter.buildRetrieveSql()` so that all three locking paths (SKIP LOCKED, NOWAIT, plain FOR UPDATE) use `getOrderedBaseRetrieveSql()` instead of only the fallback path. Then remove the now-dead `getBaseRetrieveSql()` abstract method and all four subclass overrides. Update existing tests and add new property tests for the fix.

### Tasks

- [x] 8. Write bug condition exploration test
  - **Property 1: Bug Condition** - All Locking Paths Use Ordered SQL
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate the bug exists
  - **Scoped PBT Approach**: Scope the property to cases where `isBugCondition(X)` is true: `supportsSkipLocked=true` OR (`supportsSkipLocked=false` AND `supportsNowait=true`)
  - Create test class `whistle/src/test/java/org/coderclan/whistle/OrderedSqlBugConditionProperties.java`
  - Use the same `TestPersistenter` pattern and reflection approach from `LockingClausePriorityProperties` to set `supportsSkipLocked`/`supportsNowait` fields and invoke `buildRetrieveSql(int)`
  - **Property 33: Bug Condition — All locking paths use ordered SQL**
  - For any `(supportsSkipLocked, supportsNowait, count)` where `supportsSkipLocked=true` OR `supportsNowait=true`, assert `buildRetrieveSql(count)` contains `order by retried_count asc, id desc`
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists: SKIP LOCKED and NOWAIT paths produce SQL without ORDER BY)
  - Document counterexamples found (e.g., `supportsSkipLocked=true, count=32` produces SQL without `order by`)
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 2.1, 2.2_

- [x] 9. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Locking Clauses Unchanged After Fix
  - **IMPORTANT**: Follow observation-first methodology
  - Create test class `whistle/src/test/java/org/coderclan/whistle/LockingClausePreservationProperties.java`
  - Use the same `TestPersistenter` pattern and reflection approach from `LockingClausePriorityProperties`
  - **Property 34: Preservation — Locking clauses unchanged after fix**
  - Observe on UNFIXED code: `buildRetrieveSql()` with `supportsSkipLocked=true` ends with `for update skip locked`
  - Observe on UNFIXED code: `buildRetrieveSql()` with `supportsNowait=true, supportsSkipLocked=false` ends with `for update nowait`
  - Observe on UNFIXED code: `buildRetrieveSql()` with both false ends with `for update`
  - Write property-based test: for all `(supportsSkipLocked, supportsNowait, count)` combinations, verify the correct locking clause suffix is present
  - Verify test passes on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline locking clause behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3_

- [x] 10. Fix buildRetrieveSql() and remove dead code

  - [x] 10.1 Fix buildRetrieveSql() to use getOrderedBaseRetrieveSql() for all paths
    - In `AbstractRdbmsEventPersistenter.buildRetrieveSql(int count)`, replace `getBaseRetrieveSql(count)` with `getOrderedBaseRetrieveSql(count)` in both the SKIP LOCKED and NOWAIT branches
    - _Bug_Condition: isBugCondition(input) where input.supportsSkipLocked = true OR input.supportsNowait = true_
    - _Expected_Behavior: buildRetrieveSql(count) CONTAINS "order by retried_count asc, id desc" for ALL (supportsSkipLocked, supportsNowait) combinations_
    - _Preservation: Locking clause suffixes unchanged — "for update skip locked", "for update nowait", "for update"_
    - _Requirements: 1.1, 1.2, 2.1, 2.2, 2.3, 3.1, 3.2, 3.3_

  - [x] 10.2 Remove getBaseRetrieveSql() abstract method and all subclass overrides
    - Remove the abstract `getBaseRetrieveSql(int count)` method declaration and its Javadoc from `AbstractRdbmsEventPersistenter`
    - Remove `getBaseRetrieveSql(int count)` override from `MysqlEventPersistenter`
    - Remove `getBaseRetrieveSql(int count)` override from `PostgresqlEventPersistenter`
    - Remove `getBaseRetrieveSql(int count)` override from `OracleEventPersistenter`
    - Remove `getBaseRetrieveSql(int count)` override from `H2EventPersistenter`
    - _Requirements: 2.1, 2.2_

  - [x] 10.3 Update existing test LockingClausePriorityProperties
    - In `LockingClausePriorityProperties.lockingClauseFollowsStrictPriority()`, update the SKIP LOCKED branch: remove the assertion `!sqlLower.contains("order by")` and add assertion `sqlLower.contains("order by")`
    - In the same method, update the NOWAIT branch: remove the assertion `!sqlLower.contains("order by")` and add assertion `sqlLower.contains("order by")`
    - Remove the `getBaseRetrieveSql(int count)` override from the inner `TestPersistenter` class
    - _Requirements: 2.1, 2.2, 3.1, 3.2, 3.3_

  - [x] 10.4 Update existing test BaseQueryNoLockingClauseProperties
    - Remove the `baseRetrieveSqlContainsNoLockingClauses` test method (tests `getBaseRetrieveSql()` which no longer exists)
    - Remove the `getBaseRetrieveSql(int count)` override from the inner `TestPersistenter` class
    - Keep the `orderedBaseRetrieveSqlContainsNoLockingClauses` test method (still valid)
    - _Requirements: 2.1, 2.2_

  - [x] 10.5 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - All Locking Paths Use Ordered SQL
    - **IMPORTANT**: Re-run the SAME test from task 8 - do NOT write a new test
    - The test from task 8 encodes the expected behavior (ORDER BY present in all paths)
    - When this test passes, it confirms the expected behavior is satisfied
    - Run bug condition exploration test from step 8
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 2.1, 2.2_

  - [x] 10.6 Verify preservation tests still pass
    - **Property 2: Preservation** - Locking Clauses Unchanged After Fix
    - **IMPORTANT**: Re-run the SAME tests from task 9 - do NOT write new tests
    - Run preservation property tests from step 9
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions in locking clause behavior)
    - Confirm all tests still pass after fix (no regressions)

- [x] 11. Write property test for dead code removal verification
  - **Property 35: Dead code removal — getBaseRetrieveSql() removed**
  - Create test class `whistle/src/test/java/org/coderclan/whistle/DeadCodeRemovalProperties.java`
  - Use reflection to verify that `getBaseRetrieveSql(int)` is NOT declared on `AbstractRdbmsEventPersistenter`, `MysqlEventPersistenter`, `PostgresqlEventPersistenter`, `OracleEventPersistenter`, or `H2EventPersistenter`
  - Verify that `getOrderedBaseRetrieveSql(int)` IS still declared on `AbstractRdbmsEventPersistenter`
  - _Requirements: 2.1, 2.2_

- [x] 12. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.
