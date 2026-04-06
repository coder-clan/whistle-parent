# Requirements Document

## Introduction

Whistle is a reliable event delivering and consuming library for Java Spring Boot applications. It solves the data consistency problem in Event-Driven Architecture (EDA) by persisting events to the database within the same transaction as business data, then publishing events to a message broker (RabbitMQ or Kafka) after the transaction commits. Events are confirmed via publisher acknowledgments, and unconfirmed events are periodically retried. The system provides a clean API for publishing and consuming events, with automatic Spring Boot auto-configuration, support for multiple databases (MySQL, PostgreSQL, Oracle, H2, MongoDB), and JSON-based event content serialization via Jackson.

## Glossary

- **Whistle**: The reliable event delivering and consuming library for Spring Boot applications.
- **Event_Service**: The core service interface through which application code publishes events.
- **Event_Type**: An interface representing a category of event, identified by a globally unique name and associated with a specific Event_Content class.
- **Event_Content**: A serializable marker interface representing the payload data of an event.
- **Abstract_Event_Content**: A base class implementing Event_Content that provides an idempotent ID (UUID) and a creation timestamp for deduplication and ordering.
- **Event_Consumer**: An interface that application code implements to consume events of a specific Event_Type. Implementations must be thread-safe and handle duplicate messages.
- **Event_Persistenter**: The internal component responsible for persisting events to a database, confirming delivered events, and retrieving unconfirmed events for retry.
- **Event_Sender**: The internal component that sends events to the message broker via Spring Cloud Stream using a Reactor Flux-based approach.
- **Transactional_Event_Handler**: The internal component that queues events in a ThreadLocal during an active database transaction and sends them via Event_Sender after the transaction commits.
- **Failed_Event_Retrier**: A scheduled component that periodically retrieves unconfirmed persisted events from the database and re-sends them via Event_Sender.
- **Event_Content_Serializer**: The component responsible for serializing Event_Content to JSON and deserializing JSON back to Event_Content objects.
- **Event_Type_Registrar**: The component that collects and indexes all registered Event_Type instances (from both producers and consumers) into an immutable lookup map.
- **Service_Activators**: The component that handles acknowledgment messages from the message broker (RabbitMQ confirm or Kafka record metadata) and error messages.
- **Message_Broker**: The external messaging system (RabbitMQ or Kafka) used for event transport via Spring Cloud Stream.
- **Persistent_Event_Table**: The database table (default name: `sys_event_out`) used to persist outgoing events for reliability.
- **Application_Name**: A globally unique identifier for a service, used as the consumer group name in Spring Cloud Stream.
- **Jackson_2**: The Jackson JSON library version 2.x with package prefix `com.fasterxml.jackson`, used by Spring Boot 2.x and 3.x.
- **Jackson_3**: The Jackson JSON library version 3.x with package prefix `tools.jackson`, used by Spring Boot 4.x as the default JSON library.

## Requirements

### Requirement 1: Event Publishing with Transactional Guarantee

**User Story:** As a developer, I want to publish events within a database transaction, so that events are only delivered after the business data is committed and are never lost.

#### Acceptance Criteria

1. WHEN a developer calls `Event_Service.publishEvent()` while a database transaction is active, THE Event_Service SHALL persist the event to the Persistent_Event_Table within the same database transaction and queue the event for sending after the transaction commits.
2. WHEN the database transaction commits successfully, THE Transactional_Event_Handler SHALL send all queued events for that transaction to the Event_Sender.
3. WHEN the database transaction rolls back, THE Transactional_Event_Handler SHALL discard all queued events for that transaction without sending them.
4. WHEN a developer calls `Event_Service.publishEvent()` while no database transaction is active, THE Event_Service SHALL send the event directly via Event_Sender without persisting the event to the database.
5. THE Transactional_Event_Handler SHALL use a ThreadLocal queue to isolate events between concurrent transactions.

### Requirement 2: Event Type Registration and Validation

**User Story:** As a developer, I want to define and register event types, so that the system can route events correctly and prevent naming conflicts.

#### Acceptance Criteria

