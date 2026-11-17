package com.antifraud.generator;

import com.antifraud.generator.GeneratorProperties.WorldSettings;
import com.antifraud.generator.simulation.TransactionSimulator;
import com.antifraud.generator.world.World;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.time.Clock;
import java.util.Random;

@Configuration(proxyBeanMethods = false)
class GeneratorConfiguration {

    @Bean
    World world(GeneratorProperties properties) {
        WorldSettings settings = properties.world();
        return World.generate(settings.customers(), settings.merchants(), settings.compromisedCards(),
                settings.fraudMerchants(), new Random(properties.seed()));
    }

    @Bean
    TransactionSimulator transactionSimulator(World world, GeneratorProperties properties) {
        return new TransactionSimulator(world, new Random(properties.seed() + 1), properties.fraudProbability());
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    NewTopic transactionsTopic(GeneratorProperties properties) {
        return TopicBuilder.name(properties.topics().transactions())
                .partitions(properties.topics().partitions())
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic labelsTopic(GeneratorProperties properties) {
        return TopicBuilder.name(properties.topics().labels())
                .partitions(properties.topics().partitions())
                .replicas(1)
                .build();
    }
}
