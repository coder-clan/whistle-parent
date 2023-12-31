package org.coderclan.whistle.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public abstract class AbstractEventContent implements EventContent {

    private String idempotentId = UUID.randomUUID().toString();

    private Instant time = Instant.now();

    /**
     * @return Idempotent ID, could be used to check if duplication of Events.
     */
    public String getIdempotentId() {
        return idempotentId;
    }

    /**
     * @return Creating time of the Event, could be used to check the order of Events.
     */
    public Instant getTime() {
        return time;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractEventContent content = (AbstractEventContent) o;
        return Objects.equals(idempotentId, content.idempotentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idempotentId);
    }
}
