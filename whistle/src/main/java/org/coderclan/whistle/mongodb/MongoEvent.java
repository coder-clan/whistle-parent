package org.coderclan.whistle.mongodb;

import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
@Document("sys_event_out")
@CompoundIndex(
        name = "unconfirmed_retry_order",
        def = "{'retry': 1, '_id': -1}",
        partialFilter = "{'confirmed': false}"
)
public class MongoEvent<C extends EventContent> {
    @Id
    private String id;
    private EventType<C> type;
    private C content;
    private Boolean confirmed = false;
    private Integer retry = 0;

    public MongoEvent() {
    }

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

    public Boolean getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(Boolean confirmed) {
        this.confirmed = confirmed;
    }

    public Integer getRetry() {
        return retry;
    }

    public void setRetry(Integer retry) {
        this.retry = retry;
    }
}
