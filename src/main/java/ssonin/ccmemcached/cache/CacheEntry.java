package ssonin.ccmemcached.cache;

import java.time.Duration;

public record CacheEntry(
  int flags,
  Duration ttl,
  byte[] data
) {

  static Builder cacheEntry() {
    return new Builder();
  }

  static class Builder {

    private int flags;
    private Duration ttl;
    private byte[] data;

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

    public CacheEntry build() {
      return new CacheEntry(flags, ttl, data);
    }
  }
}
