package ssonin.ccmemcached.cache;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static ssonin.ccmemcached.cache.CacheEntry.cacheEntry;
import static ssonin.ccmemcached.cache.ExpiryUpdate.RESET;

class CacheEntryExpiryTest {

  private final CacheEntryExpiry tested = new CacheEntryExpiry();

  @Nested
  class ExpireAfterCreateTest {

    @Test
    void returns_entry_ttl() {
      // given
      var entry = cacheEntry()
        .flags(7)
        .ttl(ofSeconds(30))
        .data("value".getBytes())
        .build();

      // when
      var result = tested.expireAfterCreate("mykey", entry, 1L);

      // then
      assertThat(result).isEqualTo(ofSeconds(30).toNanos());
    }
  }

  @Nested
  class ExpireAfterUpdateTest {

    @Test
    void resets_duration_when_entry_requests_reset() {
      // given
      var entry = cacheEntry()
        .flags(7)
        .ttl(ofSeconds(30))
        .data("value".getBytes())
        .expiryUpdate(RESET)
        .build();

      // when
      var result = tested.expireAfterUpdate("mykey", entry, 1L, ofSeconds(10).toNanos());

      // then
      assertThat(result).isEqualTo(ofSeconds(30).toNanos());
    }

    @Test
    void preserves_current_duration_when_entry_does_not_request_reset() {
      // given
      var entry = cacheEntry()
        .flags(7)
        .ttl(ofSeconds(30))
        .data("value".getBytes())
        .build();

      // when
      var result = tested.expireAfterUpdate("mykey", entry, 1L, ofSeconds(10).toNanos());

      // then
      assertThat(result).isEqualTo(ofSeconds(10).toNanos());
    }
  }

  @Nested
  class ExpireAfterReadTest {

    @Test
    void preserves_current_duration() {
      // given
      var entry = cacheEntry()
        .flags(7)
        .ttl(ofSeconds(30))
        .data("value".getBytes())
        .build();

      // when
      var result = tested.expireAfterRead("mykey", entry, 1L, ofSeconds(10).toNanos());

      // then
      assertThat(result).isEqualTo(ofSeconds(10).toNanos());
    }
  }
}
