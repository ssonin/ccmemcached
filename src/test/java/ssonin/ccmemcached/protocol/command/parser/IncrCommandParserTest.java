package ssonin.ccmemcached.protocol.command.parser;

import org.junit.jupiter.api.Test;
import ssonin.ccmemcached.protocol.command.IncrCommand;
import ssonin.ccmemcached.protocol.error.ClientError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static ssonin.ccmemcached.protocol.command.parser.IncrCommandParser.parse;

class IncrCommandParserTest {

  @Test
  void parses_incr_command_without_noreply() {
    // given
    var parts = new String[]{"incr", "mykey", "42"};

    // when
    var command = parse(parts);

    // then
    assertThat(command).isEqualTo(new IncrCommand("mykey", 42L, false));
  }

  @Test
  void parses_incr_command_with_noreply() {
    // given
    var parts = new String[]{"incr", "mykey", "42", "noreply"};

    // when
    var command = parse(parts);

    // then
    assertThat(command).isEqualTo(new IncrCommand("mykey", 42L, true));
  }

  @Test
  void throws_on_missing_delta() {
    // given
    var parts = new String[]{"incr", "mykey"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("expected at least 3 fields, got 2");
  }

  @Test
  void throws_on_too_many_fields() {
    // given
    var parts = new String[]{"incr", "mykey", "42", "noreply", "extra"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("expected at most 4 fields, got 5");
  }

  @Test
  void throws_on_invalid_delta() {
    // given
    var parts = new String[]{"incr", "mykey", "invalid"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("delta must be a valid 64-bit integer, got 'invalid'");
  }

  @Test
  void throws_on_negative_delta() {
    // given
    var parts = new String[]{"incr", "mykey", "-1"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("delta must be a valid 64-bit integer, got '-1'");
  }

  @Test
  void throws_on_invalid_noreply_token() {
    // given
    var parts = new String[]{"incr", "mykey", "42", "NOREPLY"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("expected 'noreply', got 'NOREPLY'");
  }
}
