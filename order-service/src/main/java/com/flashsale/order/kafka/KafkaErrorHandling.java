package com.flashsale.order.kafka;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.core.JacksonException;

/**
 * Retries a failed record with exponential backoff, then publishes it to {topic}.DLT and moves on,
 * so one bad message cannot block its partition. Malformed messages skip the retries.
 */
@Configuration
public class KafkaErrorHandling {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, ex) -> new TopicPartition(record.topic() + KafkaTopics.DLT_SUFFIX, record.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff(500, 2.0);
        backOff.setMaxAttempts(3);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class);
        return handler;
    }
}
