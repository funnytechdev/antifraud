package com.antifraud.service.rules;

import io.lettuce.core.ClientOptions;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes the Redis client fail fast when the connection is lost.
 *
 * <p>By default Lettuce queues commands while it reconnects and fails them only when the command
 * timeout expires. For a service that has to answer within milliseconds an immediate error is
 * better: it lets the circuit breaker open sooner and the remaining rules decide the transaction.
 */
@Configuration(proxyBeanMethods = false)
class RedisClientConfiguration {

    @Bean
    LettuceClientOptionsBuilderCustomizer failFastWhenDisconnected() {
        return options -> options.disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
    }
}
