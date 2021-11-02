package org.coderclan.whistle.example.producer.api;

import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventType;

public enum ExampleEventType implements EventType {
    PREDATOR_IN_SIGHT("org-coderclan-whistle-example-PredatorInSight", PredatorInformation.class),
    PREY_CAUGHT("org-coderclan-whistle-example-PreyCaught", PreyInformation.class),
    //
    ;

    /**
     * event name, should be unique in the universe. e.g. "org.coderclan.re.demo.stock.price.changed"
     */
    private String eventName;

    /**
     * event content Type
     */
    private Class<? extends EventContent> contentType;


    ExampleEventType(String eventName, Class<? extends EventContent> contentType) {
        this.eventName = eventName;
        this.contentType = contentType;
    }

    @Override
    public String getName() {
        return eventName;
    }

    @Override
    public Class<? extends EventContent> getContentType() {
        return contentType;
    }
}
