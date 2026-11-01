package com.HemashreeVI.seatlock.config;

import com.HemashreeVI.seatlock.messaging.MessagingMode;
import com.HemashreeVI.seatlock.messaging.Topics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** Kafka topics and error handling; not loaded in local (broker-less) messaging mode. */
@Configuration
@ConditionalOnProperty(name = MessagingMode.PROPERTY, havingValue = MessagingMode.KAFKA, matchIfMissing = true)
public class KafkaConfig {

    /** Partitions per topic. Dead-letter topics MUST have the same count (see below). */
    static final int PARTITIONS = 3;

    @Bean
    KafkaAdmin.NewTopics seatlockTopics() {
        // The dead-letter recoverer publishes a failed record to the SAME partition number on
        // "<topic>-dlt". If Kafka auto-created the DLT it would get the broker default (often 1
        // partition) and records from partitions 1 and 2 could not be parked, so declare them.
        return new KafkaAdmin.NewTopics(
                topic(Topics.PAYMENT_REQUESTED), topic(Topics.PAYMENT_REQUESTED + Topics.DLT_SUFFIX),
                topic(Topics.PAYMENT_COMPLETED), topic(Topics.PAYMENT_COMPLETED + Topics.DLT_SUFFIX),
                topic(Topics.BOOKING_EVENTS), topic(Topics.BOOKING_EVENTS + Topics.DLT_SUFFIX));
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(1).build();
    }

    /**
     * A failing message is retried 3 times, 1s apart; after that it is parked on "<topic>-dlt"
     * (dead-letter topic) so one poison message can't block the partition forever.
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> kafka) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafka), new FixedBackOff(1000L, 3));
    }
}
