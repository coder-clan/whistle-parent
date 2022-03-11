package org.coderclan.whistle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.exception.EventContentSerializationException;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public class JacksonEventContentSerializer implements EventContentSerializer {
    private ObjectMapper objectMapper;

    public JacksonEventContentSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public EventContent toEventContent(String string, Class<? extends EventContent> contentType) {
        try {
            return this.objectMapper.readValue(string, contentType);
        } catch (JsonProcessingException e) {
            throw new EventContentSerializationException(e);
        }
    }

    @Override
    public <C extends EventContent> String toJson(C content) {
        try {
            return this.objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new EventContentSerializationException(e);
        }
    }
}
