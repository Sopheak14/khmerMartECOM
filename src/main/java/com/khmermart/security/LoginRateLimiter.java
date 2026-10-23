package com.khmermart.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {

  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  public boolean tryConsume(String key) {
    Bucket bucket =
        buckets.computeIfAbsent(
            key,
            k ->
                Bucket.builder()
                    .addLimit(
                        Bandwidth.builder()
                            .capacity(5)
                            .refillIntervally(5, Duration.ofMinutes(15))
                            .build())
                    .build());
    return bucket.tryConsume(1);
  }

  public void reset(String key) {
    buckets.remove(key);
  }
}
