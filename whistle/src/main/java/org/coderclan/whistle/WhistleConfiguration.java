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
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
@Configuration
@PropertySource(value = "classpath:org/coderclan/whistle/spring-cloud-stream.properties", encoding = "UTF-8")
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
public class WhistleConfiguration implements ApplicationContextAware {
    private static final Logger log = LoggerFactory.getLogger(WhistleConfiguration.class);

    @Autowired(required = false)
    private List<EventConsumer<?>> consumers;

    @Value("${org.coderclan.whistle.applicationName:${spring.application.name}}")
    private String applicationName;

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        checkApplicationName();
        registerEventConsumers();
    }

    private void checkApplicationName() {
        if (Objects.isNull(this.applicationName) || this.applicationName.isEmpty()) {
            throw new IllegalStateException("The Application Name must be set.");
        }
        log.info("Whistle Application Name: {}.", this.applicationName);
    }

    private void registerEventConsumers() {
        if (Objects.isNull(consumers) || consumers.isEmpty())
            return;

        StringBuilder beanNames = new StringBuilder();

        int i = 0;
        for (EventConsumer<?> c : this.consumers) {
            i++;

            String beanName = "whistleConsumer" + i;
            beanNames.append(';').append(beanName);

            registerConsumer(c, beanName);
        }

        System.setProperty("spring.cloud.function.definition", beanNames.deleteCharAt(0).toString());
        System.setProperty("spring.cloud.stream.default.group", this.applicationName);
    }

    private void registerConsumer(EventConsumer<?> c, String beanName) {
        //-Dspring.cloud.stream.function.bindings.consumer0-in-0=xxx
        System.setProperty("spring.cloud.stream.function.bindings." + beanName + "-in-0", c.getSupportEventType().getName());

        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(ConsumerReactiveWrapper.class);

        MutablePropertyValues mpv = new MutablePropertyValues();
        beanDefinition.setPropertyValues(mpv);
        mpv.add("eventConsumer", c);

        ((BeanDefinitionRegistry) (this.applicationContext)).registerBeanDefinition(beanName, beanDefinition);
    }


    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public DatabaseEventPersistenter eventPersistenter() {
        return new DatabaseEventPersistenter();
    }

    @Bean
    @ConditionalOnBean({EventPersistenter.class})
    @ConditionalOnMissingBean
    public FailedEventRetrier failedEventRetrier(@Autowired EventPersistenter persistenter, @Autowired EventQueue eventQueue) {
        return new FailedEventRetrier(persistenter, eventQueue);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventService eventService() {
        return new EventServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionalEventHandler transactionEventHandler(@Autowired EventQueue eventQueue) {
        return new TransactionalEventHandler(eventQueue);
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
    ServiceActivators cloudStreamConfig() {
        return new ServiceActivators();
    }

    @Bean
    @ConditionalOnMissingBean
    EventQueue eventQueue(@Value("${org.coderclan.whistle.eventQueueSize:128}") int eventQueueSize) {
        log.info("Size of Event Queue: {}.", eventQueueSize);
        return new EventQueueImpl(eventQueueSize);
    }

    @Bean
    @ConditionalOnMissingBean
    EventContentSerializer eventContentSerializer(@Autowired ObjectMapper objectMapper) {
        return new JacksonEventContentSerializer(objectMapper);
    }

    @Bean
    public Supplier<Flux<Message<EventContent>>> cloudStreamSupplier(@Autowired EventQueue eventQueue) {
        return () ->
                Flux.fromStream(Stream.generate(() -> {
                    try {

                        Event<? extends EventContent> event = eventQueue.take();

                        return MessageBuilder.<EventContent>withPayload(event.getContent())
                                .setHeader("spring.cloud.stream.sendto.destination", event.getType().getName())
                                .setHeader(Constants.EVENT_PERSISTENT_ID_HEADER, event.getPersistentEventId())
                                .build();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                })).subscribeOn(Schedulers.boundedElastic()).share();


    }
}
