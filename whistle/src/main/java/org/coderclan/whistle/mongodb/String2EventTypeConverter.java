package org.coderclan.whistle.mongodb;

import org.coderclan.whistle.EventTypeRegistrar;
import org.coderclan.whistle.api.EventType;
import org.coderclan.whistle.exception.EventContentTypeNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

@ReadingConverter
public class String2EventTypeConverter implements Converter<String, EventType<?>> {

    private final EventTypeRegistrar eventTypeRegistrar;

    public String2EventTypeConverter(@Autowired EventTypeRegistrar eventTypeRegistrar) {
        this.eventTypeRegistrar = eventTypeRegistrar;
    }

    // SonarQube's bug
    // https://community.sonarsource.com/t/unresolvable-fp-java-s2638/151934
    @SuppressWarnings("java:S2638")
    @Nullable
    @Override
    public EventType<?> convert(@NonNull String source) {
        EventType<?> result = eventTypeRegistrar.findEventType(source);
        if (result == null) {
            throw new EventContentTypeNotFoundException("Unknown event type: " + source);
        }
        return result;
    }
}