1. THE Event_Type_Registrar SHALL collect all Event_Type instances from both publishing event type collections and Event_Consumer beans at application startup.
2. THE Event_Type_Registrar SHALL store all registered Event_Type instances in an immutable map keyed by Event_Type name.
3. IF two different Event_Type instances share the same name, THEN THE Event_Type_Registrar SHALL throw a DuplicatedEventTypeException.
4. THE Event_Type interface SHALL require each implementation to provide a globally unique name via `getName()` and an associated Event_Content class via `getContentType()`.
5. WHEN the application produces and consumes the same Event_Type, THE Whistle_Configuration SHALL throw an IllegalStateException at startup, because producing and consuming the same Event_Type in a single application is not supported.

### Requirement 3: Event Persistence for RDBMS

**User Story:** As a developer, I want events to be persisted to a relational database, so that unconfirmed events can be retried and data consistency is maintained.

#### Acceptance Criteria

1. WHEN an event is persisted, THE RDBMS_Event_Persistenter SHALL insert a row into the Persistent_Event_Table containing the event type name and the JSON-serialized event content, and return the generated database primary key as the persistent event ID.
2. WHEN an event delivery is confirmed, THE RDBMS_Event_Persistenter SHALL update the corresponding row in the Persistent_Event_Table to mark the event as successfully delivered.
3. WHEN unconfirmed events are retrieved for retry, THE RDBMS_Event_Persistenter SHALL select events from the Persistent_Event_Table that are not marked as successful and whose update time is older than 10 seconds, increment the retry count for each retrieved event, and return a batch of up to 32 events.
4. WHEN the database supports SKIP LOCKED (MySQL 8.0+, PostgreSQL 9.5+, Oracle 9.0+, MariaDB 10.3+, H2 1.5+), THE RDBMS_Event_Persistenter SHALL use `FOR UPDATE SKIP LOCKED` in the retrieval query to avoid blocking on locked rows.
5. WHEN the database does not support SKIP LOCKED, THE RDBMS_Event_Persistenter SHALL use `FOR UPDATE` with deterministic ordering by retried_count ascending and ID descending (`ORDER BY retried_count ASC, id DESC`) to deprioritize repeatedly-failing events and reduce deadlock risk.
6. THE RDBMS_Event_Persistenter SHALL automatically create the Persistent_Event_Table at startup if the table does not exist.
7. THE Event_Persistenter SHALL use the database connection managed by Spring's DataSourceUtils to participate in the current transaction.

### Requirement 4: Event Persistence for MongoDB

**User Story:** As a developer, I want events to be persisted to MongoDB, so that applications using MongoDB can benefit from the same reliable event delivery mechanism.

#### Acceptance Criteria

1. WHEN an event is persisted, THE MongoDB_Event_Persistenter SHALL insert a MongoEvent document into the `sys_event_out` collection containing the event type, event content, a `confirmed` field set to false, and a `retry` field set to 0, and return the generated document ID.
2. WHEN an event delivery is confirmed, THE MongoDB_Event_Persistenter SHALL update the corresponding MongoEvent document to set the `confirmed` field to true.
3. WHEN unconfirmed events are retrieved for retry, THE MongoDB_Event_Persistenter SHALL query for documents where `confirmed` is false, ordered by retry count ascending then ID descending, limited to 32 documents, and increment the retry counter for all retrieved documents.
4. THE MongoDB_Event_Persistenter SHALL use a compound partial index on `retry` ascending and `_id` descending (filtered for `confirmed:false`) to optimize retrieval of unconfirmed events with the correct sort order.

### Requirement 5: Database Auto-Configuration

**User Story:** As a developer, I want the correct database persistenter to be automatically configured based on the database driver present on the classpath, so that I do not need manual configuration.

#### Acceptance Criteria

1. WHEN the MySQL JDBC driver (`com.mysql.cj.jdbc.Driver`) is on the classpath and a DataSource bean exists, THE Whistle_Configuration SHALL create a MysqlEventPersistenter bean.
2. WHEN the PostgreSQL JDBC driver (`org.postgresql.Driver`) is on the classpath and a DataSource bean exists, THE Whistle_Configuration SHALL create a PostgresqlEventPersistenter bean.
3. WHEN the Oracle JDBC driver (`oracle.jdbc.OracleDriver`) is on the classpath and a DataSource bean exists, THE Whistle_Configuration SHALL create an OracleEventPersistenter bean.
4. WHEN the H2 JDBC driver (`org.h2.Driver`) is on the classpath and a DataSource bean exists, THE Whistle_Configuration SHALL create an H2EventPersistenter bean.
5. WHEN MongoClientSettings is available and Spring Data MongoDB is on the classpath, THE Whistle_MongoDB_Configuration SHALL create a MongodbEventPersistenter bean.
6. THE Whistle_Configuration SHALL use `@ConditionalOnMissingBean` on all persistenter beans, allowing developers to provide custom implementations that override the defaults.

