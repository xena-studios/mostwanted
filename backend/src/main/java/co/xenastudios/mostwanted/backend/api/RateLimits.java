package co.xenastudios.mostwanted.backend.api;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Requests per client, in memory, behind Cloudflare's own limits. A bucket per IP
 * refills steadily; full buckets are dropped now and then so the map stays small.
 */
final class RateLimits {

    private final long perMinute;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    RateLimits(long perMinute) {
        this.perMinute = perMinute;
    }

    /** Takes one request from their allowance; false if they've used it up. */
    boolean allow(String client) {
        return buckets.computeIfAbsent(client, key -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)).build())
                .build()).tryConsume(1);
    }

    /** Forgets clients who are back to a full allowance: they'd get the same new bucket. */
    void sweep() {
        buckets.values().removeIf(bucket -> bucket.getAvailableTokens() >= perMinute);
    }

    int size() {
        return buckets.size();
    }
}
