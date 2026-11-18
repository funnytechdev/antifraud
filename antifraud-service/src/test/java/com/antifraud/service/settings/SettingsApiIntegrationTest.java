package com.antifraud.service.settings;

import com.antifraud.contracts.DecisionEvent;
import com.antifraud.contracts.RuleHit;
import com.antifraud.contracts.Verdict;
import com.antifraud.service.IntegrationTest;
import com.antifraud.service.blacklist.BlacklistService;
import com.antifraud.service.blacklist.BlacklistType;
import com.antifraud.service.decision.DecisionEngine;
import com.antifraud.service.rules.BlacklistedCardRule;
import com.antifraud.service.rules.VelocityRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static com.antifraud.service.TestTransactions.newCardId;
import static com.antifraud.service.TestTransactions.purchase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@IntegrationTest
class SettingsApiIntegrationTest {

    private static final Instant START = Instant.parse("2026-03-01T10:00:00Z");

    @LocalServerPort
    private int port;
    @Autowired
    private DecisionEngine engine;
    @Autowired
    private SettingsService settings;
    @Autowired
    private SettingsRepository repository;
    @Autowired
    private BlacklistService blacklist;
    @Autowired
    private JsonMapper json;

    private RestClient api;

    @BeforeEach
    void setUp() {
        api = RestClient.builder()
                .baseUrl("http://localhost:" + port + "/api/v1")
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    /** Other tests rely on the default settings from the migration. */
    @AfterEach
    void restoreDefaults() {
        settings.updateRule(VelocityRule.CODE, true, 50, json.readTree("{\"threshold\": 10, \"windowSeconds\": 120}"));
        settings.updateRule(BlacklistedCardRule.CODE, true, 100, json.readTree("{}"));
        settings.updateScoring(40, 80);
    }

    @Test
    void listsEveryRuleWithItsCurrentSettings() {
        JsonNode rules = api.get().uri("/rules").retrieve().body(JsonNode.class);

        assertThat(rules.valueStream().map(rule -> rule.get("code").asString()))
                .contains(VelocityRule.CODE, BlacklistedCardRule.CODE);
        JsonNode velocity = api.get().uri("/rules/{code}", VelocityRule.CODE).retrieve().body(JsonNode.class);
        assertThat(velocity.get("enabled").asBoolean()).isTrue();
        assertThat(velocity.get("score").asInt()).isEqualTo(50);
        assertThat(velocity.get("parameters")).isEqualTo(json.readTree("{\"threshold\": 10, \"windowSeconds\": 120}"));
    }

    @Test
    void changedScoreAndParametersApplyToTheNextTransaction() {
        ResponseEntity<JsonNode> response = putRule(VelocityRule.CODE,
                "{\"enabled\": true, \"score\": 70, \"parameters\": {\"threshold\": 3, \"windowSeconds\": 60}}");
        String cardId = newCardId();

        List<DecisionEvent> decisions = IntStream.range(0, 3)
                .mapToObj(i -> engine.decide(purchase(cardId, START.plusSeconds(i * 10L))))
                .toList();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("score").asInt()).isEqualTo(70);
        assertThat(decisions.getLast().verdict()).isEqualTo(Verdict.REVIEW);
        assertThat(decisions.getLast().triggeredRules()).containsExactly(
                new RuleHit(VelocityRule.CODE, 70, "3 transactions on the card within 60 seconds"));
    }

    @Test
    void disabledRuleNoLongerAffectsDecisions() {
        String cardId = newCardId();
        blacklist.add(BlacklistType.CARD, List.of(cardId));

        putRule(BlacklistedCardRule.CODE, "{\"enabled\": false, \"score\": 100, \"parameters\": {}}");

        assertThat(engine.decide(purchase(cardId, START)).verdict()).isEqualTo(Verdict.ALLOW);
    }

    @Test
    void changedThresholdsApplyToTheNextTransaction() {
        String cardId = newCardId();
        blacklist.add(BlacklistType.CARD, List.of(cardId));

        ResponseEntity<JsonNode> response = api.put().uri("/scoring")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"reviewThreshold\": 100, \"blockThreshold\": 200}")
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(engine.decide(purchase(cardId, START)).verdict()).isEqualTo(Verdict.REVIEW);
    }

    @Test
    void settingsChangedByAnotherInstanceArriveWithTheRefresh() {
        String cardId = newCardId();
        blacklist.add(BlacklistType.CARD, List.of(cardId));

        // Written straight to the database, as another instance of the service would do.
        repository.updateRule(BlacklistedCardRule.CODE, true, 30, json.readTree("{}"));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(settings.current().rule(BlacklistedCardRule.CODE).orElseThrow().score()).isEqualTo(30));
        assertThat(engine.decide(purchase(cardId, START)).riskScore()).isEqualTo(30);
    }

    @Test
    void invalidParametersAreRejectedWithTheReason() {
        assertRejected(putRule(VelocityRule.CODE,
                "{\"enabled\": true, \"score\": 50, \"parameters\": {\"threshold\": 1, \"windowSeconds\": 120}}"),
                HttpStatus.BAD_REQUEST, "threshold must be at least 2");
        assertRejected(putRule(VelocityRule.CODE,
                "{\"enabled\": true, \"score\": 50, \"parameters\": {\"threshold\": 10, \"windowSeconds\": 120, \"windowMinutes\": 2}}"),
                HttpStatus.BAD_REQUEST, "windowMinutes");
        assertRejected(putRule(VelocityRule.CODE,
                "{\"enabled\": true, \"score\": 50, \"parameters\": {\"threshold\": 10}}"),
                HttpStatus.BAD_REQUEST, "windowSeconds");
        assertRejected(putRule(VelocityRule.CODE,
                "{\"enabled\": true, \"score\": -5, \"parameters\": {\"threshold\": 10, \"windowSeconds\": 120}}"),
                HttpStatus.BAD_REQUEST, "score");
        assertRejected(putRule("NO_SUCH_RULE", "{\"enabled\": true, \"score\": 5, \"parameters\": {}}"),
                HttpStatus.NOT_FOUND, "NO_SUCH_RULE");

        assertThat(settings.current().rule(VelocityRule.CODE).orElseThrow().parameters())
                .isEqualTo(new VelocityRule.Parameters(10, 120));
    }

    @Test
    void reviewThresholdAboveBlockThresholdIsRejected() {
        ResponseEntity<JsonNode> response = api.put().uri("/scoring")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"reviewThreshold\": 90, \"blockThreshold\": 80}")
                .retrieve()
                .toEntity(JsonNode.class);

        assertRejected(response, HttpStatus.BAD_REQUEST, "must not exceed");
        assertThat(settings.current().scoring().reviewThreshold()).isEqualTo(40);
    }

    private ResponseEntity<JsonNode> putRule(String code, String body) {
        return api.put().uri("/rules/{code}", code)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(JsonNode.class);
    }

    private static void assertRejected(ResponseEntity<JsonNode> response, HttpStatus status, String detail) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody().get("detail").asString()).contains(detail);
    }
}
