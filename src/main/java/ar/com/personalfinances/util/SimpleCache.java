package ar.com.personalfinances.util;

import lombok.Getter;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class SimpleCache {

    private final Map<String, SimpleCacheEntry> cache = new ConcurrentHashMap<>();

    public void put(String key, Object value) {
        put(key, value, null);
    }

    public void put(String key, Object value, Instant expiresAt) {
        cache.put(key, new SimpleCacheEntry(value, expiresAt));
    }

    public Optional<Object> get(String key) {
        SimpleCacheEntry entry = cache.get(key);
        if (entry == null) {
            return Optional.empty();
        }

        if (entry.isExpired()) {
            cache.remove(key);
            return Optional.empty();
        }

        return Optional.of(entry.getValue());
    }

    public Object getOrCompute(String key, Supplier<Object> supplier) {
        return getOrCompute(key, supplier, null);
    }

    public Object getOrCompute(String key, Supplier<Object> supplier, Instant expiresAt) {
        return get(key).orElseGet(() -> {
            Object value = supplier.get();
            put(key, value, expiresAt);
            return value;
        });
    }

    public void invalidate(String key) {
        cache.remove(key);
    }

    public void clear() {
        cache.clear();
    }

    private static class SimpleCacheEntry {

        @Getter
        private final Object value;
        private final Instant expiresAt;

        private SimpleCacheEntry(Object value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        public boolean isExpired() {
            return expiresAt != null && Instant.now().isAfter(expiresAt);
        }
    }
}
