package com.antifraud.service.blacklist;

import com.antifraud.service.IntegrationTest;
import com.antifraud.service.blacklist.BlacklistController.AddRequest;
import com.antifraud.service.blacklist.BlacklistController.AddResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;

import static com.antifraud.service.TestTransactions.newCardId;
import static com.antifraud.service.TestTransactions.newMerchantId;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class BlacklistApiIntegrationTest {

    @LocalServerPort
    private int port;

    private RestClient api;

    @BeforeEach
    void setUp() {
        api = RestClient.builder()
                .baseUrl("http://localhost:" + port + "/api/v1/blacklist")
                // Statuses are asserted explicitly, so an error response must not throw.
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    @Test
    void addedCardsAppearOnTheListAndDuplicatesAreNotCountedTwice() {
        String first = newCardId();
        String second = newCardId();

        assertThat(add("cards", List.of(first, second))).isEqualTo(2);
        assertThat(add("cards", List.of(first))).isZero();

        assertThat(list("cards")).contains(first, second);
        assertThat(list("merchants")).doesNotContain(first, second);
    }

    @Test
    void removedMerchantDisappearsFromTheList() {
        String merchant = newMerchantId();
        add("merchants", List.of(merchant));

        assertThat(delete("merchants", merchant)).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(list("merchants")).doesNotContain(merchant);
        assertThat(delete("merchants", merchant)).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unknownBlacklistIsNotFound() {
        HttpStatusCode status = api.get().uri("/countries").retrieve().toBodilessEntity().getStatusCode();

        assertThat(status).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void blankValueIsRejected() {
        HttpStatusCode status = api.post().uri("/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AddRequest(List.of("card-1", " ")))
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();

        assertThat(status).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(list("cards")).doesNotContain("card-1");
    }

    private long add(String type, List<String> values) {
        return api.post().uri("/{type}", type)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AddRequest(values))
                .retrieve()
                .body(AddResponse.class)
                .added();
    }

    private List<String> list(String type) {
        return api.get().uri("/{type}", type)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private HttpStatusCode delete(String type, String value) {
        return api.delete().uri("/{type}/{value}", type, value).retrieve().toBodilessEntity().getStatusCode();
    }
}
