package org.coderclan.whistle.api;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public abstract class EventContent implements Serializable {
    private String idempotentId = UUID.randomUUID().toString();

    public String getIdempotentId() {
        return idempotentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventContent content = (EventContent) o;
        return Objects.equals(idempotentId, content.idempotentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idempotentId);
    }
}
