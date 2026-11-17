package com.antifraud.generator.publish;

import com.antifraud.generator.GeneratorProperties;
import com.antifraud.generator.GeneratorProperties.FeedSettings;
import com.antifraud.generator.world.Card;
import com.antifraud.generator.world.Merchant;
import com.antifraud.generator.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Tells the antifraud service which cards are stolen and which merchants are fraudulent.
 *
 * <p>It plays the role of the external feeds a bank receives from payment systems: the generator
 * owns the simulated world, so it is the only one who knows what has been compromised.
 */
@Component
public class BlacklistFeed {

    private static final Logger log = LoggerFactory.getLogger(BlacklistFeed.class);

    private final FeedSettings settings;
    private final World world;
    private final RestClient restClient;

    public BlacklistFeed(GeneratorProperties properties, World world, RestClient.Builder restClientBuilder) {
        this.settings = properties.blacklistFeed();
        this.world = world;
        this.restClient = restClientBuilder.baseUrl(settings.baseUrl()).build();
    }

    /**
     * Registers the blacklist, waiting for the antifraud API to come up if necessary.
     *
     * @return {@code true} if the blacklist was delivered or the feed is disabled
     */
    public boolean publish() throws InterruptedException {
        if (!settings.enabled()) {
            return true;
        }
        List<String> cards = world.compromisedCards().stream().map(Card::id).toList();
        List<String> merchants = world.fraudMerchants().stream().map(Merchant::id).toList();

        for (int attempt = 1; attempt <= settings.maxAttempts(); attempt++) {
            try {
                post("cards", cards);
                post("merchants", merchants);
                log.info("Registered {} compromised cards and {} fraud merchants at {}",
                        cards.size(), merchants.size(), settings.baseUrl());
                return true;
            } catch (RestClientException e) {
                log.warn("Antifraud API is not reachable (attempt {} of {}): {}",
                        attempt, settings.maxAttempts(), e.getMessage());
                Thread.sleep(settings.retryDelay().toMillis());
            }
        }
        log.error("Blacklist was not delivered to {}; blacklist scenarios will go undetected", settings.baseUrl());
        return false;
    }

    private void post(String type, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        restClient.post()
                .uri("/api/v1/blacklist/{type}", type)
                .body(Map.of("values", values))
                .retrieve()
                .toBodilessEntity();
    }
}
