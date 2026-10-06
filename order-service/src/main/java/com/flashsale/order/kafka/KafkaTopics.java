package com.flashsale.order.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Topic names, and creation of the topics on startup if they do not exist. */
@Configuration
public class KafkaTopics {

    public static final String ORDER_CREATED = "order.created";
    public static final String PAYMENT_RESULT = "payment.result";
    public static final String DLT_SUFFIX = ".DLT";

    // Dead-letter topics have the same partition count, so a failed record keeps its partition.
    private static final int PARTITIONS = 3;

    @Bean
    NewTopic orderCreatedTopic() {
        return TopicBuilder.name(ORDER_CREATED).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic orderCreatedDltTopic() {
        return TopicBuilder.name(ORDER_CREATED + DLT_SUFFIX).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic paymentResultTopic() {
        return TopicBuilder.name(PAYMENT_RESULT).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic paymentResultDltTopic() {
        return TopicBuilder.name(PAYMENT_RESULT + DLT_SUFFIX).partitions(PARTITIONS).replicas(1).build();
    }
}
