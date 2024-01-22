package org.coderclan.whistle;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.coderclan.whistle.api.EventConsumer;
import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventService;
import org.coderclan.whistle.api.EventType;
import org.coderclan.whistle.rdbms.H2EventPersistenter;
import org.coderclan.whistle.rdbms.MysqlEventPersistenter;
import org.coderclan.whistle.rdbms.OracleEventPersistenter;
import org.coderclan.whistle.rdbms.PostgresqlEventPersistenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.messaging.Message;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
@Configuration
@PropertySource(value = "classpath:org/coderclan/whistle/spring-cloud-stream.properties", encoding = "UTF-8")
@EnableConfigurationProperties(WhistleConfigurationProperties.class)
@AutoConfigureAfter({DataSourceAutoConfiguration.class, WhistleMongodbConfiguration.class})
public class WhistleConfiguration implements ApplicationContextAware {
    private static final Logger log = LoggerFactory.getLogger(WhistleConfiguration.class);
    private static final String CLOUD_STREAM_SUPPLIER = "cloudStreamSupplier";
    @Autowired(required = false)
    private List<EventConsumer<?>> consumers;
    @Autowired(required = false)
    List<Collection<? extends EventType<?>>> publishingEventType;

    @Autowired
    private WhistleConfigurationProperties properties;


    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    @jakarta.annotation.PostConstruct
    public void init() {
        checkApplicationName();
        registerEventConsumers();
        checkEventType();
    }

    /**
     * Whistle does NOT support producing and consuming an event at the same time.
     */
    private void checkEventType() {
        if (Objects.isNull(consumers) || Objects.isNull(this.publishingEventType)) {
            return;
        }

        Set<EventType<?>> consumingTypes = consumers.stream().map(EventConsumer::getSupportEventType).collect(Collectors.toSet());
        Set<? extends EventType<?>> producingTypes = this.publishingEventType.stream().flatMap(Collection::stream).collect(Collectors.toSet());

        producingTypes.retainAll(consumingTypes);
        if (!producingTypes.isEmpty()) {
            if (log.isErrorEnabled()) {
                log.error("Whistle does NOT support producing and consuming an event at the same time. Please check the following events: {}", producingTypes.stream().map(EventType::getName).collect(Collectors.joining(",")));
            }
            throw new IllegalStateException("Whistle does NOT support producing and consuming an event at the same time.");
        }
    }

    private void checkApplicationName() {
        String applicationName = this.properties.getApplicationName();
        if (Objects.isNull(applicationName) || applicationName.isEmpty()) {
            throw new IllegalStateException("The Application Name must be set.");
        }
        log.info("Whistle Application Name: {}.", applicationName);
    }

    private void registerEventConsumers() {

        StringBuilder beanNamesStr = new StringBuilder(CLOUD_STREAM_SUPPLIER);

        String[] consumerBeanNames =
                this.applicationContext.getBeanNamesForType(EventConsumer.class);
        for (String consumerBeanName : consumerBeanNames) {
            beanNamesStr.append(';').append(consumerBeanName);

            EventConsumer<?> c = (EventConsumer<?>) this.applicationContext.getBean(consumerBeanName);
            String topicName = c.getSupportEventType().getName();

            //-Dspring.cloud.stream.function.bindings.consumer0-in-0=xxx
            System.setProperty("spring.cloud.stream.function.bindings." + consumerBeanName + "-in-0", topicName);
        }

        System.setProperty("spring.cloud.function.definition", beanNamesStr.toString());
        System.setProperty("spring.cloud.stream.default.group", this.properties.getApplicationName());
    }

    @Bean("mysqlEventPersistenter")
    @ConditionalOnClass(name = "com.mysql.cj.jdbc.Driver")
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public MysqlEventPersistenter mysqlEventPersistenter(
            @Autowired DataSource dataSource,
            @Autowired EventContentSerializer serializer,
            @Autowired EventTypeRegistrar eventTypeRegistrar
    ) {
        return new MysqlEventPersistenter(dataSource, serializer, eventTypeRegistrar, this.properties.getPersistentTableName());
    }

    @Bean("h2EventPersistenter")
    @ConditionalOnClass(name = "org.h2.Driver")
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public H2EventPersistenter h2EventPersistenter(
            @Autowired DataSource dataSource,
            @Autowired EventContentSerializer serializer,
            @Autowired EventTypeRegistrar eventTypeRegistrar
    ) {
        return new H2EventPersistenter(dataSource, serializer, eventTypeRegistrar, this.properties.getPersistentTableName());
    }

    @Bean("postgresqlEventPersistenter")
    @ConditionalOnClass(name = "org.postgresql.Driver")
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public PostgresqlEventPersistenter postgresqlEventPersistenter(
            @Autowired DataSource dataSource,
            @Autowired EventContentSerializer serializer,
            @Autowired EventTypeRegistrar eventTypeRegistrar
    ) {
        return new PostgresqlEventPersistenter(dataSource, serializer, eventTypeRegistrar, this.properties.getPersistentTableName());
    }

    @Bean("oracleEventPersistenter")
    @ConditionalOnClass(name = "oracle.jdbc.OracleDriver")
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public OracleEventPersistenter oracleEventPersistenter(
            @Autowired DataSource dataSource,
            @Autowired EventContentSerializer serializer,
            @Autowired EventTypeRegistrar eventTypeRegistrar) {
        return new OracleEventPersistenter(dataSource, serializer, eventTypeRegistrar, this.properties.getPersistentTableName());
    }

    @Bean
    @ConditionalOnBean({EventPersistenter.class})
    @ConditionalOnMissingBean
    public FailedEventRetrier failedEventRetrier(@Autowired EventPersistenter persistenter, @Autowired EventSender eventSender) {
        return new FailedEventRetrier(persistenter, eventSender);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventService eventService() {
        return new EventServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionalEventHandler transactionEventHandler(@Autowired EventSender eventSender) {
        return new TransactionalEventHandler(eventSender);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventTypeRegistrar eventTypeRegistrar(@Autowired(required = false) List<Collection<? extends EventType<?>>> publishingEventType, @Autowired(required = false) List<EventConsumer<?>> consumers) {
        return new EventTypeRegistrar(publishingEventType, consumers);
    }

    @Bean
    @ConditionalOnMissingBean
    ServiceActivators cloudStreamConfig() {
        return new ServiceActivators();
    }

    @Bean
    @ConditionalOnMissingBean
    public EventSender eventSender() {
        return new ReactorEventSender();
    }

    @Bean
    @ConditionalOnMissingBean
    public EventContentSerializer eventContentSerializer(@Autowired ObjectMapper objectMapper) {
        return new JacksonEventContentSerializer(objectMapper);
    }

    @Bean(CLOUD_STREAM_SUPPLIER)
    @ConditionalOnMissingBean(name = CLOUD_STREAM_SUPPLIER)
    public Supplier<Flux<Message<EventContent>>> cloudStreamSupplier(@Autowired EventSender eventSender) {
        return eventSender::asFlux;
    }
}