### Requirement 6: Failed Event Retry

**User Story:** As a developer, I want unconfirmed events to be automatically retried, so that transient failures in message broker communication do not result in lost events.

#### Acceptance Criteria

1. WHEN the application has started and an Event_Persistenter bean is available, THE Failed_Event_Retrier SHALL start a scheduled task that periodically retrieves unconfirmed events from the database and re-sends them via Event_Sender.
2. THE Failed_Event_Retrier SHALL use a configurable delay interval (default: 10 seconds) between retry cycles, controlled by the `org.coderclan.whistle.retryDelay` configuration property.
3. WHEN a retry cycle retrieves a full batch of 32 events, THE Failed_Event_Retrier SHALL immediately perform another retrieval cycle until fewer than 32 events are returned.
4. IF an exception occurs during a retry cycle, THEN THE Failed_Event_Retrier SHALL log the error and continue scheduling subsequent retry cycles.
5. WHEN no Event_Persistenter bean is available, THE Failed_Event_Retrier SHALL not start the scheduled retry task.

### Requirement 7: Event Sending via Spring Cloud Stream

**User Story:** As a developer, I want events to be sent to the message broker using Spring Cloud Stream, so that the system supports both RabbitMQ and Kafka without code changes.

#### Acceptance Criteria

1. THE Event_Sender SHALL use a Reactor Sinks.Many to buffer outgoing events and expose them as a Flux of Spring Messaging Messages.
2. WHEN an event is sent, THE Event_Sender SHALL create a Spring Message containing the Event_Content as payload, the Event_Type name as the `spring.cloud.stream.sendto.destination` header, and the persistent event ID as a custom header.
3. THE Whistle_Configuration SHALL register a Spring Cloud Stream Supplier bean (`cloudStreamSupplier`) that returns the Event_Sender's Flux, enabling Spring Cloud Stream to poll and publish messages to the Message_Broker.
4. THE Event_Sender SHALL use synchronized access to the Reactor Sink to ensure thread safety when multiple threads emit events concurrently.

### Requirement 8: Message Broker Acknowledgment Handling

**User Story:** As a developer, I want the system to handle delivery confirmations from the message broker, so that successfully delivered events are marked as confirmed in the database.

#### Acceptance Criteria

1. WHEN a RabbitMQ publisher confirm is received with `amqp_publishConfirm=true` and a persistent event ID header is present, THE Service_Activators SHALL call `Event_Persistenter.confirmEvent()` with the persistent event ID.
2. WHEN a Kafka record metadata header (`kafka_recordMetadata`) is present and non-null and a persistent event ID header is present, THE Service_Activators SHALL call `Event_Persistenter.confirmEvent()` with the persistent event ID.
3. WHEN an error message is received on the error channel, THE Service_Activators SHALL log the error.
4. THE Whistle default configuration SHALL enable RabbitMQ publisher confirms (`spring.rabbitmq.publisher-confirm-type=correlated`) and configure the acknowledgment channel to `coderclan-whistle-ack-channel`.
5. THE Whistle default configuration SHALL configure Kafka to send record metadata to the `coderclan-whistle-ack-channel` for acknowledgment processing.

### Requirement 9: Event Content Serialization

**User Story:** As a developer, I want event content to be serialized to JSON and deserialized back to typed objects, so that events can be persisted and transmitted as text.

#### Acceptance Criteria

1. THE Jackson_Event_Content_Serializer SHALL serialize any Event_Content object to a JSON string using Jackson ObjectMapper.
2. THE Jackson_Event_Content_Serializer SHALL deserialize a JSON string back to the correct Event_Content subclass using the content type class provided by the Event_Type.
3. IF serialization fails, THEN THE Jackson_Event_Content_Serializer SHALL throw an EventContentSerializationException.
4. IF deserialization fails, THEN THE Jackson_Event_Content_Serializer SHALL throw an EventContentSerializationException.
5. FOR ALL valid Event_Content objects, serializing to JSON then deserializing back SHALL produce an equivalent Event_Content object (round-trip property).
6. THE Whistle_Configuration SHALL use `@ConditionalOnMissingBean` on the Event_Content_Serializer bean, allowing developers to provide a custom serializer.

