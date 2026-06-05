package ssonin.ccmemcached.protocol;

import org.junit.jupiter.api.Test;
import ssonin.ccmemcached.protocol.error.ClientError;

import java.util.OptionalLong;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static ssonin.ccmemcached.cache.CacheEntry.cacheEntry;
import static ssonin.ccmemcached.cache.StoreResult.EXISTS;
import static ssonin.ccmemcached.cache.StoreResult.NOT_FOUND;
import static ssonin.ccmemcached.cache.StoreResult.NOT_STORED;
import static ssonin.ccmemcached.cache.StoreResult.STORED;
import static ssonin.ccmemcached.protocol.error.ErrorType.ERROR;
import static ssonin.ccmemcached.protocol.error.ErrorType.SERVER_ERROR;

class ResponseFormatterTest {

  private final ResponseFormatter tested = new ResponseFormatter();

  @Test
  void formats_storage_results() {
    // when
    var actualStored = tested.storage(STORED);
    var actualNotStored = tested.storage(NOT_STORED);
    var actualExists = tested.storage(EXISTS);
    var actualNotFound = tested.storage(NOT_FOUND);

    // then
    assertThat(actualStored).isEqualTo("STORED\r\n");
    assertThat(actualNotStored).isEqualTo("NOT_STORED\r\n");
    assertThat(actualExists).isEqualTo("EXISTS\r\n");
    assertThat(actualNotFound).isEqualTo("NOT_FOUND\r\n");
  }

  @Test
  void formats_delete_results() {
    // when
    var actualDeleted = tested.deleted(true);
    var actualNotFound = tested.deleted(false);

    // then
    assertThat(actualDeleted).isEqualTo("DELETED\r\n");
    assertThat(actualNotFound).isEqualTo("NOT_FOUND\r\n");
  }

  @Test
  void formats_touch_results() {
    // when
    var actualTouched = tested.touched(true);
    var actualNotFound = tested.touched(false);

    // then
    assertThat(actualTouched).isEqualTo("TOUCHED\r\n");
    assertThat(actualNotFound).isEqualTo("NOT_FOUND\r\n");
  }

  @Test
  void formats_numeric_results() {
    // when
    var actualPresent = tested.numeric(OptionalLong.of(-1L));
    var actualMissing = tested.numeric(OptionalLong.empty());

    // then
    assertThat(actualPresent).isEqualTo("18446744073709551615\r\n");
    assertThat(actualMissing).isEqualTo("NOT_FOUND\r\n");
  }

  @Test
  void formats_value_line_without_cas_unique() {
    // given
    var entry = cacheEntry()
      .flags(7)
      .ttl(ofSeconds(30))
      .data("value".getBytes())
      .casUnique(42L)
      .build();

    // when
    var actual = tested.valueLine("mykey", entry, false);

    // then
    assertThat(actual).isEqualTo("VALUE mykey 7 5\r\n");
  }

  @Test
  void formats_value_line_with_cas_unique_as_unsigned() {
    // given
    var entry = cacheEntry()
      .flags(7)
      .ttl(ofSeconds(30))
      .data("value".getBytes())
      .casUnique(-1L)
      .build();

    // when
    var actual = tested.valueLine("mykey", entry, true);

    // then
    assertThat(actual).isEqualTo("VALUE mykey 7 5 18446744073709551615\r\n");
  }

  @Test
  void formats_error_responses() {
    // when
    var actualClientError = tested.error(new ClientError("invalid input"));
    var actualError = tested.error(ERROR, "ignored");
    var actualServerError = tested.error(SERVER_ERROR, "internal server error");

    // then
    assertThat(actualClientError).isEqualTo("CLIENT_ERROR invalid input\r\n");
    assertThat(actualError).isEqualTo("ERROR\r\n");
    assertThat(actualServerError).isEqualTo("SERVER_ERROR internal server error\r\n");
  }

  @Test
  void formats_crlf_and_end() {
    // when
    var actualCrlf = tested.crlf();
    var actualEnd = tested.end();

    // then
    assertThat(actualCrlf).isEqualTo("\r\n");
    assertThat(actualEnd).isEqualTo("END\r\n");
  }
}
