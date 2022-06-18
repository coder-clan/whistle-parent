package org.coderclan.whistle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.support.ErrorMessage;

import java.util.Objects;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public class ServiceActivators {
    private static final Logger log = LoggerFactory.getLogger(ServiceActivators.class);
    @Autowired(required = false)
    private EventPersistenter persistenter;


    /**
     * @param persistentId   Persistent ID(Primary Key in persistent table) of Event
     * @param confirmed      Confirmed Header for RabbitMQ
     * @param recordMetadata RecordMetadata Header for Kafka
     */
    @ServiceActivator(inputChannel = "coderclan-whistle-ack-channel")
    public void acks(@Header(value = Constants.EVENT_PERSISTENT_ID_HEADER, required = false) String persistentId,
                     @Header(value = "amqp_publishConfirm", required = false) Boolean confirmed,
                     @Header(value = "kafka_recordMetadata", required = false) Object recordMetadata
    ) {
        boolean success = Objects.equals(confirmed, Boolean.TRUE) || !(Objects.isNull(recordMetadata));
        log.trace("Confirm received. persistentId={}, confirmed={}", persistentId, success);
        if (Objects.nonNull(persistentId) && success && !Objects.isNull(this.persistenter)) {
            this.persistenter.confirmEvent(persistentId);
        }
    }

    @ServiceActivator(inputChannel = "errorChannel")
    public void errors(ErrorMessage error) {
        log.error("Error countered", error.getPayload());
    }
}
