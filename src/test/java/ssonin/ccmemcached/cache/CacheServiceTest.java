package ssonin.ccmemcached.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssonin.ccmemcached.protocol.command.DecrCommand;
import ssonin.ccmemcached.protocol.command.IncrCommand;
import ssonin.ccmemcached.protocol.command.TouchCommand;

import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.time.Duration.ofDays;
import static java.time.Duration.ofSeconds;
import static java.time.Instant.parse;
import static java.time.InstantSource.fixed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static ssonin.ccmemcached.cache.CacheEntry.cacheEntry;
import static ssonin.ccmemcached.cache.ExpiryUpdate.PRESERVE;
import static ssonin.ccmemcached.cache.ExpiryUpdate.RESET;
import static ssonin.ccmemcached.cache.StoreResult.EXISTS;
import static ssonin.ccmemcached.cache.StoreResult.NOT_FOUND;
import static ssonin.ccmemcached.cache.StoreResult.STORED;
import static ssonin.ccmemcached.protocol.command.AddCommand.Builder.addCommand;
import static ssonin.ccmemcached.protocol.command.CasCommand.Builder.casCommand;
import static ssonin.ccmemcached.protocol.command.ReplaceCommand.Builder.replaceCommand;
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
      then(delegate).should().put("mykey", cacheEntry()
        .flags(42)
        .ttl(ofDays(365L * 100))
        .data(data)
        .casUnique(1L)
        .expiryUpdate(RESET)
        .build());
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
      then(delegate).should().put("mykey", cacheEntry()
        .flags(7)
        .ttl(ofSeconds(900))
        .data(data)
        .casUnique(1L)
        .expiryUpdate(RESET)
        .build());
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
      then(delegate).should().put("mykey", cacheEntry()
        .flags(9)
        .ttl(ofSeconds(3600))
        .data(data)
        .casUnique(1L)
        .expiryUpdate(RESET)
        .build());
    }
  }

  @Nested
  class AddOperation {

    @Test
    void stores_entry_when_key_is_absent() {
      // given
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      given(delegate.asMap()).willReturn(entries);
      var command = addCommand()
        .key("mykey")
        .flags(7)
        .expTime(900)
        .bytes(5)
        .build();
      var data = "value".getBytes();

      // when
      var stored = tested.add(command, data);

      // then
      assertThat(stored).isTrue();
      assertThat(entries).containsEntry("mykey", cacheEntry()
        .flags(7)
        .ttl(ofSeconds(900))
        .data(data)
        .casUnique(1L)
        .expiryUpdate(RESET)
        .build());
    }

    @Test
    void returns_false_and_preserves_existing_entry_when_key_exists() {
      // given
      var existing = cacheEntry()
        .flags(1)
        .ttl(ofSeconds(30))
        .data("first".getBytes())
        .casUnique(41L)
        .build();
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      entries.put("mykey", existing);
      given(delegate.asMap()).willReturn(entries);
      var command = addCommand()
        .key("mykey")
        .flags(7)
        .expTime(900)
        .bytes(6)
        .build();

      // when
      var stored = tested.add(command, "second".getBytes());

      // then
      assertThat(stored).isFalse();
      assertThat(entries).containsEntry("mykey", existing);
    }
  }

  @Nested
  class ReplaceOperation {

    @Test
    void stores_entry_when_key_is_present() {
      // given
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      entries.put("mykey", cacheEntry()
        .flags(1)
        .ttl(ofSeconds(30))
        .data("first".getBytes())
        .build());
      given(delegate.asMap()).willReturn(entries);
      var command = replaceCommand()
        .key("mykey")
        .flags(7)
        .expTime(900)
        .bytes(6)
        .build();
      var data = "second".getBytes();

      // when
      var stored = tested.replace(command, data);

      // then
      assertThat(stored).isTrue();
      assertThat(entries).containsEntry("mykey", cacheEntry()
        .flags(7)
        .ttl(ofSeconds(900))
        .data(data)
        .casUnique(1L)
        .expiryUpdate(RESET)
        .build());
    }

    @Test
    void returns_false_when_key_is_absent() {
      // given
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      given(delegate.asMap()).willReturn(entries);
      var command = replaceCommand()
        .key("mykey")
        .flags(7)
        .expTime(900)
        .bytes(6)
        .build();

      // when
      var stored = tested.replace(command, "second".getBytes());

      // then
      assertThat(stored).isFalse();
      assertThat(entries).doesNotContainKey("mykey");
    }
  }

  @Nested
  class CasOperation {

    @Test
    void stores_entry_when_cas_unique_matches() {
      // given
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      entries.put("mykey", cacheEntry()
        .flags(1)
        .ttl(ofSeconds(30))
        .data("first".getBytes())
        .casUnique(41L)
        .build());
      given(delegate.asMap()).willReturn(entries);
      var command = casCommand()
        .key("mykey")
        .flags(7)
        .expTime(900)
        .bytes(6)
        .casUnique(41L)
        .build();
      var data = "second".getBytes();

      // when
      var result = tested.cas(command, data);

      // then
      assertThat(result).isEqualTo(STORED);
      assertThat(entries).containsEntry("mykey", cacheEntry()
        .flags(7)
        .ttl(ofSeconds(900))
        .data(data)
        .casUnique(1L)
        .expiryUpdate(RESET)
        .build());
    }

    @Test
    void returns_exists_and_preserves_entry_when_cas_unique_does_not_match() {
      // given
      var existing = cacheEntry()
        .flags(1)
        .ttl(ofSeconds(30))
        .data("first".getBytes())
        .casUnique(41L)
        .expiryUpdate(RESET)
        .build();
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      entries.put("mykey", existing);
      given(delegate.asMap()).willReturn(entries);
      var command = casCommand()
        .key("mykey")
        .flags(7)
        .expTime(900)
        .bytes(6)
        .casUnique(42L)
        .build();

      // when
      var result = tested.cas(command, "second".getBytes());

      // then
      assertThat(result).isEqualTo(EXISTS);
      assertThat(entries).containsEntry("mykey", existing);
    }

    @Test
    void returns_not_found_when_key_is_absent() {
      // given
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      given(delegate.asMap()).willReturn(entries);
      var command = casCommand()
        .key("missing")
        .flags(7)
        .expTime(900)
        .bytes(6)
        .casUnique(42L)
        .build();

      // when
      var result = tested.cas(command, "second".getBytes());

      // then
      assertThat(result).isEqualTo(NOT_FOUND);
      assertThat(entries).doesNotContainKey("missing");
    }
  }

  @Nested
  class GetOperation {

    @Test
    void returns_entries_present_for_requested_keys() {
      // given
      var keys = List.of("first", "second");
      var entries = Map.of("first", cacheEntry()
        .flags(7)
        .ttl(ofSeconds(30))
        .data("value".getBytes())
        .build());
      given(delegate.getAllPresent(keys)).willReturn(entries);

      // when
      var result = tested.getAllPresent(keys);

      // then
      assertThat(result).isEqualTo(entries);
    }
  }

  @Nested
  class DeleteOperation {

    @Test
    void returns_true_and_removes_entry_when_key_exists() {
      // given
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      entries.put("mykey", cacheEntry()
        .flags(7)
        .ttl(ofSeconds(30))
        .data("value".getBytes())
        .build());
      given(delegate.asMap()).willReturn(entries);

      // when
      var deleted = tested.delete("mykey");

      // then
      assertThat(deleted).isTrue();
      assertThat(entries).doesNotContainKey("mykey");
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
    }
  }

  @Nested
  class TouchOperation {

    @Test
    void updates_ttl_and_preserves_flags_and_data_when_key_exists() {
      // given
      var existingData = "value".getBytes();
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      entries.put("mykey", cacheEntry()
        .flags(7)
        .ttl(ofSeconds(30))
        .data(existingData)
        .casUnique(41L)
        .build());
      given(delegate.asMap()).willReturn(entries);
      var command = new TouchCommand("mykey", 900, false);

      // when
      var touched = tested.touch(command);

      // then
      assertThat(touched).isTrue();
      assertThat(entries).containsEntry("mykey", cacheEntry()
        .flags(7)
        .ttl(ofSeconds(900))
        .data(existingData)
        .casUnique(41L)
        .expiryUpdate(RESET)
        .build());
    }

    @Test
    void returns_false_when_key_is_absent() {
      // given
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      given(delegate.asMap()).willReturn(entries);
      var command = new TouchCommand("missing", 900, false);

      // when
      var touched = tested.touch(command);

      // then
      assertThat(touched).isFalse();
      assertThat(entries).doesNotContainKey("missing");
    }
  }

  @Nested
  class IncrementTest {

    @Test
    void increments_numeric_value_and_preserves_flags_and_ttl() {
      // given
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      entries.put("mykey", cacheEntry()
        .flags(7)
        .ttl(ofSeconds(30))
        .data("41".getBytes())
        .build());
      given(delegate.asMap()).willReturn(entries);

      // when
      var result = tested.increment(new IncrCommand("mykey", 1L, false));
      var updated = entries.get("mykey");

      // then
      assertThat(result).hasValue(42L);
      assertThat(updated.flags()).isEqualTo(7);
      assertThat(updated.ttl()).isEqualTo(ofSeconds(30));
      assertThat(updated.data()).isEqualTo("42".getBytes());
      assertThat(updated.casUnique()).isEqualTo(1L);
      assertThat(updated.expiryUpdate()).isEqualTo(PRESERVE);
    }

    @Test
    void wraps_unsigned_overflow_to_zero() {
      // given
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      entries.put("mykey", cacheEntry()
        .flags(7)
        .ttl(ofSeconds(30))
        .data("18446744073709551615".getBytes())
        .build());
      given(delegate.asMap()).willReturn(entries);

      // when
      var result = tested.increment(new IncrCommand("mykey", 1L, false));
      var updated = entries.get("mykey");

      // then
      assertThat(result).hasValue(0L);
      assertThat(updated.flags()).isEqualTo(7);
      assertThat(updated.ttl()).isEqualTo(ofSeconds(30));
      assertThat(updated.data()).isEqualTo("0".getBytes());
      assertThat(updated.casUnique()).isEqualTo(1L);
      assertThat(updated.expiryUpdate()).isEqualTo(PRESERVE);
    }

    @Test
    void returns_empty_when_key_is_missing() {
      // given
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      given(delegate.asMap()).willReturn(entries);

      // when
      var result = tested.increment(new IncrCommand("missing", 1L, false));

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void throws_when_stored_value_is_not_numeric() {
      // given
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      entries.put("mykey", cacheEntry()
        .flags(7)
        .ttl(ofSeconds(30))
        .data("value".getBytes())
        .build());
      given(delegate.asMap()).willReturn(entries);

      // when
      var thrown = catchThrowable(() -> tested.increment(new IncrCommand("mykey", 1L, false)));

      // then
      assertThat(thrown).hasMessage("CLIENT_ERROR: value is not a valid unsigned integer");
    }
  }

  @Nested
  class DecrementTest {

    @Test
    void decrements_numeric_value_and_preserves_flags_and_ttl() {
      // given
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      entries.put("mykey", cacheEntry()
        .flags(7)
        .ttl(ofSeconds(30))
        .data("42".getBytes())
        .build());
      given(delegate.asMap()).willReturn(entries);

      // when
      var result = tested.decrement(new DecrCommand("mykey", 1L, false));
      var updated = entries.get("mykey");

      // then
      assertThat(result).hasValue(41L);
      assertThat(updated.flags()).isEqualTo(7);
      assertThat(updated.ttl()).isEqualTo(ofSeconds(30));
      assertThat(updated.data()).isEqualTo("41".getBytes());
      assertThat(updated.casUnique()).isEqualTo(1L);
      assertThat(updated.expiryUpdate()).isEqualTo(PRESERVE);
    }

    @Test
    void decrements_unsigned_value_above_signed_long_range() {
      // given
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      entries.put("mykey", cacheEntry()
        .flags(7)
        .ttl(ofSeconds(30))
        .data("18446744073709551615".getBytes())
        .build());
      given(delegate.asMap()).willReturn(entries);

      // when
      var result = tested.decrement(new DecrCommand("mykey", 1L, false));
      var updated = entries.get("mykey");

      // then
      assertThat(result).isPresent();
      assertThat(Long.toUnsignedString(result.getAsLong())).isEqualTo("18446744073709551614");
      assertThat(updated.flags()).isEqualTo(7);
      assertThat(updated.ttl()).isEqualTo(ofSeconds(30));
      assertThat(updated.data()).isEqualTo("18446744073709551614".getBytes());
      assertThat(updated.casUnique()).isEqualTo(1L);
      assertThat(updated.expiryUpdate()).isEqualTo(PRESERVE);
    }

    @Test
    void clamps_decrement_result_to_zero() {
      // given
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      entries.put("mykey", cacheEntry()
        .flags(7)
        .ttl(ofSeconds(30))
        .data("2".getBytes())
        .build());
      given(delegate.asMap()).willReturn(entries);

      // when
      var result = tested.decrement(new DecrCommand("mykey", 5L, false));
      var updated = entries.get("mykey");

      // then
      assertThat(result).hasValue(0L);
      assertThat(updated.flags()).isEqualTo(7);
      assertThat(updated.ttl()).isEqualTo(ofSeconds(30));
      assertThat(updated.data()).isEqualTo("0".getBytes());
      assertThat(updated.casUnique()).isEqualTo(1L);
      assertThat(updated.expiryUpdate()).isEqualTo(PRESERVE);
    }

    @Test
    void returns_empty_when_key_is_missing() {
      // given
      var entries = new ConcurrentHashMap<String, CacheEntry>();
      given(delegate.asMap()).willReturn(entries);

      // when
      var result = tested.decrement(new DecrCommand("missing", 1L, false));

      // then
      assertThat(result).isEmpty();
    }
  }
}
