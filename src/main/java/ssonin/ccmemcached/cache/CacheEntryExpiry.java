package ssonin.ccmemcached.cache;

import com.github.benmanes.caffeine.cache.Expiry;

public final class CacheEntryExpiry implements Expiry<String, CacheEntry> {

  @Override
  public long expireAfterCreate(String key, CacheEntry value, long currentTime) {
    return value.ttl().toNanos();
  }

  @Override
  public long expireAfterUpdate(String key, CacheEntry value, long currentTime, long currentDuration) {
    return value.resetExpiryOnUpdate()
      ? value.ttl().toNanos()
      : currentDuration;
  }

  @Override
  public long expireAfterRead(String key, CacheEntry value, long currentTime, long currentDuration) {
    return currentDuration;
  }
}
