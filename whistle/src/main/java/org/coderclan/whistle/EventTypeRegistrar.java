package org.coderclan.whistle;

import net.jcip.annotations.ThreadSafe;
import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventType;
import org.coderclan.whistle.exception.DuplicatedEventTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
@ThreadSafe
public class EventTypeRegistrar {
    private static final Logger log = LoggerFactory.getLogger(EventTypeRegistrar.class);

    /**
     * Key:  eventType name, value: eventType
     * Immutable, created by {@link Collections#unmodifiableMap(Map)}
     */
    private final Map<String, EventType<?>> map;

    public EventTypeRegistrar(@Autowired(required = false) List<Collection<? extends EventType<?>>> publishingEventType) {
        map = this.toMap(publishingEventType);
    }

    /**
     * @param publishingEventType
     * @return unmodified map. Key:  eventType name, value: eventType
     */
    private Map<String, EventType<?>> toMap(List<Collection<? extends EventType<?>>> publishingEventType) {
        if (CollectionUtils.isEmpty(publishingEventType)) {
            log.info("Publishing event type not configured!");
            return Collections.unmodifiableMap(Collections.emptyMap());
        }
        // init event Types;
        HashMap<String, EventType<?>> tempMap = new HashMap<>();
        publishingEventType.stream().flatMap(Collection::stream).forEach(type -> {
            EventType<?> existsType = tempMap.put(type.getName(), type);
            if ((!Objects.isNull(existsType)) && (!Objects.equals(existsType, type))) {
                throw new DuplicatedEventTypeException("There are two Event Type with the same name: " + type.getName() + ", they are: " + existsType.getClass().getName() + " and " + type.getClass().getName());
            }
        });
        return Collections.unmodifiableMap(tempMap);
    }

    public EventType<? extends EventContent> findEventType(String type) {
        if (Objects.isNull(map)) {
            return null;
        }
        return map.get(type);
    }
}
