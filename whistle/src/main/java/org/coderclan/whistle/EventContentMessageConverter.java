package org.coderclan.whistle;

import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventType;
import org.coderclan.whistle.exception.EventContentTypeNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.util.MimeType;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * Serialize object to JSON, and add object type to headers({@link Constants#CONTENT_JAVA_TYPE_HEADER}).  Deserialize object to the type specified by the header.
 *
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public class EventContentMessageConverter implements MessageConverter {
    private static final Logger log = LoggerFactory.getLogger(EventContentMessageConverter.class);

    @Autowired
    private List<CompositeMessageConverter> messageConverters;
    /**
     * A message converter which support application/json.
     * JSON Serializing and Deserializing will be delegated to this converter.
     */
    private AbstractMessageConverter jsonMessageConverter;

    @Autowired
    private EventTypeRegistrar eventTypeRegistrar;

    @PostConstruct
    @jakarta.annotation.PostConstruct
    public void init() {

        // find a Message converter which supports application/json from this.messageConverter
        MimeType json = new MimeType("application", "json");
        for (CompositeMessageConverter messageConverter : messageConverters) {
            for (MessageConverter c : messageConverter.getConverters()) {
                if (c instanceof AbstractMessageConverter) {
                    AbstractMessageConverter s = (AbstractMessageConverter) c;
                    if (s.getSupportedMimeTypes().contains(json)) {
                        this.jsonMessageConverter = s;
                        return;
                    }
                }
            }
        }
        throw new IllegalStateException("Json Message Converter not found!");
    }

    @Override
    public Object fromMessage(Message<?> message, Class<?> targetClass) {
        Class<?> targetType = getJavaType(message);
        return this.jsonMessageConverter.fromMessage(message, targetType);
    }

    private Class<?> getJavaType(Message<?> message) {
        String targetType = (String) message.getHeaders().get(Constants.CONTENT_JAVA_TYPE_HEADER);
        log.trace("org-coderclan-whistle-java-type: {}", targetType);
        Class<?> clazz = null;
        try {
            clazz = Class.forName(targetType);
        } catch (ClassNotFoundException e) {
            log.debug("", e);
        }

        if (Objects.nonNull(clazz)) {
            return clazz;
        }

        String exchange = (String) message.getHeaders().get(Constants.RABBITMQ_HEADER_RECEIVED_EXCHANGE);
        log.trace(Constants.RABBITMQ_HEADER_RECEIVED_EXCHANGE + ": {}", exchange);
        if (Objects.isNull(exchange) || exchange.isEmpty()) {
            exchange = (String) message.getHeaders().get(Constants.RABBITMQ_HEADER_ORIGINAL_EXCHANGE);
            log.trace(Constants.RABBITMQ_HEADER_ORIGINAL_EXCHANGE + ": {}", exchange);
        }

        EventType<? extends EventContent> eventType = eventTypeRegistrar.findEventType(exchange);
        if (Objects.nonNull(eventType)) {
            clazz = eventType.getContentType();
        }

        if (Objects.isNull(clazz)) {
            throw new EventContentTypeNotFoundException();
        }
        return clazz;
    }

    @Override
    public Message<?> toMessage(Object payload, MessageHeaders headers) {
        // add type name of payload to headers.
        HashMap<String, Object> map = new HashMap<>(headers);
        map.put(Constants.CONTENT_JAVA_TYPE_HEADER, payload.getClass().getName());
        MessageHeaders newHeader = new MessageHeaders(map);

        return this.jsonMessageConverter.toMessage(payload, newHeader);
    }
}
