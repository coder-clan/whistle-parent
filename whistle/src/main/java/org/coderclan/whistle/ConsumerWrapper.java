package org.coderclan.whistle;

import org.coderclan.whistle.api.EventConsumer;
import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.exception.ConsumerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public class ConsumerWrapper implements Consumer<EventContent> {
    private static final Logger log = LoggerFactory.getLogger(ConsumerWrapper.class);

    private EventConsumer<EventContent> eventConsumer;

    public void setEventConsumer(EventConsumer<EventContent> eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    @Override
    public void accept(EventContent content) {
        consume(eventConsumer, content);
    }

    static void consume(EventConsumer<EventContent> eventConsumer, EventContent content) {
        try {
            log.trace("Received event: type={}, content={}", eventConsumer.getSupportEventType(), content);
            if (!eventConsumer.consume(content)) {
                throw new ConsumerException("Consume failed.");
            }
        } catch (Exception e) {
            throw new ConsumerException(e);
        }
    }
}
