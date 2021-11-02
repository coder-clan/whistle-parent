package org.coderclan.whistle.example.consumer;

import org.coderclan.whistle.api.EventConsumer;
import org.coderclan.whistle.api.EventType;
import org.coderclan.whistle.example.producer.api.ExampleEventType;
import org.coderclan.whistle.example.producer.api.PredatorInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PredatorInSightEventConsumer implements EventConsumer<PredatorInformation> {
    private static final Logger log = LoggerFactory.getLogger(PredatorInSightEventConsumer.class);

    @Override
    public EventType<PredatorInformation> getSupportEventType() {
        return ExampleEventType.PREDATOR_IN_SIGHT;
    }

    @Override
    public boolean consume(PredatorInformation message) throws Exception {
        log.info("Holy shit, " + message.getNumber() + " " + message.getPredatorType() + "(s)! What should we do?");
        return true;
    }
}
