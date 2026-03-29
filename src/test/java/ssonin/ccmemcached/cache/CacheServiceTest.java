package ssonin.ccmemcached.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.InstantSource;

import static java.time.Duration.ofDays;
import static java.time.Duration.ofSeconds;
import static java.time.Instant.parse;
import static java.time.InstantSource.fixed;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static ssonin.ccmemcached.protocol.command.SetCommand.Builder.setCommand;

class CacheServiceTest {

  @SuppressWarnings("unchecked")
  private final Cache<String, CacheEntry> delegate = mock(Cache.class);
  private final InstantSource clock = fixed(parse("2024-01-01T00:00:00Z"));

  private final CacheService tested = new CacheService(delegate, clock);

  @Nested
  class SetOperation {

    @Test
    void stores_entry_without_expiry_when_exptime_is_zero() {
      // given
      var command = setCommand()
        .key("mykey")
        .flags(42)
        .expTime(0)
        .bytes(5)
        .build();
      var data = "value".getBytes();

      // when
      tested.put(command, data);

      // then
      then(delegate).should().put("mykey", new CacheEntry(42, ofDays(365L * 100), data));
    }

    @Test
    void stores_entry_with_relative_ttl_when_exptime_is_within_relative_range() {
      // given
      var command = setCommand()
        .key("mykey")
        .flags(7)
        .expTime(900)
        .bytes(5)
        .build();
      var data = "value".getBytes();

      // when
      tested.put(command, data);

      // then
      then(delegate).should().put("mykey", new CacheEntry(7, ofSeconds(900), data));
    }

    @Test
    void stores_entry_with_absolute_ttl_when_exptime_is_unix_timestamp() {
      // given
      var now = parse("2024-01-01T00:00:00Z");
      var command = setCommand()
        .key("mykey")
        .flags(9)
        .expTime((int) now.plusSeconds(3600).getEpochSecond())
        .bytes(5)
        .build();
      var data = "value".getBytes();

      // when
      tested.put(command, data);

      // then
      then(delegate).should().put("mykey", new CacheEntry(9, ofSeconds(3600), data));
    }
  }
}