### Requirement 10: Event Consumption

**User Story:** As a developer, I want to implement event consumers as Spring beans, so that the system automatically discovers and wires them to receive events from the message broker.

#### Acceptance Criteria

1. THE Whistle_Configuration SHALL discover all Spring beans implementing Event_Consumer at startup and register them as Spring Cloud Stream function bindings.
2. WHEN registering event consumers, THE Whistle_Configuration SHALL set the Spring Cloud Stream function binding for each consumer to the Event_Type name returned by `EventConsumer.getSupportEventType()`.
3. THE Whistle_Configuration SHALL set the `spring.cloud.stream.default.group` property to the Application_Name, ensuring that only one instance in a consumer group receives each event.
4. WHEN an Event_Consumer's `consume()` method returns false, THE Event_Consumer default `accept()` method SHALL throw a ConsumerException.
5. WHEN an Event_Consumer's `consume()` method throws an exception, THE Event_Consumer default `accept()` method SHALL wrap the exception in a ConsumerException and re-throw it.

### Requirement 11: Application Configuration

**User Story:** As a developer, I want to configure Whistle behavior through standard Spring Boot configuration properties, so that I can tune the system for different environments.

#### Acceptance Criteria

1. THE Whistle_Configuration_Properties SHALL expose a `retryDelay` property (default: 10 seconds) under the `org.coderclan.whistle` prefix to control the interval between failed event retry cycles.
2. THE Whistle_Configuration_Properties SHALL expose an `applicationName` property under the `org.coderclan.whistle` prefix, defaulting to `spring.application.name` if not explicitly set.
3. THE Whistle_Configuration_Properties SHALL expose a `persistentTableName` property (default: `sys_event_out`) under the `org.coderclan.whistle` prefix to control the database table name for event persistence.
4. IF the Application_Name is null or empty at startup, THEN THE Whistle_Configuration SHALL throw an IllegalStateException.
5. THE Whistle_Configuration_Properties SHALL support the deprecated property `org.coderclan.whistle.table.producedEvent` as a fallback for `persistentTableName` for backward compatibility.

### Requirement 12: Spring Boot Auto-Configuration

**User Story:** As a developer, I want Whistle to auto-configure itself when added as a dependency, so that minimal setup is required to start using the library.

#### Acceptance Criteria

1. THE Whistle module SHALL register `WhistleConfiguration` and `WhistleMongodbConfiguration` as auto-configuration classes via both `spring.factories` (Spring Boot 2.x) and `AutoConfiguration.imports` (Spring Boot 3.x).
2. THE Whistle_Configuration SHALL auto-configure after `DataSourceAutoConfiguration` and `WhistleMongodbConfiguration`.
3. THE Whistle_Configuration SHALL provide default beans for Event_Service, Transactional_Event_Handler, Event_Type_Registrar, Service_Activators, Event_Sender, and Event_Content_Serializer, each guarded by `@ConditionalOnMissingBean`.
4. THE Whistle_Configuration SHALL load default Spring Cloud Stream properties from `spring-cloud-stream.properties`, including RabbitMQ publisher confirm settings, Kafka auto-create topics, Kafka minimum partition count of 8, and consumer dead-letter queue configuration.

### Requirement 13: Idempotent Event Content

**User Story:** As a developer, I want each event to carry a unique idempotent ID and timestamp, so that consumers can detect and handle duplicate events.

#### Acceptance Criteria

1. THE Abstract_Event_Content SHALL generate a UUID-based idempotent ID upon construction.
2. THE Abstract_Event_Content SHALL record the creation timestamp (as `java.time.Instant`) upon construction.
3. THE Abstract_Event_Content SHALL implement `equals()` and `hashCode()` based solely on the idempotent ID, so that two event content instances with the same idempotent ID are considered equal.

### Requirement 14: Thread Safety

**User Story:** As a developer, I want the core Whistle components to be thread-safe, so that the library works correctly in multi-threaded Spring Boot applications.

#### Acceptance Criteria

