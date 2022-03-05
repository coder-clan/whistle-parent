package org.coderclan.whistle;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.coderclan.whistle.api.EventConsumer;
import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventService;
import org.coderclan.whistle.api.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
@Configuration
@PropertySource(value = "classpath:org/coderclan/whistle/spring-cloud-stream.properties", encoding = "UTF-8")
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
public class WhistleConfiguration implements ApplicationContextAware {
    private static final Logger log = LoggerFactory.getLogger(WhistleConfiguration.class);

    @Autowired(required = false)
    private List<EventConsumer> consumers;

    @Autowired(required = false)
    private EventPersistenter persistenter;

    @Resource
    private String whistleSystemName;

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        registerEventConsumers();
    }

    private void registerEventConsumers() {
        if (Objects.isNull(consumers) || consumers.isEmpty())
            return;

        StringBuilder beanNames = new StringBuilder();

        int i = 0;
        for (EventConsumer c : this.consumers) {
            i++;

            String beanName = "whistleConsumer" + i;
            beanNames.append(';').append(beanName);

            registerConsumer(c, beanName);
        }

        System.setProperty("spring.cloud.function.definition", beanNames.deleteCharAt(0).toString());
        System.setProperty("spring.cloud.stream.default.group", this.whistleSystemName);
    }

    private void registerConsumer(EventConsumer c, String beanName) {
        //-Dspring.cloud.stream.function.bindings.consumer0-in-0=xxx
        System.setProperty("spring.cloud.stream.function.bindings." + beanName + "-in-0", c.getSupportEventType().getName());

        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(ConsumerWrapper.class);

        MutablePropertyValues mpv = new MutablePropertyValues();
        beanDefinition.setPropertyValues(mpv);
        mpv.add("eventConsumer", c);

        ((BeanDefinitionRegistry) (this.applicationContext)).registerBeanDefinition(beanName, beanDefinition);
    }

    /**
     * @param persistentId   Persistent ID(Primary Key in persistent table) of Event
     * @param confirmed      Confirmed Header for RabbitMQ
     * @param recordMetadata RecordMetadata Header for Kafka
     */
    @ServiceActivator(inputChannel = "coderclan-whistle-ack-channel")
    public void acks(@Header(Constants.EVENT_PERSISTENT_ID_HEADER) Long persistentId,
                     @Header(value = "amqp_publishConfirm", required = false) Boolean confirmed,
                     @Header(value = "kafka_recordMetadata", required = false) Object recordMetadata
    ) {
        boolean success = Objects.equals(confirmed, Boolean.TRUE) || !(Objects.isNull(recordMetadata));
        log.trace("Confirm received. persistentId={}, confirmed={}", persistentId, success);
        if (persistentId != -1L && success && !Objects.isNull(this.persistenter)) {
            this.persistenter.confirmEvent(persistentId);
        }
    }

    @ServiceActivator(inputChannel = "errorChannel")
    public void errors(Message<?> error) {
        System.out.println("Error: " + error);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public EventPersistenter eventPersistenter() {
        return new EventPersistenter();
    }

    @Bean
    @ConditionalOnBean({DataSource.class, EventPersistenter.class})
    @ConditionalOnMissingBean
    public FailedEventRetrier failedEventRetrier(DataSource ds, EventPersistenter persistenter,
                                                 ObjectMapper objectMapper, EventTypeRegistrar typeRegistrar) {
        return new FailedEventRetrier(ds, persistenter, objectMapper, typeRegistrar);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventService eventService() {
        return new EventServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionEventHandler transactionEventHandler() {
        return new TransactionEventHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    EventTypeRegistrar eventTypeRegistrar(@Autowired(required = false) List<Collection<? extends EventType<?>>> publishingEventType) {
        return new EventTypeRegistrar(publishingEventType);
    }

    @Bean
    @ConditionalOnMissingBean
    EventContentMessageConverter eventContentMessageConverter() {
        return new EventContentMessageConverter();
    }

    @Bean
    public Supplier<Message<EventContent>> supplier() {
        return () -> {
            while (true) {
                try {
                    Event<? extends EventContent> event = Constants.queue.take();

                    return MessageBuilder.<EventContent>withPayload(event.getContent())
                            .setHeader("spring.cloud.stream.sendto.destination", event.getType().getName())
                            .setHeader(Constants.EVENT_PERSISTENT_ID_HEADER, event.getPersistentEventId())
                            .build();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }

        };
    }
}
