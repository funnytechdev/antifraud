package com.antifraud.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AntifraudApplication {

    public static void main(String[] args) {
        SpringApplication.run(AntifraudApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
