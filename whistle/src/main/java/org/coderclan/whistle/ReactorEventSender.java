package org.coderclan.whistle;

import org.coderclan.whistle.api.EventContent;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public class ReactorEventSender implements EventSender {
    private final Sinks.Many<Message<EventContent>> sink = Sinks.many().unicast().onBackpressureBuffer();

    @Override
    public Flux<Message<EventContent>> asFlux() {
        return sink.asFlux();
    }

    @Override
    public void send(Event<? extends EventContent> event) {
        Message<EventContent> message = MessageBuilder.<EventContent>withPayload(event.getContent())
                .setHeader("spring.cloud.stream.sendto.destination", event.getType().getName())
                .setHeader(Constants.EVENT_PERSISTENT_ID_HEADER, event.getPersistentEventId())
                .build();
        sink.emitNext(message, Sinks.EmitFailureHandler.FAIL_FAST);
    }
}
