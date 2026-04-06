package org.coderclan.whistle.mongodb;

import org.coderclan.whistle.Constants;
import org.coderclan.whistle.Event;
import org.coderclan.whistle.EventPersistenter;
import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */

public class MongodbEventPersistenter implements EventPersistenter {

    private final MongoTemplate template;

    public MongodbEventPersistenter(@Autowired MongoTemplate template) {
        this.template = template;
    }

    @Override
    public <C extends EventContent> String persistEvent(EventType<C> type, C content) {
        MongoEvent<C> ret = template.insert(new MongoEvent<C>(type, content));
        return ret.getId();
    }

    @Override
    public void confirmEvent(String persistentEventId) {
        template.update(MongoEvent.class)
                .matching(Query.query(Criteria.where("id").is(persistentEventId)))
                .apply(Update.update("confirmed", true)).first();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Event<?>> retrieveUnconfirmedEvent() {
        Query query = Query.query(Criteria.where("confirmed").is(false))
                // Order by retry asc, id desc
                // Retry ASC: events with lower retry counts are processed first (poison events sink down).
                // ID DESC: among events with the same retry count, newer events are processed first.
                .with(Sort.by(Sort.Direction.ASC, "retry").and(Sort.by(Sort.Direction.DESC, "id")))
                .limit(Constants.RETRY_BATCH_COUNT);
        List<MongoEvent<EventContent>> r = (List<MongoEvent<EventContent>>) (List<?>) template.find(query, MongoEvent.class);

        // If there are results, increment their retry counter by 1 in the database.
        if (!r.isEmpty()) {
            List<String> ids = r.stream()
                    .map(MongoEvent::getId)
                    // sort by id to avoid deadlock in mongodb
                    .sorted()
                    .collect(Collectors.toList());
            Query incQuery = Query.query(Criteria.where("id").in(ids));
            Update incUpdate = new Update().inc("retry", 1);
            template.update(MongoEvent.class)
                    .matching(incQuery)
                    .apply(incUpdate).all();
        }

        return r.stream()
                .map(e -> new Event<>(e.getId(), e.getType(), e.getContent()))
                .collect(Collectors.toList());
    }
}