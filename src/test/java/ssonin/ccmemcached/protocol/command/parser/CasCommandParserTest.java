package ssonin.ccmemcached.protocol.command.parser;

import org.junit.jupiter.api.Test;
import ssonin.ccmemcached.protocol.error.ClientError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static ssonin.ccmemcached.protocol.command.CasCommand.Builder.casCommand;
import static ssonin.ccmemcached.protocol.command.parser.CasCommandParser.parse;

class CasCommandParserTest {

  @Test
  void parses_cas_command_without_noreply() {
    // given
    var parts = new String[]{"cas", "mykey", "0", "900", "5", "42"};

    // when
    var command = parse(parts);

    // then
    assertThat(command).isEqualTo(casCommand()
      .key("mykey")
      .flags(0)
      .expTime(900)
      .bytes(5)
      .casUnique(42L)
      .build());
  }

  @Test
  void parses_cas_command_with_noreply() {
    // given
    var parts = new String[]{"cas", "mykey", "0", "900", "5", "42", "noreply"};

    // when
    var command = parse(parts);

    // then
    assertThat(command).isEqualTo(casCommand()
      .key("mykey")
      .flags(0)
      .expTime(900)
      .bytes(5)
      .casUnique(42L)
      .noReply(true)
      .build());
  }

  @Test
  void parses_unsigned_cas_unique_above_signed_long_range() {
    // given
    var parts = new String[]{"cas", "mykey", "0", "900", "5", "18446744073709551615"};

    // when
    var command = parse(parts);

    // then
    assertThat(command.casUnique()).isEqualTo(-1L);
  }

  @Test
  void throws_on_missing_cas_unique() {
    // given
    var parts = new String[]{"cas", "mykey", "0", "900", "5"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("expected at least 6 fields, got 5");
  }

  @Test
  void throws_on_invalid_cas_unique() {
    // given
    var parts = new String[]{"cas", "mykey", "0", "900", "5", "invalid"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("cas unique must be a valid 64-bit integer, got 'invalid'");
  }

  @Test
  void throws_on_invalid_noreply_token() {
    // given
    var parts = new String[]{"cas", "mykey", "0", "900", "5", "42", "NOREPLY"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("expected 'noreply', got 'NOREPLY'");
  }

  @Test
  void throws_on_too_many_fields() {
    // given
    var parts = new String[]{"cas", "mykey", "0", "900", "5", "42", "noreply", "extra"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("expected at most 7 fields, got 8");
  }
}