1. THE Event_Service_Impl SHALL be thread-safe, as annotated with `@ThreadSafe`.
2. THE Event_Type_Registrar SHALL be thread-safe by using an immutable map for event type storage, as annotated with `@ThreadSafe`.
3. THE Transactional_Event_Handler SHALL be thread-safe by using ThreadLocal for per-transaction event queuing, as annotated with `@ThreadSafe`.
4. THE Event_Sender SHALL use synchronized access to the Reactor Sink to ensure thread-safe event emission.
5. THE RDBMS_Event_Persistenter implementations SHALL be thread-safe, as annotated with `@ThreadSafe`.

### Requirement 15: Java and Spring Boot Compatibility

**User Story:** As a developer, I want Whistle to be compatible with Java 8+ and Spring Boot 2 through Spring Boot 4, so that the library can be used across projects with different technology stacks.

#### Acceptance Criteria

1. THE Whistle library SHALL be compatible with Java 8 and all subsequent Java LTS versions (Java 11, Java 17, Java 21, Java 25).
2. THE Whistle library SHALL be compatible with Spring Boot 2.x (2.6.4+), Spring Boot 3.x, and Spring Boot 4.x.
3. THE Whistle library SHALL NOT use any Java API introduced after Java 8 in its public API interfaces (`whistle-api` module).
4. THE Whistle library SHALL register auto-configuration classes via both `spring.factories` (Spring Boot 2.x) and `AutoConfiguration.imports` (Spring Boot 3.x / 4.x) to support all major versions.
5. THE Whistle library's current codebase SHALL have been verified to work correctly on Spring Boot 2, Spring Boot 3, and Spring Boot 4 environments.
6. WHEN running on Spring Boot 4.x (Spring Framework 7), THE Whistle library SHALL resolve all relocated auto-configuration class references to their new package paths.

### Requirement 16: javax/jakarta Namespace Compatibility

**User Story:** As a developer, I want Whistle to support both javax and jakarta namespace annotations, so that it works correctly on Spring Boot 2.x (javax) and Spring Boot 3.x/4.x (jakarta).

#### Acceptance Criteria

1. WHEN running on Spring Boot 2.x or 3.x, THE Whistle library SHALL support both `javax.annotation.PostConstruct` and `jakarta.annotation.PostConstruct` lifecycle annotations.
2. WHEN running on Spring Boot 4.x (Spring Framework 7), THE Whistle library SHALL use only `jakarta.annotation.PostConstruct`, because Spring Framework 7 only processes `jakarta.annotation.PostConstruct` and `javax.annotation.PostConstruct` is not on the default classpath.
3. THE Whistle module SHALL declare `jakarta.annotation-api` as a `provided` scope dependency, so that it is available at compile time but does not force a transitive dependency on applications.
4. THE `javax.sql.DataSource` usage SHALL NOT require any javax/jakarta migration handling, as it is part of the Java SE standard library (`java.sql` module) and is unaffected by the Jakarta EE namespace change.
5. THE Whistle library SHALL NOT use any other javax.* APIs from Java EE (e.g., `javax.inject`, `javax.persistence`) that would require jakarta migration, relying solely on Spring Framework annotations for dependency injection and configuration.
6. WHEN targeting Spring Boot 4.x compatibility, THE Whistle_Configuration SHALL remove the `@javax.annotation.PostConstruct` annotation and retain only `@jakarta.annotation.PostConstruct`.

### Requirement 17: MongoDB Custom Converters

**User Story:** As a developer using MongoDB, I want Event_Type objects to be correctly serialized and deserialized when stored in MongoDB documents, so that event type information is preserved across persistence operations.

#### Acceptance Criteria

1. THE Whistle_MongoDB_Configuration SHALL register an EventType2StringConverter that converts Event_Type objects to their string name for MongoDB storage.
2. THE Whistle_MongoDB_Configuration SHALL register a String2EventTypeConverter that converts string names back to Event_Type objects using the Event_Type_Registrar during MongoDB reads.
3. THE Whistle_MongoDB_Configuration SHALL register these converters as MongoCustomConversions, conditional on MongoClientSettings being available.

### Requirement 18: Spring Boot 2.x / 3.x / 4.x Multi-Version Auto-Configuration

**User Story:** As a developer, I want Whistle to auto-configure itself on Spring Boot 2.x, 3.x, and 4.x without any additional manual setup.

#### Acceptance Criteria

