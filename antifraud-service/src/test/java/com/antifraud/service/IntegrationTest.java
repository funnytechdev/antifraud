package com.antifraud.service;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Starts the whole application against Testcontainers.
 *
 * <p>All integration tests share this exact configuration, so Spring caches one application
 * context and the containers are started only once per test run.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
