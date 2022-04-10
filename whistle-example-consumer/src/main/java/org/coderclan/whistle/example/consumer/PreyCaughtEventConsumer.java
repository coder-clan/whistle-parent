package org.coderclan.whistle.example.consumer;

import org.coderclan.whistle.api.EventConsumer;
import org.coderclan.whistle.api.EventType;
import org.coderclan.whistle.example.producer.api.ExampleEventType;
import org.coderclan.whistle.example.producer.api.PreyInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PreyCaughtEventConsumer implements EventConsumer<PreyInformation> {
    private static final Logger log = LoggerFactory.getLogger(PreyCaughtEventConsumer.class);


    @Override
    public EventType<PreyInformation> getSupportEventType() {
        return ExampleEventType.PREY_CAUGHT;
    }

    @Override
    public boolean consume(PreyInformation message) throws Exception {
        log.info("Thank God to give us " + message.getNumber() + " " + message.getPreyType() + "(s) for dinner.");
//        if (Math.random()>0.9) {
//            throw new RuntimeException("Come on, here is an error.");
//        }
        return true;
    }
}
