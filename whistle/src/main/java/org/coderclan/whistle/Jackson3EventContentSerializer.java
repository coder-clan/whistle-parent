package org.coderclan.whistle;

import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.exception.EventContentSerializationException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Jackson 3 ({@code tools.jackson}) implementation of {@link EventContentSerializer}.
 * Used on Spring Boot 4.x where Jackson 3 is the default JSON library.
 *
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public class Jackson3EventContentSerializer implements EventContentSerializer {
    private ObjectMapper objectMapper;

    public Jackson3EventContentSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public EventContent toEventContent(String string, Class<? extends EventContent> contentType) {
        try {
            return this.objectMapper.readValue(string, contentType);
        } catch (JacksonException e) {
            throw new EventContentSerializationException(e);
        }
    }

    @Override
    public <C extends EventContent> String toJson(C content) {
        try {
            return this.objectMapper.writeValueAsString(content);
        } catch (JacksonException e) {
            throw new EventContentSerializationException(e);
        }
    }
}
