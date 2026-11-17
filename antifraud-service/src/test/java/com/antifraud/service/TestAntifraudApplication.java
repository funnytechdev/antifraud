package com.antifraud.service;

import org.springframework.boot.SpringApplication;

/**
 * Runs the service locally with Kafka, Redis and PostgreSQL started in containers:
 * {@code ./gradlew :antifraud-service:bootTestRun}. No Docker Compose is needed.
 */
public class TestAntifraudApplication {

    public static void main(String[] args) {
        SpringApplication.from(AntifraudApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
