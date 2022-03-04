package org.coderclan.whistle;

import net.jcip.annotations.ThreadSafe;
import org.coderclan.whistle.api.EventType;
import org.coderclan.whistle.exception.DuplicatedEventTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 *
 */
@ThreadSafe
public class EventTypeRegistrar {
    private static final Logger log = LoggerFactory.getLogger(EventTypeRegistrar.class);
    @Autowired(required = false)
    private List<Collection<? extends EventType<?>>> publishingEventType;

    /**
     * Immutable, created by {@link Collections#unmodifiableMap(Map)}
     */
    private volatile Map<String, EventType<?>> map;

    @PostConstruct
    public void init() {
        if (CollectionUtils.isEmpty(publishingEventType)) {
            log.info("Publishing event type not configured!");
            return;
        }
        // init event Types;
        HashMap<String, EventType<?>> tempMap = new HashMap<>();
        publishingEventType.stream().flatMap(Collection::stream).forEach(type -> {
            EventType<?> existsType = tempMap.put(type.getName(), type);
            if ((!Objects.isNull(existsType)) && (!Objects.equals(existsType, type))) {
                throw new DuplicatedEventTypeException("There are two Event Type with the same name: " + type.getName() + ", they are: " + existsType.getClass().getName() + " and " + type.getClass().getName());
            }
        });
        this.map = Collections.unmodifiableMap(tempMap);
    }

    public EventType<?> findEventType(String type) {
        if (Objects.isNull(map)) {
            return null;
        }
        return map.get(type);
    }
}
