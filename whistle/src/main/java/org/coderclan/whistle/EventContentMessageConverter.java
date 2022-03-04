package org.coderclan.whistle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Objects;

/**
 * Serialize object to JSON, and add object type to headers. Deserialize object to the type specified by the header.
 *
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public class EventContentMessageConverter implements MessageConverter {
    private static final Logger log = LoggerFactory.getLogger(EventContentMessageConverter.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Object fromMessage(Message<?> message, Class<?> targetClass) {
        String type = (String) message.getHeaders().get(Constants.CONTENT_JAVA_TYPE_HEADER);
        if (Objects.isNull(type) || type.isEmpty()) {
            log.error("Message without Type Header received!");
            return null;
        }
        try {
            Class<?> clazz = Class.forName(type);
            String temp = new String((byte[]) message.getPayload(), StandardCharsets.UTF_8);
            return objectMapper.readValue(temp, clazz);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (JsonMappingException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Message<?> toMessage(Object payload, MessageHeaders headers) {
        HashMap<String, Object> map = new HashMap<>(headers);
        map.put(Constants.CONTENT_JAVA_TYPE_HEADER, payload.getClass().getName());
        MessageHeaders newHeader = new MessageHeaders(map);

        try {
            byte[] data = this.objectMapper.writeValueAsBytes(payload);
            return MessageBuilder.withPayload(data).copyHeaders(newHeader).build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }
}
