package ssonin.ccmemcached.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.time.Duration.ofDays;
import static java.time.Duration.ofSeconds;
import static java.time.Instant.parse;
import static java.time.InstantSource.fixed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static ssonin.ccmemcached.protocol.command.AddCommand.Builder.addCommand;
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

  @Nested
  class AddOperation {

    @Test
    void stores_entry_when_key_is_absent() {
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      given(delegate.asMap()).willReturn(entries);
      var command = addCommand()
        .key("mykey")
        .flags(7)
        .expTime(900)
        .bytes(5)
        .build();
      var data = "value".getBytes();

      var stored = tested.add(command, data);

      assertThat(stored).isTrue();
      assertThat(entries).containsEntry("mykey", new CacheEntry(7, ofSeconds(900), data));
      then(delegate).should().asMap();
    }

    @Test
    void returns_false_and_preserves_existing_entry_when_key_exists() {
      var existing = new CacheEntry(1, ofSeconds(30), "first".getBytes());
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      entries.put("mykey", existing);
      given(delegate.asMap()).willReturn(entries);
      var command = addCommand()
        .key("mykey")
        .flags(7)
        .expTime(900)
        .bytes(6)
        .build();

      var stored = tested.add(command, "second".getBytes());

      assertThat(stored).isFalse();
      assertThat(entries).containsEntry("mykey", existing);
      then(delegate).should().asMap();
    }
  }

  @Nested
  class GetOperation {

    @Test
    void returns_entries_present_for_requested_keys() {
      // given
      var keys = List.of("first", "second");
      var entries = Map.of("first", new CacheEntry(7, ofSeconds(30), "value".getBytes()));
      given(delegate.getAllPresent(keys)).willReturn(entries);

      // when
      var result = tested.getAllPresent(keys);

      // then
      assertThat(result).isEqualTo(entries);
      then(delegate).should().getAllPresent(keys);
    }
  }

  @Nested
  class DeleteOperation {

    @Test
    void returns_true_and_removes_entry_when_key_exists() {
      // given
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      entries.put("mykey", new CacheEntry(7, ofSeconds(30), "value".getBytes()));
      given(delegate.asMap()).willReturn(entries);

      // when
      var deleted = tested.delete("mykey");

      // then
      assertThat(deleted).isTrue();
      assertThat(entries).doesNotContainKey("mykey");
      then(delegate).should().asMap();
    }

    @Test
    void returns_false_when_key_does_not_exist() {
      // given
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      given(delegate.asMap()).willReturn(entries);

      // when
      var deleted = tested.delete("missing");

      // then
      assertThat(deleted).isFalse();
      then(delegate).should().asMap();
    }
  }
}
