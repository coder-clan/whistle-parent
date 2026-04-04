package org.coderclan.whistle;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Jackson-based {@link EventContentSerializer} beans.
 * <p>
 * Uses static inner classes with {@code @ConditionalOnClass} to isolate Jackson 2
 * ({@code com.fasterxml.jackson}) and Jackson 3 ({@code tools.jackson}) dependencies.
 * Only the inner class whose Jackson version is on the classpath will be loaded,
 * preventing {@code NoClassDefFoundError} at startup.
 * <p>
 * Ordered before {@link WhistleConfiguration} so that the serializer bean is available
 * when persistenter beans (which depend on it) are created.
 *
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
@Configuration
@AutoConfigureBefore(name = "org.coderclan.whistle.WhistleConfiguration")
public class WhistleJacksonConfiguration {

    @Configuration
    @ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")
    static class Jackson2Configuration {
        @Bean
        @ConditionalOnMissingBean(EventContentSerializer.class)
        public EventContentSerializer eventContentSerializer(
                @Autowired com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
            return new JacksonEventContentSerializer(objectMapper);
        }
    }

    @Configuration
    @ConditionalOnClass(name = "tools.jackson.databind.ObjectMapper")
    static class Jackson3Configuration {
        @Bean
        @ConditionalOnMissingBean(EventContentSerializer.class)
        public EventContentSerializer eventContentSerializer(
                @Autowired tools.jackson.databind.ObjectMapper objectMapper) {
            return new Jackson3EventContentSerializer(objectMapper);
        }
    }
}
