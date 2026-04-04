# Best Practices Research: Transactional Outbox & Event-Driven Systems

This document consolidates best practices from industry sources for reviewing the Whistle event system's requirements and design.

## Sources

1. [Outbox Pattern: Transactional Integrity in Microservices](https://www.developers.dev/tech-talk/the-outbox-pattern-mastering-transactional-integrity-in-microservices-architecture.html) - developers.dev, 2026
2. [Event-Driven Systems Best Practices (2026)](https://tutorialq.com/microservices/patterns/event-drive-systems-best-practices) - tutorialq.com, 2026
3. [Transactional Outbox: Database-Kafka Consistency](https://www.conduktor.io/blog/transactional-outbox-pattern-database-kafka) - conduktor.io, 2025
4. [Transactional Outbox with RabbitMQ Part 2: Retries, DLQ, Observability](https://levelup.gitconnected.com/transactional-outbox-with-rabbitmq-part-2-handling-retries-dead-letter-queues-and-observability-d53217cf45e9) - gitconnected.com, 2025
5. [Event Versioning Strategies](https://oneuptime.com/blog/post/2026-01-30-event-driven-versioning-strategies/view) - oneuptime.com, 2026
6. [Transactional Outbox Pattern - AWS](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html) - AWS Prescriptive Guidance
7. [Schema Versioning in Kafka Events](https://blog.stackademic.com/schema-versioning-in-kafka-events-designing-backward-forward-compatible-payloads-in-spring-b3533ee8dac8) - stackademic.com, 2025
8. [Idempotency Mastery: Robust Event-Driven Recovery](https://openillumi.com/en/en-eda-reliable-recovery-strategy/) - openillumi.com, 2025
9. [Debezium CDC vs Polling for Outbox](https://medium.com/fever-engineering/enhancing-inter-service-communication-the-transactional-outbox-pattern-solution-part-2-5ef97402592b) - medium.com, 2024

Content was rephrased for compliance with licensing restrictions.

---

## Category 1: Transactional Outbox Pattern Best Practices

### BP-1.1: Atomic Transaction Scope
The business data update and the outbox table insertion must be wrapped in a single local database transaction. This is the fundamental guarantee of the pattern — either both succeed or both fail. [Source 1, 3]

### BP-1.2: Event Immutability in Outbox
The event payload stored in the outbox table should be immutable and self-contained. It must contain all necessary data for downstream consumers to act without querying the source service back. [Source 1]

### BP-1.3: Outbox Table Cleanup Strategy
The outbox table grows unbounded without cleanup. Production systems need a cleanup strategy:
- Delete rows after successful publish (for polling-based)
- Partition by time for high-volume systems (drop entire partitions)
- Archive confirmed events to cold storage
[Source 3]

### BP-1.4: CDC vs Polling Trade-off
Two primary relay mechanisms exist:
- **Polling**: Simple to implement, works with any database, but adds 100ms+ latency and creates database load from constant SELECT/UPDATE queries. Becomes a bottleneck at scale.
- **CDC (Change Data Capture)**: Near real-time (milliseconds), reads from transaction log not the table, but requires more infrastructure (Debezium, logical replication, Kafka).
Recommendation: Start with polling for prototypes/moderate throughput. Use CDC for production systems with latency requirements under 1 second or high throughput. [Source 1, 3, 4]

### BP-1.5: SKIP LOCKED for Concurrent Polling
When using polling-based relay with multiple workers, use `FOR UPDATE SKIP LOCKED` to avoid blocking on locked rows and enable concurrent processing. [Source 4]


---

## Category 2: Retry & Error Handling Best Practices

### BP-2.1: Exponential Backoff with Jitter
Retries should use exponential backoff with jitter to avoid retry storms when multiple workers fail at once. Fixed-interval retries can overwhelm the broker or database under sustained failure. [Source 4]

### BP-2.2: Bounded Retry Count (Max Retry Limit)
Retries must be bounded — never retry infinitely. A maximum retry count (e.g., 3-5 attempts) prevents infinite loops and ensures permanently failed events are surfaced rather than hidden. [Source 2, 4]

### BP-2.3: Dead Letter Queue (DLQ) Strategy
Events that fail after maximum retries must be routed to a Dead Letter Queue for human or automated review, never silently dropped. Each microservice should have its own DLQ topic (convention: `<original-topic>.DLT`). DLQs serve three purposes: inspect and discard, fix data and replay, or escalate operationally. [Source 1, 2, 4]

### BP-2.4: Classify Failures (Transient vs Permanent)
Not all failures are transient. Consumers should explicitly classify failures:
- **Transient**: database outages, timeouts, temporary dependency issues → retry
- **Permanent**: malformed payloads, missing headers, schema mismatches → send to DLQ immediately
Retrying permanent failures wastes resources. [Source 4]

### BP-2.5: Durable Retry State
Retry state (retry count, next retry time) should be persisted in the database, not held in memory. This ensures worker crashes do not lose retry intent. [Source 4]

### BP-2.6: Retry Metrics and Observability
Track retry-related metrics:
- `retries_total`: How often events are retried
- `retry_exhaustions_total`: Events exceeding max retry limit
- `dlq_published_total`: Events sent to DLQ
- `dlq_publish_failed_total`: Failures publishing to DLQ (critical alert)
[Source 4]

---

## Category 3: Idempotency Best Practices

### BP-3.1: At-Least-Once Delivery Guarantee
The outbox pattern guarantees at-least-once delivery, not exactly-once. If the relay crashes after publishing but before marking as processed, the event will be sent again. [Source 1, 3]

### BP-3.2: Idempotent Consumer Pattern (Mandatory)
Every consuming service must implement idempotent processing. Common approaches:
- Store a unique event/transaction ID in a `processed_events` table before processing
- Use `INSERT ... ON CONFLICT DO NOTHING` for database operations
- Check for duplicate event IDs before executing business logic
This is not optional — it is mandatory for production systems. [Source 1, 2, 3]

### BP-3.3: Event Envelope with Idempotent ID
Every event should carry a standard envelope containing: `eventId`, `correlationId`, `timestamp`, and `schemaVersion`. The eventId enables deduplication. [Source 2]

---

## Category 4: Event Schema & Versioning Best Practices

### BP-4.1: Schema Versioning Strategy
Events stored in brokers or databases will outlive the code that produced them. Without versioning, consumers break when schemas change. Strategies include:
- Version in event metadata
- Upcasting old events to new format at read time
- Weak schema with defensive deserialization
- Schema registry with compatibility enforcement
[Source 5]

### BP-4.2: Backward Compatibility Rule
Producers must always be backward compatible. Safe changes include adding optional fields with defaults. Unsafe changes include removing required fields, renaming fields, or changing field types. [Source 5, 7]

### BP-4.3: Schema Registry for Governance
For organization-wide governance, use a centralized schema registry (e.g., Confluent Schema Registry) with compatibility checks enforced in CI/CD before deployment. [Source 2, 5, 7]

### BP-4.4: Event Naming Convention
Adopt a consistent naming pattern like `Domain.Entity.Action` (e.g., `orders.Order.Created`). Generic names like "update" or "change" are an anti-pattern. [Source 2]

---

## Category 5: Observability Best Practices

### BP-5.1: Monitor Consumer Lag
Consumer lag (difference between latest produced event and latest consumed event) is the primary health signal. Rising lag indicates consumers cannot keep up and is the earliest warning of downstream problems. [Source 2]

### BP-5.2: Monitor Outbox Lag
Track the time difference between an event being inserted into the outbox table and being successfully published to the broker. Spiking outbox lag means the business is operating on stale data. [Source 1]

### BP-5.3: Correlation IDs End-to-End
Use correlation IDs from the API gateway through every event and service call to enable distributed tracing. Without it, debugging a failure in a multi-service event chain requires manual correlation. [Source 2]

### BP-5.4: Separate Dashboards for Outbox vs Consumer
Use two focused dashboards:
- **Outbox Reliability Dashboard**: backlog growth, publish latency, retry rates, DLQ routing
- **Consumer Processing Dashboard**: consumption rate, processing failure rate, retry queue activity, DLQ volume
Separating concerns prevents alert fatigue. [Source 4]

---

## Category 6: Architecture & Design Best Practices

### BP-6.1: Event Ordering Guarantees
If event order matters, ensure the relay mechanism preserves the exact commit order. For Kafka, partition by entity key to guarantee ordering within a partition. [Source 1, 2]

### BP-6.2: Outbox Table Schema Design
A well-designed outbox table should include:
- `id`: Unique identifier (UUID or auto-increment)
- `aggregate_type`: Routes to topic/destination
- `aggregate_id`: Becomes the message key (for ordering)
- `event_type`: Event classification
- `payload`: Event data as JSON
- `created_at`: Timestamp
- `status/confirmed`: Processing state
- `retry_count`: Number of retry attempts
[Source 3, 4]

### BP-6.3: Thread Safety for Concurrent Access
When multiple threads or workers access the outbox, use proper concurrency controls:
- `FOR UPDATE SKIP LOCKED` for database-level locking
- Synchronized access for in-memory buffers
- ThreadLocal for per-transaction isolation
[Source 4]

### BP-6.4: Auto-Configuration with Override Capability
Library-style implementations should auto-configure with sensible defaults but allow users to override any component. Use conditional bean creation patterns. [General Spring Boot best practice]

---

## Summary Checklist for Review

| # | Best Practice | Category |
|---|---|---|
| BP-1.1 | Atomic transaction scope | Outbox Pattern |
| BP-1.2 | Event immutability in outbox | Outbox Pattern |
| BP-1.3 | Outbox table cleanup strategy | Outbox Pattern |
| BP-1.4 | CDC vs Polling consideration | Outbox Pattern |
| BP-1.5 | SKIP LOCKED for concurrent polling | Outbox Pattern |
| BP-2.1 | Exponential backoff with jitter | Retry & Error |
| BP-2.2 | Bounded retry count | Retry & Error |
| BP-2.3 | Dead letter queue strategy | Retry & Error |
| BP-2.4 | Classify transient vs permanent failures | Retry & Error |
| BP-2.5 | Durable retry state | Retry & Error |
| BP-2.6 | Retry metrics and observability | Retry & Error |
| BP-3.1 | At-least-once delivery guarantee | Idempotency |
| BP-3.2 | Idempotent consumer pattern | Idempotency |
| BP-3.3 | Event envelope with idempotent ID | Idempotency |
| BP-4.1 | Schema versioning strategy | Schema & Versioning |
| BP-4.2 | Backward compatibility rule | Schema & Versioning |
| BP-4.3 | Schema registry for governance | Schema & Versioning |
| BP-4.4 | Event naming convention | Schema & Versioning |
| BP-5.1 | Monitor consumer lag | Observability |
| BP-5.2 | Monitor outbox lag | Observability |
| BP-5.3 | Correlation IDs end-to-end | Observability |
| BP-5.4 | Separate dashboards | Observability |
| BP-6.1 | Event ordering guarantees | Architecture |
| BP-6.2 | Outbox table schema design | Architecture |
| BP-6.3 | Thread safety for concurrent access | Architecture |
| BP-6.4 | Auto-configuration with override | Architecture |
