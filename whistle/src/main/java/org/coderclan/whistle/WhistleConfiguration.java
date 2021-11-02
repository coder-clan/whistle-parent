package org.coderclan.whistle;

import org.coderclan.whistle.api.EventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Objects;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
@Configuration
@PropertySource(value = "classpath:org/coderclan/whistle/spring-cloud-stream.properties", encoding = "UTF-8")
public class WhistleConfiguration implements ApplicationContextAware {
    private static final Logger log = LoggerFactory.getLogger(WhistleConfiguration.class);

    @Autowired(required = false)
    private List<EventConsumer> consumers;

    @Autowired(required = false)
    private EventPersistenter persistenter;

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;

    }

    @PostConstruct
    public void init() {
        if (Objects.isNull(consumers) || consumers.isEmpty())
            return;

        StringBuilder sb = new StringBuilder();
        // System.setProperty("spring.cloud.stream.function.bindings." + "ackHandler" + "-in-0", "sending-confirm-ack-channel");

        int i = 0;
        for (EventConsumer c : this.consumers) {
            i++;

            String beanName = "whistleConsumer" + i;
            sb.append(';').append(beanName);

            //-Dspring.cloud.stream.function.bindings.consumer0-in-0=xxx
            System.setProperty("spring.cloud.stream.function.bindings." + beanName + "-in-0", c.getSupportEventType().getName());

            GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
            beanDefinition.setBeanClass(ConsumerWrapper.class);

            MutablePropertyValues mpv = new MutablePropertyValues();
            beanDefinition.setPropertyValues(mpv);
            mpv.add("eventConsumer", c);
            ((BeanDefinitionRegistry) (this.applicationContext)).registerBeanDefinition(beanName, beanDefinition);

        }

        System.setProperty("spring.cloud.function.definition", sb.deleteCharAt(0).toString());
        System.setProperty("spring.cloud.stream.default.group", "exampleConsumer");
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
}
