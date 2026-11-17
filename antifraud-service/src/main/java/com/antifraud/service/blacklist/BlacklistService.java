package com.antifraud.service.blacklist;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * Blacklists of compromised cards and suspicious merchants, kept as Redis sets.
 *
 * <p>A set gives a constant-time lookup on the scoring path, and a change made through the API is
 * visible to every instance of the service at once.
 */
@Service
public class BlacklistService {

    private final StringRedisTemplate redis;

    public BlacklistService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean contains(BlacklistType type, String value) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(type.redisKey(), value));
    }

    /**
     * @return number of values that were not on the list before
     */
    public long add(BlacklistType type, Collection<String> values) {
        if (values.isEmpty()) {
            return 0;
        }
        Long added = redis.opsForSet().add(type.redisKey(), values.toArray(String[]::new));
        return added == null ? 0 : added;
    }

    /**
     * @return {@code false} if the value was not on the list
     */
    public boolean remove(BlacklistType type, String value) {
        Long removed = redis.opsForSet().remove(type.redisKey(), value);
        return removed != null && removed > 0;
    }

    public Set<String> list(BlacklistType type) {
        Set<String> members = redis.opsForSet().members(type.redisKey());
        return members == null ? Set.of() : new TreeSet<>(members);
    }
}
