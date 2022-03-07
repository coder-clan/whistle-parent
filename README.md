# Whistle

The Whistle is a Reliable Event Delivering and Consuming mechanism for the Java Spring Boot framework. It is named
Whistle since Whistles should be an important tool to communicate in ancient times.

EDA(Event Driving Architecture) is a good way to decouple the services in SOA(Service Oriented Architecture, or
Micro-services). But there is a problem in EDA, which is Data Consistency. A service usually saves some data to a
Database when doing business logic, and then publishes an Event to an MQ(Message Queue, e.g. RabbitMQ, Kafka, etc.)
The Database and The MQ are two Data Sources, and data in the two Data Sources may be in an un-consistent state. For
example, if the network connection between the service and the MQ is interrupted for some reason, the service can't know
the data state in the MQ and don't know whether to commit or roll back the database transaction.

The Whistle use the following approach to solve the Data Consistency problem in Event Publishing:

- Enable MQ's Publisher Confirm.
- Persistent the Event to the database within the same Database Transaction with state unsuccessful.
- After Database Transaction is committed, publish the event to MQ.
- Change the state of the Persisted Event in the database to successful after receiving the Confirmation from MQ.
- Periodically retry sending the unsuccessful Persisted Event to MQ.

Using this approach, Events will never lose, but Events may delay, may be duplicated.

If there is no Database Transaction, the Whistle can also be used to handle events. but there is no Data Consistency
guarantee in this situation.

## Concepts

The Whistle introduce four concepts: EventType, EventContent, EventService and EventConsumer.

EventType indicates the Type of Event, for example, we can define these EventTypes: OrderCreated, OrderPaid,
OrderDelivered. To publish events of these types to tell other systems that: a new Order has been created, an Order has
been paid, or an Order has been delivered properly.

EventContent is a super Class that defines the data schema of event contents. An EventType refers to a subclass of
EventContent.

We can call EventService.publishEvent(EventType type, EventContent content) to publish Event. Implement EventConsumer to
consume a type of events.

## Design

The Whistle uses <a href="https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/">Spring Cloud
Stream</a> to handle event publishing and consuming.

For publishing, the Spring Cloud Stream uses <code>
org.coderclan.whistle.WhistleConfiguration.cloudStreamSupplier</code> to read events from a BlockingQueue and send them
to MQ.

EventService, which is provided by the Whistle, its publishEvent() method will put the event into the BlockingQueue
directly if there is no Database Transaction (@Transactional), or it will persist the event into the database and use
TransactionalEventHandler to handle the event if Transaction is active. The TransactionalEventHandler will put the event
into LocalThreadStorage, and register a callback (
via TransactionSynchronization) to listen to the Commit event of current Transaction. When the transaction commits, The
callback will be fired, it will get events from the LocalThreadStorage and put it into the BlockingQueue.

FailedEventRetrier will periodically retrieve unconfirmed events from Database and re-put them into the BlockingQueue.

For consuming, the Whistle will find all Spring Beans which are instances of EventConsumer, wraps them by
ConsumerWrapper, and registers these ConsumerWrappers to Spring as Spring Beans. The Spring Cloud Stream will use these
ConsumerWrapper Beans to consume events from MQ.

## How to use Whistle.

The module whistle-example-producer demonstrates how to use the Whistle as a producer to publish events. The module
whistle-example-consumer is a demo for the consumer.

Install a rabbitMQ on local machine with default configuration, whistle-example-producer and whistle-example-consumer
could be started as normal spring boot projects. (java -jar whistle-example-producer-1.0.0-SNAPSHOT.jar)

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

- Implement <code>org.coderclan.whistle.api.EventType</code> and <code>org.coderclan.whistle.api.EventContent</code>.
  Using Enumeration to define EventType is recommended.
- The Whistle needs to know what EventTypes we be published. Expose the event types the system will publish As <code>
  org.coderclan.whistle.example.producer.Config.eventTypes</code> do.
- Inject <code>org.coderclan.whistle.api.EventService</code> into your Spring Beans and call it <code>
  EventService.publishEvent()</code> to publish events. <code>org.coderclan.whistle.example.producer.Notification</code>
  demonstrate how to use it.
- Config connection
  for <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/messaging.html#messaging.amqp.rabbitmq">
  RabbitMQ</a>
  or <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/messaging.html#messaging.kafka">Kafka</a>.

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

- Implement <code>org.coderclan.whistle.api.EventConsumer</code> to consume events. Remember to deal with the duplicated
  events. Events with the same <code>org.coderclan.whistle.api.EventContent.getIdempotentId()</code> are
  duplicated.  <code>org.coderclan.whistle.example.consumer.PredatorInSightEventConsumer</code> demonstrates how to
  implement <code>EventConsumer</code>.
- Register event consumers as Spring Bean, Whistle will find all Spring Beans which are instances of EventConsumer, and
  use them to consume the events.
- Config connection
  for <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/messaging.html#messaging.amqp.rabbitmq">
  RabbitMQ</a>
  or <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/messaging.html#messaging.kafka">Kafka</a>.

## Configuration

The Whistle use Spring Cloud Stream, so all the configuration of Spring Cloud Stream could be adjusted. Please refer to
<a href="https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/">Spring Cloud Stream Documentation</a>
for more.

The Whistle introduce some configuration items to change behaviors, <code>org.coderclan.whistle.*</code>, please check
the application.yml in the module whistle-example-producer for details.
