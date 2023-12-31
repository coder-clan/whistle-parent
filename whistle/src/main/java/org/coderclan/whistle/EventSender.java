package org.coderclan.whistle;

import org.coderclan.whistle.api.EventContent;
import org.springframework.messaging.Message;
import reactor.core.publisher.Flux;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public interface EventSender {
    void send(Event<? extends EventContent> event);

    Flux<Message<EventContent>> asFlux();
}
