package ssonin.ccmemcached.cache;

import java.time.Duration;

import static ssonin.ccmemcached.cache.ExpiryUpdate.PRESERVE;
import static ssonin.ccmemcached.cache.ExpiryUpdate.RESET;

public record CacheEntry(
  int flags,
  Duration ttl,
  byte[] data,
  ExpiryUpdate expiryUpdate
) {

  public boolean resetExpiryOnUpdate() {
    return expiryUpdate == RESET;
  }

  public static Builder cacheEntry() {
    return new Builder();
  }

  public static class Builder {

    private int flags;
    private Duration ttl;
    private byte[] data;
    private ExpiryUpdate expiryUpdate = PRESERVE;

    private Builder() {}

    public Builder flags(int flags) {
      this.flags = flags;
      return this;
    }

    public Builder ttl(Duration ttl) {
      this.ttl = ttl;
      return this;
    }

    public Builder data(byte[] data) {
      this.data = data;
      return this;
    }

    public Builder expiryUpdate(ExpiryUpdate expiryUpdate) {
      this.expiryUpdate = expiryUpdate;
      return this;
    }

    public CacheEntry build() {
      return new CacheEntry(flags, ttl, data, expiryUpdate);
    }
  }
}
