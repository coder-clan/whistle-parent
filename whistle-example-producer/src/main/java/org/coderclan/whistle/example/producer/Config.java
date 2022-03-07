package org.coderclan.whistle.example.producer;

import org.coderclan.whistle.example.producer.api.ExampleEventType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

@Configuration
public class Config {
    @Bean
    public Collection<ExampleEventType> eventTypes() {
        return Collections.unmodifiableList(Arrays.asList(ExampleEventType.values()));
    }
}

