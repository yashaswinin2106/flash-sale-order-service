package com.flashsale.payment;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.core.JacksonException;

@Configuration
public class KafkaConfig {

    private static final String DLT_SUFFIX = ".DLT";
    private static final int PARTITIONS = 3;

    @Bean
    NewTopic orderCreatedTopic() {
        return TopicBuilder.name(OrderCreatedListener.ORDER_CREATED).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic orderCreatedDltTopic() {
        return TopicBuilder.name(OrderCreatedListener.ORDER_CREATED + DLT_SUFFIX).partitions(PARTITIONS).replicas(1)
                .build();
    }

    @Bean
    NewTopic paymentResultTopic() {
        return TopicBuilder.name(PaymentProcessor.PAYMENT_RESULT).partitions(PARTITIONS).replicas(1).build();
    }

    /**
     * Retries a failed record with exponential backoff, then publishes it to order.created.DLT and
     * moves on, so one bad message cannot block its partition. Malformed messages skip the retries.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff(500, 2.0);
        backOff.setMaxAttempts(3);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class);
        return handler;
    }
}
