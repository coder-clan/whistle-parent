package org.coderclan.whistle;

import org.coderclan.whistle.api.EventType;
import org.coderclan.whistle.exception.DuplicatedEventTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.util.*;

@Component
public class EventTypeRegistrar {
    private static final Logger log = LoggerFactory.getLogger(EventTypeRegistrar.class);
    @Autowired(required = false)
    private List<Collection<? extends EventType<?>>> publishingEventType;

    private Map<String, EventType<?>> map;

    @PostConstruct
    public void init() {
        if (CollectionUtils.isEmpty(publishingEventType)) {
            log.info("Publishing event type not configured!");
            return;
        }
        // init event Types;
        HashMap<String, EventType<?>> map = new HashMap<>();
        publishingEventType.stream().flatMap(Collection::stream).forEach(type -> {
            EventType<?> existsType = map.put(type.getName(), type);
            if ((!Objects.isNull(existsType)) && (!Objects.equals(existsType, type))) {
                throw new DuplicatedEventTypeException("There are two Event Type with the same name: " + type.getName() + ", they are: " + existsType.getClass().getName() + " and " + type.getClass().getName());
            }
        });
        this.map = Collections.unmodifiableMap(map);
    }

    public EventType<?> findEventType(String type) {
        if (Objects.isNull(map)) {
            return null;
        }
        return map.get(type);
    }
}
