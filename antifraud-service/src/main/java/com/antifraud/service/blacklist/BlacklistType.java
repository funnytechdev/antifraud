package com.antifraud.service.blacklist;

import java.util.Arrays;
import java.util.Optional;

public enum BlacklistType {
    CARD("cards", "blacklist:cards"),
    MERCHANT("merchants", "blacklist:merchants");

    private final String path;
    private final String redisKey;

    BlacklistType(String path, String redisKey) {
        this.path = path;
        this.redisKey = redisKey;
    }

    /** Name of the list in the REST API, e.g. {@code /api/v1/blacklist/cards}. */
    public String path() {
        return path;
    }

    public String redisKey() {
        return redisKey;
    }

    public static Optional<BlacklistType> fromPath(String path) {
        return Arrays.stream(values()).filter(type -> type.path.equals(path)).findFirst();
    }
}
