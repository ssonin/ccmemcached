package ssonin.ccmemcached.cache;

import com.github.benmanes.caffeine.cache.Cache;
import ssonin.ccmemcached.protocol.command.Command;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;

import static ssonin.ccmemcached.cache.CacheEntry.cacheEntry;

public final class CacheService {

  private static final Duration NEVER_EXPIRES = Duration.ofDays(365L * 100);
  private static final long MAX_RELATIVE_EXPTIME = 2_592_000L;

  private final Cache<String, CacheEntry> delegate;
  private final InstantSource clock;

  public CacheService(Cache<String, CacheEntry> delegate, InstantSource clock) {
    this.delegate = delegate;
    this.clock = clock;
  }

  public void put(Command command, byte[] data) {
    final var entry = cacheEntry()
      .flags(command.flags())
      .ttl(evaluateTtl(command.expTime()))
      .data(data)
      .build();
    delegate.put(command.key(), entry);
  }

  private Duration evaluateTtl(long expTime) {
    if (expTime <= 0) {
      return NEVER_EXPIRES;
    } else if (expTime <= MAX_RELATIVE_EXPTIME) {
      return Duration.ofSeconds(expTime);
    } else {
      final var expiry = Instant.ofEpochSecond(expTime);
      return Duration.between(clock.instant(), expiry);
    }
  }
}
