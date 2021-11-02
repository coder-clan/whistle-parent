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

    private EventConsumer eventConsumer;


    public EventConsumer getEventConsumer() {
        return eventConsumer;
    }

    public void setEventConsumer(EventConsumer eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    @Override
    public void accept(EventContent content) {
        try {
            log.trace("Received event: type={}, content={}", this.eventConsumer.getSupportEventType(), content);
            eventConsumer.consume(content);
        } catch (Exception e) {
            throw new ConsumerException(e);
        }
    }
}
