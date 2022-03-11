package org.coderclan.whistle;

import org.coderclan.whistle.api.EventContent;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public interface EventContentSerializer {
    EventContent toEventContent(String json, Class<? extends EventContent> contentType);

    <C extends EventContent> String toJson(C content);
}
