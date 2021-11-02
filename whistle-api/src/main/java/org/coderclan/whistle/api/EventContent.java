package org.coderclan.whistle.api;

import java.io.Serializable;
import java.util.UUID;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public abstract class EventContent implements Serializable {
    private String idempotentId = UUID.randomUUID().toString();

    public String getIdempotentId() {
        return idempotentId;
    }
}
