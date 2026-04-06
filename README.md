# Whistle

The Whistle is a Reliable Event Delivering and Consuming mechanism for the Java Spring Boot framework. It is named
Whistle since Whistles should be an important tool for communication in ancient times.

EDA(Event Driving Architecture) is a good way to decouple the services in SOA(Service Oriented Architecture, or
Micro-services). But there is a problem in EDA, which is data consistency. A service usually saves some data to a
Database when doing business logic, and then publishes an event to an MQ(Message Queue, e.g. RabbitMQ, Kafka, etc.). The
Database and The MQ are two data sources, and data in the two data sources may be in an inconsistent state. For example,
if the network connection between the service and the MQ is interrupted for some reason, the service can't know the data
state in the MQ and doesn’t know whether to commit or roll back the database transaction.

The Whistle uses the following approaches to solve the data consistency problem in event publishing:

- Enable MQ's Publisher Confirm.
- Persistent the event to the database within the same database transaction with the state of unsuccessful.
- After the database transaction is committed, publish the event to MQ.
- Change the state of the Persisted Event in the database to successful after receiving the confirmation from MQ.
- Periodically retry to send the unsuccessful persistent event to MQ.

Using this approach, events will never be lost, and there will be no phantom events (events sent to MQ, but database
transactions were rolled back). However, events may be delayed or duplicated, or may be out of order.

If there is no database transaction, the Whistle can also be used to handle events. but there is no data consistency
guarantee in this situation.

## Compatibility

| Dimension | Supported Range |
|---|---|
| Java | 8, 11, 17, 21, 25 |
| Spring Boot | 2.x (2.6.4+), 3.x, 4.x |
| Spring Cloud | 2020.0.4+ (Boot 2.x), 2022.0.x+ (Boot 3.x), 2025.1.0 (Boot 4.x) |
| Databases | MySQL, PostgreSQL, Oracle, H2, MongoDB |
| Jackson | Jackson 2 (Spring Boot 2.x/3.x), Jackson 3 (Spring Boot 4.x) — auto-detected |

The javax/jakarta namespace migration (Spring Boot 2.x → 3.x/4.x) is handled transparently. No code changes are needed when upgrading.

## Change Log

- 1.2.1 Probe-based locking strategy detection and retry ordering improvements.
    - At startup, the system probes the database with real SQL to detect `FOR UPDATE SKIP LOCKED` and `FOR UPDATE NOWAIT` support, replacing the previous version-based detection. The strongest available locking clause is selected automatically: SKIP LOCKED > NOWAIT > plain FOR UPDATE.
    - All retrieval paths now use `ORDER BY retried_count ASC, id DESC` to deprioritize poison events (events that repeatedly fail).
    - Added configurable `retrieveTransactionTimeout` (default 5s) to protect against deadlocks during event retrieval.
    - Breaking change: The abstract method `getRetrieveSql(int, boolean)` has been replaced by `getOrderedBaseRetrieveSql(int)`. If you have a custom subclass of AbstractRdbmsEventPersistenter, implement `getOrderedBaseRetrieveSql(int)` returning the SELECT/WHERE/ORDER BY/LIMIT portion without any locking clause.
    - Tested compatibility with Spring Boot 2.x, 3.x, and 4.x on Java 8+.
- 1.2.0 Added "skip lock" to retrieveSql of RdbmsEventPersistenter to prevent deadlock if underline database support it.
    - Breaking change: The method signature of RdbmsEventPersistenter.getRetrieveSql(int) has been changed to RdbmsEventPersistenter.getRetrieveSql(int, boolean). If you have a custom
      subclass of RdbmsEventPersistenter, please update the method signature accordingly.
    - MongoDBEventPersistenter now records retry (times) in MongoDB database.
- 1.1.0 Refactored to make the Whistle more flexible for customization.
    - Breaking change: The original EventContent class has been renamed to AbstractEventContent. The EventContent is a
      marker interface now. When upgrading from 1.0.x to 1.1.x, subclasses of original EventContent will encounter
      compilation errors. To resolve this, change "extends EventContent" to "extends AbstractEventContent".
    - Introduced interfaces for several components to allow user to custom these components more easily.
    - Bug fixes:
        - Resolved: The FailedEventRetrier stops retrying event sending when exceptions are thrown.
        - Resolved deadlock issues in Oracle database by appending "skip lock" to the SQL for fetching failed events.
        - Proper persistent table creation in H2 databases is now ensured.
- 1.0.2 Fixed the bug that transaction does not work in spring boot 3.
- 1.0.1 Supports spring boot 3.x

## Concepts

The Whistle introduces four concepts: <code>EventType</code>, <code>EventContent</code>, <code>EventService</code>
and <code>EventConsumer</code>.

<code>EventType</code> indicates the Type of Event, for example, we can define these EventTypes: OrderCreated,
OrderPaid, OrderDelivered. To publish events of these types to tell other systems that: a new order has been created, an
order has been paid, or an order has been delivered properly.

