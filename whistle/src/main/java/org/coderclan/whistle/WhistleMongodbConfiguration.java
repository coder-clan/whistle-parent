package org.coderclan.whistle;

import com.mongodb.MongoClientSettings;
import org.coderclan.whistle.mongodb.EventType2StringConverter;
import org.coderclan.whistle.mongodb.MongodbEventPersistenter;
import org.coderclan.whistle.mongodb.String2EventTypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.Arrays;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
@Configuration
@AutoConfigureAfter(MongoAutoConfiguration.class)
@ConditionalOnClass(MongoCustomConversions.class)
public class WhistleMongodbConfiguration {
    private static final Logger log = LoggerFactory.getLogger(WhistleMongodbConfiguration.class);

    @Bean("eventPersistenter")
    @ConditionalOnBean(MongoClientSettings.class)
    @ConditionalOnMissingBean
    MongodbEventPersistenter mongodbEventPersistenter() {
        return new MongodbEventPersistenter();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MongoClientSettings.class)
    public String2EventTypeConverter string2EventTypeConverter(@Autowired EventTypeRegistrar eventTypeRegistrar) {
        return new String2EventTypeConverter(eventTypeRegistrar);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MongoClientSettings.class)
    public EventType2StringConverter eventType2StringConverter() {
        return new EventType2StringConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MongoClientSettings.class)
    public MongoCustomConversions mongoCustomConversions(@Autowired String2EventTypeConverter string2EventTypeConverter,
                                                         @Autowired EventType2StringConverter eventType2StringConverter) {
        return new MongoCustomConversions(
                Arrays.asList(
                        string2EventTypeConverter,
                        eventType2StringConverter)
        );
    }
}
