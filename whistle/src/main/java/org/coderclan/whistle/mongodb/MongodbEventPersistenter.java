package org.coderclan.whistle.mongodb;

import com.mongodb.client.result.UpdateResult;
import org.coderclan.whistle.Constants;
import org.coderclan.whistle.Event;
import org.coderclan.whistle.EventPersistenter;
import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventType;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private MongoTemplate template;

    @Override
    public <C extends EventContent> String persistEvent(EventType<C> type, C content) {
        MongoEvent<C> ret = template.insert(new MongoEvent<C>(type, content));
        return ret.getId();
    }

    @Override
    public void confirmEvent(String persistentEventId) {
        UpdateResult ret = template.update(MongoEvent.class)
                .matching(Query.query(Criteria.where("id").is(persistentEventId)))
                .apply(Update.update("confirmed", true)).first();
    }

    @Override
    public List<Event<?>> retrieveUnconfirmedEvent() {
        List<MongoEvent> r = template.find(Query.query(Criteria.where("confirmed").is(false)).limit(Constants.MAX_QUEUE_COUNT), MongoEvent.class);
        return r.stream().map(e -> new Event<EventContent>(
                e.getId(), e.getType(), e.getContent()
        )).collect(Collectors.toList());
    }
}
