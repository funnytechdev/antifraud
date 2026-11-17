package com.antifraud.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class AntifraudApplicationTest {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void startsAgainstRealInfrastructureAndAppliesTheSchema() {
        Long decisions = jdbc.sql("SELECT count(*) FROM decisions").query(Long.class).single();

        assertThat(decisions).isNotNegative();
    }
}
