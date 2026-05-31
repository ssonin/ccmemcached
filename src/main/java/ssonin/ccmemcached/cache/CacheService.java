package ssonin.ccmemcached.cache;

import com.github.benmanes.caffeine.cache.Cache;
import ssonin.ccmemcached.protocol.command.AddCommand;
import ssonin.ccmemcached.protocol.command.AppendCommand;
import ssonin.ccmemcached.protocol.command.CasCommand;
import ssonin.ccmemcached.protocol.command.DecrCommand;
import ssonin.ccmemcached.protocol.command.IncrCommand;
import ssonin.ccmemcached.protocol.command.MetadataStorageCommand;
import ssonin.ccmemcached.protocol.command.NumericCommand;
import ssonin.ccmemcached.protocol.command.ReplaceCommand;
import ssonin.ccmemcached.protocol.command.SetCommand;
import ssonin.ccmemcached.protocol.command.TouchCommand;
import ssonin.ccmemcached.protocol.error.ClientError;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Long.compareUnsigned;
import static java.lang.Long.parseUnsignedLong;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static ssonin.ccmemcached.cache.CacheEntry.cacheEntry;
import static ssonin.ccmemcached.cache.ExpiryUpdate.PRESERVE;
import static ssonin.ccmemcached.cache.ExpiryUpdate.RESET;
import static ssonin.ccmemcached.cache.StoreResult.EXISTS;
import static ssonin.ccmemcached.cache.StoreResult.NOT_FOUND;
import static ssonin.ccmemcached.cache.StoreResult.NOT_STORED;
import static ssonin.ccmemcached.cache.StoreResult.STORED;

public final class CacheService {

  private static final Duration NEVER_EXPIRES = Duration.ofDays(365L * 100);
  private static final long MAX_RELATIVE_EXPTIME = 2_592_000L;
  private static final int MAX_VALUE_BYTES = 1024 * 1024;

  private final Cache<String, CacheEntry> delegate;
  private final InstantSource clock;
  private final AtomicLong casSequence = new AtomicLong();

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

  public boolean touch(TouchCommand command) {
    return delegate.asMap().computeIfPresent(command.key(), (_, existing) ->
      cacheEntry()
        .flags(existing.flags())
        .ttl(evaluateTtl(command.expTime()))
        .data(existing.data())
        .casUnique(existing.casUnique())
        .expiryUpdate(RESET)
        .build()) != null;
  }

  public OptionalLong increment(IncrCommand command) {
    return updateNumericValue(command);
  }

  public OptionalLong decrement(DecrCommand command) {
    return updateNumericValue(command);
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

  public boolean replace(ReplaceCommand command, byte[] data) {
    return delegate.asMap().computeIfPresent(command.key(), (_, _) -> toCacheEntry(command, data)) != null;
  }

  public StoreResult cas(CasCommand command, byte[] data) {
    final var entries = delegate.asMap();
    while (true) {
      final var existing = entries.get(command.key());
      if (existing == null) {
        return NOT_FOUND;
      }
      if (existing.casUnique() != command.casUnique()) {
        return EXISTS;
      }
      if (entries.replace(command.key(), existing, toCacheEntry(command, data))) {
        return STORED;
      }
    }
  }

  public StoreResult append(AppendCommand command, byte[] data) {
    final var entries = delegate.asMap();
    while (true) {
      final var existing = entries.get(command.key());
      if (existing == null || data.length > MAX_VALUE_BYTES - existing.data().length) {
        return NOT_STORED;
      }
      final var updated = cacheEntry()
        .flags(existing.flags())
        .ttl(existing.ttl())
        .data(concat(existing.data(), data))
        .casUnique(nextCasUnique())
        .expiryUpdate(PRESERVE)
        .build();
      if (entries.replace(command.key(), existing, updated)) {
        return STORED;
      }
    }
  }

  private byte[] concat(byte[] first, byte[] second) {
    final var result = Arrays.copyOf(first, first.length + second.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }

  private OptionalLong updateNumericValue(NumericCommand command) {
    final var updatedValue = new AtomicLong();
    final var updated = delegate.asMap().computeIfPresent(command.key(), (_, existing) -> {
      final var current = parseNumericValue(existing.data());
      final var next = switch (command) {
        case IncrCommand _ -> current + command.delta();
        case DecrCommand _ -> compareUnsigned(current, command.delta()) <= 0L
          ? 0L
          : current - command.delta();
      };
      updatedValue.set(next);
      return cacheEntry()
        .flags(existing.flags())
        .ttl(existing.ttl())
        .data(Long.toUnsignedString(next).getBytes(US_ASCII))
        .casUnique(nextCasUnique())
        .expiryUpdate(PRESERVE)
        .build();
    }) != null;
    return updated ? OptionalLong.of(updatedValue.get()) : OptionalLong.empty();
  }

  private long nextCasUnique() {
    return casSequence.incrementAndGet();
  }

  private long parseNumericValue(byte[] data) {
    final var raw = new String(data, US_ASCII);
    try {
      return parseUnsignedLong(raw);
    } catch (NumberFormatException e) {
      throw new ClientError("value is not a valid unsigned integer");
    }
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

  private CacheEntry toCacheEntry(MetadataStorageCommand command, byte[] data) {
    return cacheEntry()
      .flags(command.flags())
      .ttl(evaluateTtl(command.expTime()))
      .data(data)
      .casUnique(nextCasUnique())
      .expiryUpdate(RESET)
      .build();
  }
}
