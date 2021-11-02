package org.coderclan.whistle;

import org.coderclan.whistle.api.EventContent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

import java.util.function.Supplier;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
@Configuration
public class SupplierConfig {
    @Bean
    public Supplier<Message<EventContent>> supplier() {
        return () -> {
            while (true) {
                try {
                    Event<? extends EventContent> event = Constants.queue.take();

                    return MessageBuilder.<EventContent>withPayload(event.getContent())
                            .setHeader("spring.cloud.stream.sendto.destination", event.getType().getName())
                            .setHeader(Constants.EVENT_PERSISTENT_ID_HEADER, event.getPersistentEventId())
                            .build();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }

        };
    }

}