1. THE Whistle module SHALL provide `META-INF/spring.factories` for Spring Boot 2.x auto-configuration discovery.
2. THE Whistle module SHALL provide `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` for Spring Boot 3.x and 4.x auto-configuration discovery.
3. BOTH auto-configuration registration files SHALL reference the same configuration classes (`WhistleConfiguration` and `WhistleMongodbConfiguration`).
4. THE auto-configuration SHALL function identically on Spring Boot 2.x, Spring Boot 3.x, and Spring Boot 4.x, with no behavioral differences.
5. WHEN running on Spring Boot 4.x, THE Whistle module SHALL rely solely on `AutoConfiguration.imports` for auto-configuration discovery, because `spring.factories` is no longer used for auto-configuration registration in Spring Boot 4.

### Requirement 19: Spring Boot 4 Auto-Configuration Class Path Migration

**User Story:** As a developer, I want Whistle's auto-configuration class references to use the correct package paths in Spring Boot 4, so that the application starts normally without requiring manual exclusion of configuration classes.

#### Acceptance Criteria

1. WHEN running on Spring Boot 4.x, THE Whistle_Configuration SHALL reference `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration` in its `@AutoConfigureAfter` annotation, instead of the relocated `org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration`.
2. WHEN running on Spring Boot 4.x, THE Whistle_MongoDB_Configuration SHALL reference `org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration` in its `@AutoConfigureAfter` and `@ConditionalOnClass` annotations, instead of the relocated `org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration`.
3. THE Whistle library SHALL ensure that both WhistleConfiguration and WhistleMongodbConfiguration can be loaded by the Spring Boot 4 auto-configuration mechanism without throwing `ClassNotFoundException` or requiring manual exclusion.
4. IF the old auto-configuration class path is not found at runtime, THEN THE Whistle library SHALL gracefully fall back to the new package path, or use a version-adaptive strategy to reference the correct class.

### Requirement 20: Jackson 3 Compatibility (Spring Boot 4)

**User Story:** As a developer, I want Whistle's JSON serialization components to be compatible with Jackson 3 (`tools.jackson` package), so that they work correctly in Spring Boot 4 environments.

#### Acceptance Criteria

1. WHEN running on Spring Boot 4.x, THE Jackson_Event_Content_Serializer SHALL use Jackson 3 (`tools.jackson.databind.ObjectMapper`) for event content serialization and deserialization, because Spring Boot 4 defaults to Jackson 3 and no longer includes Jackson 2 (`com.fasterxml.jackson`) on the classpath.
2. WHEN running on Spring Boot 2.x or 3.x, THE Jackson_Event_Content_Serializer SHALL continue to use Jackson 2 (`com.fasterxml.jackson.databind.ObjectMapper`) for backward compatibility.
3. THE Whistle_Configuration SHALL NOT expose Jackson 2 class references (`com.fasterxml.jackson.databind.ObjectMapper`) in its method signatures when running on Spring Boot 4.x, to avoid `NoClassDefFoundError` during reflection-based bean introspection.
4. FOR ALL valid Event_Content objects, serializing to JSON then deserializing back using either Jackson 2 or Jackson 3 SHALL produce an equivalent Event_Content object (round-trip property).
5. THE Whistle library SHALL provide a version-adaptive mechanism (e.g., separate configuration classes or conditional bean definitions) to select the appropriate Jackson version at runtime based on classpath availability.

### Requirement 21: Spring Cloud Stream 5.0 Functional Programming Model Compatibility

**User Story:** As a developer, I want Whistle to continue working correctly with Spring Cloud Stream 5.0 (the version paired with Spring Boot 4), without being affected by removed annotations.

#### Acceptance Criteria

1. THE Whistle library SHALL NOT use `@EnableBinding` or `@StreamListener` annotations, because these annotations have been removed in Spring Cloud Stream 5.0.
2. THE Whistle library SHALL use the functional programming model (`Supplier<Flux<Message>>` and `Consumer<Message>`) for Spring Cloud Stream integration, which is compatible with Spring Cloud Stream 4.x and 5.0.
3. WHEN running on Spring Boot 4.x with Spring Cloud 2025.1.0, THE Whistle library SHALL function correctly with the Spring Cloud Stream RabbitMQ and Kafka binders without referencing any removed or relocated binder classes.
4. THE Whistle library SHALL NOT directly reference `org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration`, because this class has been relocated to `org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration` in Spring Boot 4.


---

## Addendum: Bugfix — Retrieve SQL Missing ORDER BY for SKIP LOCKED and NOWAIT Paths

