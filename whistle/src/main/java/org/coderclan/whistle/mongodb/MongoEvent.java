package org.coderclan.whistle.mongodb;

import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
@Document("sys_event_out")
public class MongoEvent<C extends EventContent> {
    @Id
    private String id;
    private final EventType<C> type;
    private final C content;
    @Indexed(partialFilter = "{confirmed:false}")
    private Boolean confirmed = false;
    private Integer retry;

    public MongoEvent(EventType<C> type, C content) {
        this.type = type;
        this.content = content;
    }

    public String getId() {
        return id;
    }

    public EventType<C> getType() {
        return type;
    }

    public C getContent() {
        return content;
    }
}
