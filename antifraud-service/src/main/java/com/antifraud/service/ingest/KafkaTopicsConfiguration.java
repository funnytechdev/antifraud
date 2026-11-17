package com.antifraud.service.ingest;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Declares the topics the service works with; they are created on startup if missing. */
@Configuration(proxyBeanMethods = false)
class KafkaTopicsConfiguration {

    @Bean
    NewTopic transactionsTopic(TopicsProperties topics) {
        return TopicBuilder.name(topics.transactions()).partitions(topics.partitions()).replicas(1).build();
    }

    @Bean
    NewTopic decisionsTopic(TopicsProperties topics) {
        return TopicBuilder.name(topics.decisions()).partitions(topics.partitions()).replicas(1).build();
    }
}
