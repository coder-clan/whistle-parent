package org.coderclan.whistle;

import org.coderclan.whistle.api.EventConsumer;
import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventService;
import org.coderclan.whistle.api.EventType;
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
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

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

    @Autowired(required = false)
    private List<EventConsumer> consumers;

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


    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public DatabaseEventPersistenter eventPersistenter() {
        return new DatabaseEventPersistenter();
    }

    @Bean
    @ConditionalOnBean({EventPersistenter.class})
    @ConditionalOnMissingBean
    public FailedEventRetrier failedEventRetrier(@Autowired EventPersistenter persistenter) {
        return new FailedEventRetrier(persistenter);
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
    ServiceActivators cloudStreamConfig() {
        return new ServiceActivators();
    }

    @Bean
    public Supplier<Message<EventContent>> cloudStreamSupplier() {
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
