package com.flashsale.payment;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts Postgres and Kafka once per test JVM. The schema comes from the order service's
 * Flyway migrations, which own the database.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

	static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.0"));

	static {
		Startables.deepStart(POSTGRES, KAFKA).join();
	}

	@Bean
	DynamicPropertyRegistrar containerProperties() {
		return registry -> {
			registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
			registry.add("spring.datasource.username", POSTGRES::getUsername);
			registry.add("spring.datasource.password", POSTGRES::getPassword);
			registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
			registry.add("spring.flyway.locations", () -> "filesystem:../order-service/src/main/resources/db/migration");
		};
	}

	public static String kafkaBootstrapServers() {
		return KAFKA.getBootstrapServers();
	}

}