<code>EventContent</code> is a super Class that defines the data schema of event contents. An EventType connects to a
subclass of
<code>EventContent</code>.

To publish an event, we can use <code>EventService.publishEvent(EventType type, EventContent content)</code>.

To consume events, we can implement <code>EventConsumer</code>.

## Design

The Whistle uses <a href="https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/">Spring Cloud
Stream</a> to handle event publishing and consuming.

For publishing, the Spring Cloud Stream uses <code>org.coderclan.whistle.WhistleConfiguration.cloudStreamSupplier</code>
to send events to MQ. The cloudStreamSupplier uses <code>org.coderclan.whistle.EventSender</code> to create a Flux
of <code>EventContent</code>.

EventService, which is provided by the Whistle, its <code>publishEvent()</code> method will call <code>
org.coderclan.whistle.EventSender.send</code> to send events directly if there is no Database Transaction (
@Transactional) enabled,
or it will persist the event into the
database and use <code>TransactionalEventHandler</code> to handle the event if Transaction is active. The
TransactionalEventHandler will put the event into ThreadLocal, and register a callback (via TransactionSynchronization)
to listen to the commit event of the current Transaction. When the transaction commits, the callback will be fired, it
will get events from the ThreadLocal and call <code>
org.coderclan.whistle.EventSender.send</code> to send events.

<code>FailedEventRetrier</code> will periodically retrieve unconfirmed events from the database and re-send them via
<code>EventSender</code>.

For consuming, the Whistle will look for all Spring Beans implementing <code>EventConsumer</code>, and config system properties for these beans
to let Spring Cloud Stream to use them for consuming. Check <code>WhistleConfiguration.registerEventConsumers()</code> for details.

## How to use Whistle.

The module whistle-example-producer demonstrates how to use the Whistle as a producer to publish events. The module
whistle-example-consumer is a demo for the consumer.

Install the RabbitMQ on a local machine with the default configuration, and then whistle-example-producer and
whistle-example-consumer could be started as normal spring boot projects. (java -jar
whistle-example-producer-1.0.0-SNAPSHOT.jar)

### Producer

- Depend on the Whistle in maven pom.xml.

<pre>
        &lt;dependency>
            &lt;groupId>org.coderclan&lt;/groupId>
            &lt;artifactId>whistle&lt;/artifactId>
        &lt;/dependency>
        &lt;!-- Choose either RabbitMQ or Kafka, not both.  -->
        &lt;dependency>
            &lt;groupId>org.springframework.cloud&lt;/groupId>
            &lt;artifactId>spring-cloud-starter-stream-rabbit&lt;/artifactId>
        &lt;/dependency>
        &lt;dependency>
            &lt;groupId>org.springframework.cloud&lt;/groupId>
            &lt;artifactId>spring-cloud-starter-stream-kafka&lt;/artifactId>
        &lt;/dependency>
</pre>
Make sure the version of Spring Cloud works with Spring Boot version in your project. please check the section "<code>
Table 1. Release train Spring Boot compatibility</code>" on <a href="https://spring.io/projects/spring-cloud">the spring
cloud official page</a> for a list of compatibility between Spring Cloud and Spring Boot. The following method may be
used to specific the version of Spring Cloud.
<pre>
    &lt;dependencyManagement>
         &lt;dependencies>
             &lt;dependency>
                 &lt;groupId>org.springframework.cloud &lt;/groupId>
                 &lt;artifactId>spring-cloud-dependencies &lt;/artifactId>
                 &lt;version>${spring-cloud.version} &lt;/version>
                 &lt;type>pom &lt;/type>
                 &lt;scope>import &lt;/scope>
             &lt;/dependency>
         &lt;/dependencies>
     &lt;/dependencyManagement></pre>

- Implement <code>org.coderclan.whistle.api.EventType</code> and <code>org.coderclan.whistle.api.EventContent</code>.
  Use Enumeration to define EventType is recommended. If you want to share event type definitions between producer and consumer modules, depend on <code>whistle-api</code> (which has zero framework dependencies) in the shared module instead of <code>whistle</code>.

- The Whistle needs to know what <code>EventType</code> will be published. Expose the event types which the system will
  publish As <code>
  org.coderclan.whistle.example.producer.Config.eventTypes()</code> do.

- Inject <code>org.coderclan.whistle.api.EventService</code> into your Spring Beans and call <code>
  EventService.publishEvent()</code>
  to publish events.  <code>org.coderclan.whistle.example.producer.Notification</code> demonstrate how to do this.

- Config connection
  for <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/messaging.html#messaging.amqp.rabbitmq">
  RabbitMQ</a>
  or <a href="https://docs.spring.iospring-boot/docs/current/reference/html/messaging.html#messaging.kafka">Kafka</a>

### Consumer

- Depend on the Whistle in maven pom.xml.

