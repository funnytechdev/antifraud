package com.antifraud.service;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real Kafka, Redis and PostgreSQL in containers, the same versions as in {@code compose.yaml}.
 *
 * <p>{@link ServiceConnection} points the application at the containers, so no connection
 * properties have to be overridden in tests.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer("apache/kafka:4.4.0");
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>("redis:8.10-alpine").withExposedPorts(6379);
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:18.7-alpine");
    }
}
