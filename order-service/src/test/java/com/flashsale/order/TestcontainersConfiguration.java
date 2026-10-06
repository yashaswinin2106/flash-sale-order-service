package com.flashsale.order;

import com.flashsale.order.support.TestData;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts Postgres and Redis once per test JVM. Every Spring test context shares them, and they are
 * not Spring beans, so closing one context does not stop the containers under the others.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7"))
			.withExposedPorts(6379);

	static {
		Startables.deepStart(POSTGRES, REDIS).join();
	}

	@Bean
	DynamicPropertyRegistrar containerProperties() {
		return registry -> {
			registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
			registry.add("spring.datasource.username", POSTGRES::getUsername);
			registry.add("spring.datasource.password", POSTGRES::getPassword);
			registry.add("spring.data.redis.host", REDIS::getHost);
			registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		};
	}

	@Bean
	TestData testData(JdbcTemplate jdbc, StringRedisTemplate redis) {
		return new TestData(jdbc, redis);
	}

}