<pre>
        &lt;dependency>
            &lt;groupId>org.coderclan&lt;/groupId>
            &lt;artifactId>whistle&lt;/artifactId>
        &lt;/dependency>
        &lt;!-- Choose either RabbitMQ or Kafka, not both.  -->
        &lt;dependency>
            &lt;groupId>org.springframework.cloud&lt;/groupId>
            &lt;artifactId>spring-cloud-starter-stream-rabbit&lt;/artifactId>
        &lt;/dependency>
        &lt;dependency>
            &lt;groupId>org.springframework.cloud&lt;/groupId>
            &lt;artifactId>spring-cloud-starter-stream-kafka&lt;/artifactId>
        &lt;/dependency>
</pre>
Make sure the version of Spring Cloud works with Spring Boot version in your project. please check the section "<code>
Table 1. Release train Spring Boot compatibility</code>" on <a href="https://spring.io/projects/spring-cloud">the spring
cloud official page</a> for a list of compatibility between Spring Cloud and Spring Boot. The following method may be
used to specific the version of Spring Cloud.
<pre>
    &lt;dependencyManagement>
         &lt;dependencies>
             &lt;dependency>
                 &lt;groupId>org.springframework.cloud &lt;/groupId>
                 &lt;artifactId>spring-cloud-dependencies &lt;/artifactId>
                 &lt;version>${spring-cloud.version} &lt;/version>
                 &lt;type>pom &lt;/type>
                 &lt;scope>import &lt;/scope>
             &lt;/dependency>
         &lt;/dependencies>
     &lt;/dependencyManagement></pre>

- Implement <code>org.coderclan.whistle.api.EventConsumer</code> to consume events. Remember to deal with the duplicated
  events. Events with the same <code>org.coderclan.whistle.api.EventContent.getIdempotentId()</code> are
  duplicated. <code>org.coderclan.whistle.example.consumer.PredatorInSightEventConsumer</code> demonstrates how to
  implement <code>EventConsumer</code>.

- Register event consumers as Spring Bean, Whistle will find all Spring Beans which are instances of <code>
  EventConsumer</code>, and use them to consume the events.

- Config connection
  for <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/messaging.html#messaging.amqp.rabbitmq">
  RabbitMQ</a>
  or <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/messaging.html#messaging.kafka">Kafka</a>.

## Configuration

The Whistle uses Spring Cloud Stream, so all the configuration properties of Spring Cloud Stream could be adjusted.
Please refer to
<a href="https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/">Spring Cloud Stream Documentation</a>
for more. The Whistle has changed some of these configuration items. Please check <code>
org.coderclan.whistle.spring-cloud-stream.properties</code> for details.

The Whistle introduces the following configuration properties under the <code>org.coderclan.whistle</code> prefix:

| Property | Default | Description |
|---|---|---|
| <code>applicationName</code> | <code>${spring.application.name}</code> | Globally unique identifier for the service, used as the consumer group name. Required. |
| <code>retryDelay</code> | <code>10</code> (seconds) | Interval between failed event retry cycles. |
| <code>persistentTableName</code> | <code>sys_event_out</code> | Database table name for event persistence. |
| <code>retrieveTransactionTimeout</code> | <code>5</code> (seconds) | Query timeout for the <code>SELECT ... FOR UPDATE</code> statement during event retrieval. Protects against deadlocks when SKIP LOCKED is not available. Set to 0 to disable. |

## Pitfalls

### Compatibility between Spring Cloud and Spring Boot.

Make sure the version of Spring Cloud works with Spring Boot version in your project. please check the section <code>
Table 1. Release train Spring Boot compatibility</code> on <a href="https://spring.io/projects/spring-cloud">the spring
cloud official page</a> for a list of compatibility between Spring Cloud and Spring Boot.

### Problems of producing and consuming the same EventType in a single system

Producing and consuming the same EventType in a single system is not supported. The Whistle will detect this at startup and throw an <code>IllegalStateException</code>.

If producing and consuming the same EventType in a single system, the Spring Cloud Stream will NOT send the event to
Broker (i.e. RabbitMQ). This will cause two problems:

- Other systems can NOT receive events of this EventType from Broker.
- The Whistle will retry to send the events forever. Since the events are not sent to Broker, so there will be no ACK
  from Broker, and there is no way to change the state of the event to successfully sent.

Please redesign the system to avoid to produce and consume the same EventType in a single system. There is a discussion
of the same problem in stackoverflow:
<a
href="https://stackoverflow.com/questions/66729569/spring-cloud-stream-not-send-message-to-kafka-when-declare-producer-and-consumer?rq=1">https://stackoverflow.com/questions/66729569/spring-cloud-stream-not-send-message-to-kafka-when-declare-producer-and-consumer?rq=1</a>

### Event not persist to MongoDB in spring-data-mongodb

Spring Data MongoDB does not enable transactions by default. The Whistle only persists events to the database if
transactions are enabled. You can simply enable transactions by adding a Spring Bean of MongoTransactionManager. For an
example, please refer to org.coderclan.whistle.example.producer.Config#transactionManager in the
whistle-example-producer-mongo project.