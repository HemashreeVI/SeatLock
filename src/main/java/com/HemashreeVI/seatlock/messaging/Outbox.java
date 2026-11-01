package com.HemashreeVI.seatlock.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Records a message to be published, as part of the caller's transaction. */
@Component
@RequiredArgsConstructor
public class Outbox {

    private final OutboxRepository repository;
    private final JsonMapper json;

    /** MANDATORY: an outbox write outside a business transaction would defeat the whole pattern. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void add(String aggregateType, Object aggregateId, String eventType, String topic, Object payload) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType(aggregateType);
        event.setAggregateId(String.valueOf(aggregateId));
        event.setEventType(eventType);
        event.setTopic(topic);
        event.setPayload(json.writeValueAsString(payload));
        repository.save(event);
    }
}
