package org.coderclan.whistle;

import org.coderclan.whistle.api.EventContent;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

public class ReactorEventQueue implements EventQueue {
    private final Sinks.Many<Message<EventContent>> sink = Sinks.many().unicast().onBackpressureBuffer();

    @Override
    public boolean offer(Event event) {
        Message<EventContent> message = MessageBuilder.<EventContent>withPayload(event.getContent())
                .setHeader("spring.cloud.stream.sendto.destination", event.getType().getName())
                .setHeader(Constants.EVENT_PERSISTENT_ID_HEADER, event.getPersistentEventId())
                .build();
        sink.emitNext(message, Sinks.EmitFailureHandler.FAIL_FAST);
        return true;
    }

    @Override
    public Event take() throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(Event event) {
        return false;
    }

    public Flux<Message<EventContent>> asFlux() {
        return sink.asFlux();
    }
}