### Introduction

In `AbstractRdbmsEventPersistenter.buildRetrieveSql()`, the `ORDER BY retried_count ASC, id DESC` clause is only applied when the database does not support SKIP LOCKED or NOWAIT (the plain `FOR UPDATE` fallback path). The SKIP LOCKED and NOWAIT paths call `getBaseRetrieveSql()` which returns SQL without any ordering. This means on databases that support SKIP LOCKED (MySQL 8.0+, PostgreSQL 9.5+, Oracle 9.0+, H2 1.5+) or NOWAIT, poison events with high retry counts are retrieved with equal priority to fresh events, potentially blocking normal event processing.

### Bug Analysis

#### Current Behavior (Defect)

1.1 WHEN the database supports SKIP LOCKED, THEN the system generates a retrieve SQL using `getBaseRetrieveSql()` which contains NO `ORDER BY` clause, causing events with high `retried_count` (poison events) to be retrieved with equal priority to fresh events.

1.2 WHEN the database supports NOWAIT but not SKIP LOCKED, THEN the system generates a retrieve SQL using `getBaseRetrieveSql()` which contains NO `ORDER BY` clause, causing events with high `retried_count` (poison events) to be retrieved with equal priority to fresh events.

#### Expected Behavior (Correct)

2.1 WHEN the database supports SKIP LOCKED, THEN the system SHALL generate a retrieve SQL using `getOrderedBaseRetrieveSql()` which includes `ORDER BY retried_count ASC, id DESC`, so that fresh events (low retry count) are prioritized over poison events (high retry count).

2.2 WHEN the database supports NOWAIT but not SKIP LOCKED, THEN the system SHALL generate a retrieve SQL using `getOrderedBaseRetrieveSql()` which includes `ORDER BY retried_count ASC, id DESC`, so that fresh events (low retry count) are prioritized over poison events (high retry count).

2.3 WHEN the database supports neither SKIP LOCKED nor NOWAIT, THEN the system SHALL CONTINUE TO generate a retrieve SQL using `getOrderedBaseRetrieveSql()` with `ORDER BY retried_count ASC, id DESC` and append `FOR UPDATE`.

#### Unchanged Behavior (Regression Prevention)

3.1 WHEN the database supports SKIP LOCKED, THEN the system SHALL CONTINUE TO append `FOR UPDATE SKIP LOCKED` to the retrieve SQL.

3.2 WHEN the database supports NOWAIT but not SKIP LOCKED, THEN the system SHALL CONTINUE TO append `FOR UPDATE NOWAIT` to the retrieve SQL.

3.3 WHEN the database supports neither SKIP LOCKED nor NOWAIT, THEN the system SHALL CONTINUE TO append `FOR UPDATE` to the retrieve SQL.

3.4 WHEN unconfirmed events are retrieved for retry, THEN the system SHALL CONTINUE TO return a batch of up to 32 events with incremented retry counts.

### Bug Condition

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type LockingStrategy
  OUTPUT: boolean

  // Returns true when the database supports SKIP LOCKED or NOWAIT,
  // i.e., the paths that currently use unordered getBaseRetrieveSql()
  RETURN X.supportsSkipLocked = true OR X.supportsNowait = true
END FUNCTION
```

### Property Specification

```pascal
// Property: Fix Checking — All locking paths use ordered SQL
FOR ALL X WHERE isBugCondition(X) DO
  retrieveSql ← buildRetrieveSql'(RETRY_BATCH_COUNT)
  ASSERT retrieveSql CONTAINS "ORDER BY retried_count ASC, id DESC"
END FOR
```

### Preservation Goal

```pascal
// Property: Preservation Checking — Non-buggy path unchanged
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT buildRetrieveSql(X) = buildRetrieveSql'(X)
  // Both use getOrderedBaseRetrieveSql() + " for update"
END FOR

// Property: Preservation Checking — Locking clauses unchanged
FOR ALL X DO
  IF X.supportsSkipLocked THEN
    ASSERT buildRetrieveSql'(X) ENDS WITH "for update skip locked"
  ELSE IF X.supportsNowait THEN
    ASSERT buildRetrieveSql'(X) ENDS WITH "for update nowait"
  ELSE
    ASSERT buildRetrieveSql'(X) ENDS WITH "for update"
  END IF
END FOR
```
