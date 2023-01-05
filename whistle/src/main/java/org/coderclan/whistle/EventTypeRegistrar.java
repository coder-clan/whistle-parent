package org.coderclan.whistle;

import net.jcip.annotations.ThreadSafe;
import org.coderclan.whistle.api.EventConsumer;
import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventType;
import org.coderclan.whistle.exception.DuplicatedEventTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.function.Consumer;

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
    private final Map<String, EventType<?>> eventTypeMap;


    public EventTypeRegistrar(@Autowired(required = false) List<Collection<? extends EventType<?>>> publishingEventType,
                              @Autowired(required = false) List<EventConsumer<?>> consumers
    ) {
        this.eventTypeMap = this.toMap(publishingEventType, consumers);
    }

    /**
     * @param publishingEventType
     * @param consumers
     * @return unmodified map. Key:  eventType name, value: eventType
     */
    private Map<String, EventType<?>> toMap(List<Collection<? extends EventType<?>>> publishingEventType, List<EventConsumer<?>> consumers) {
        HashMap<String, EventType<?>> tempMap = new HashMap<>();

        final Consumer<EventType<?>> putEventTypeToTheTempMap = type -> {
            EventType<?> existsType = tempMap.put(type.getName(), type);
            if ((!Objects.isNull(existsType)) && (!Objects.equals(existsType, type))) {
                throw new DuplicatedEventTypeException("There are two Event Type with the same name: " + type.getName() + ", they are: " + existsType.getClass().getName() + " and " + type.getClass().getName());
            }
        };

        if (CollectionUtils.isEmpty(publishingEventType)) {
            log.info("Publishing event type not configured!");
        } else {
            publishingEventType.stream().flatMap(Collection::stream).forEach(putEventTypeToTheTempMap);
        }

        if (CollectionUtils.isEmpty(consumers)) {
            log.info("There is no Event Consumer.");
        } else {
            consumers.stream().map(EventConsumer::getSupportEventType).forEach(putEventTypeToTheTempMap);
        }

        return Collections.unmodifiableMap(tempMap);
    }

    public EventType<? extends EventContent> findEventType(String type) {
        if (Objects.isNull(eventTypeMap)) {
            return null;
        }
        return eventTypeMap.get(type);
    }
}
