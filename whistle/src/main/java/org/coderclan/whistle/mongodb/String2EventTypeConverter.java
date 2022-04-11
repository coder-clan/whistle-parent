package org.coderclan.whistle.mongodb;


import org.coderclan.whistle.EventTypeRegistrar;
import org.coderclan.whistle.api.EventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
public class String2EventTypeConverter implements Converter<String, EventType<?>> {

    private final EventTypeRegistrar eventTypeRegistrar;

    public String2EventTypeConverter(@Autowired EventTypeRegistrar eventTypeRegistrar) {
        this.eventTypeRegistrar = eventTypeRegistrar;
    }

    @Override
    public EventType<?> convert(String source) {
        return eventTypeRegistrar.findEventType(source);
    }
}
