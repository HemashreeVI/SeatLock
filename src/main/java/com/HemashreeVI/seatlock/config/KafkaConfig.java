package com.HemashreeVI.seatlock.config;

import com.HemashreeVI.seatlock.messaging.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic paymentRequestedTopic() {
        return TopicBuilder.name(Topics.PAYMENT_REQUESTED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentCompletedTopic() {
        return TopicBuilder.name(Topics.PAYMENT_COMPLETED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic bookingEventsTopic() {
        return TopicBuilder.name(Topics.BOOKING_EVENTS).partitions(3).replicas(1).build();
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
