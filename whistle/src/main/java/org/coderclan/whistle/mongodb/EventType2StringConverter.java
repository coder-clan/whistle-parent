package org.coderclan.whistle.mongodb;


import org.coderclan.whistle.api.EventType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

@WritingConverter
public class EventType2StringConverter implements Converter<EventType<?>, String> {

    @Override
    public String convert(EventType<?> source) {
        return source.getName();
    }
}
