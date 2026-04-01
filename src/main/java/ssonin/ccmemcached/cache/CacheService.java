package ssonin.ccmemcached.cache;

import com.github.benmanes.caffeine.cache.Cache;
import ssonin.ccmemcached.protocol.command.AddCommand;
import ssonin.ccmemcached.protocol.command.SetCommand;
import ssonin.ccmemcached.protocol.command.StorageCommand;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;

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

  public Map<String, CacheEntry> getAllPresent(List<String> keys) {
    return delegate.getAllPresent(keys);
  }

  public boolean delete(String key) {
    return delegate.asMap().remove(key) != null;
  }

  public void cleanUp() {
    delegate.cleanUp();
  }

  public void put(SetCommand command, byte[] data) {
    delegate.put(command.key(), toCacheEntry(command, data));
  }

  public boolean add(AddCommand command, byte[] data) {
    return delegate.asMap().putIfAbsent(command.key(), toCacheEntry(command, data)) == null;
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

  private CacheEntry toCacheEntry(StorageCommand command, byte[] data) {
    return cacheEntry()
      .flags(command.flags())
      .ttl(evaluateTtl(command.expTime()))
      .data(data)
      .build();
  }
}
