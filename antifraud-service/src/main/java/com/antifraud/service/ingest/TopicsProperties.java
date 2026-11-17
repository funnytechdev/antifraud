package com.antifraud.service.ingest;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param transactions topic the service reads purchases from
 * @param decisions    topic the service publishes decisions to
 * @param partitions   number of partitions of both topics; it caps the consumer parallelism
 */
@ConfigurationProperties("antifraud.topics")
public record TopicsProperties(String transactions, String decisions, int partitions) {
}
