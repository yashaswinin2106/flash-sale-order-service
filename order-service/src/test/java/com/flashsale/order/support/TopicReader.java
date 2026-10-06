package com.flashsale.order.support;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/** Reads a topic from the beginning with its own consumer group, to check what was published. */
public class TopicReader implements AutoCloseable {

    private final KafkaConsumer<String, String> consumer;

    public TopicReader(String bootstrapServers, String topic) {
        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(List.of(topic));
    }

    /** Waits up to the timeout for a record matching the predicate. */
    public Optional<ConsumerRecord<String, String>> await(Predicate<ConsumerRecord<String, String>> matches,
                                                          Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                if (matches.test(record)) {
                    return Optional.of(record);
                }
            }
        }
        return Optional.empty();
    }

    /** Counts matching records seen within the given time. */
    public int count(Predicate<ConsumerRecord<String, String>> matches, Duration within) {
        Instant deadline = Instant.now().plus(within);
        int count = 0;
        while (Instant.now().isBefore(deadline)) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                if (matches.test(record)) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public void close() {
        consumer.close();
    }
}
