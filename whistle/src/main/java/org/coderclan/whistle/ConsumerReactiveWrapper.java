package org.coderclan.whistle;

import org.coderclan.whistle.api.EventConsumer;
import org.coderclan.whistle.api.EventContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public class ConsumerReactiveWrapper implements Function<Flux<EventContent>, Mono<Void>> {
    private static final Logger log = LoggerFactory.getLogger(ConsumerReactiveWrapper.class);

    private EventConsumer<EventContent> eventConsumer;

    public void setEventConsumer(EventConsumer<EventContent> eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    @Override
    public Mono<Void> apply(Flux<EventContent> flux) {
        return flux.map(e -> {
            ConsumerWrapper.consume(eventConsumer, e);
            return true;
        }).then();
    }
}
