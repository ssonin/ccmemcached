package ssonin.ccmemcached.protocol.command.parser;

import org.junit.jupiter.api.Test;
import ssonin.ccmemcached.protocol.command.DecrCommand;
import ssonin.ccmemcached.protocol.error.ClientError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static ssonin.ccmemcached.protocol.command.parser.DecrCommandParser.parse;

class DecrCommandParserTest {

  @Test
  void parses_decr_command_without_noreply() {
    // given
    var parts = new String[]{"decr", "mykey", "42"};

    // when
    var command = parse(parts);

    // then
    assertThat(command).isEqualTo(new DecrCommand("mykey", 42L, false));
  }

  @Test
  void parses_decr_command_with_noreply() {
    // given
    var parts = new String[]{"decr", "mykey", "42", "noreply"};

    // when
    var command = parse(parts);

    // then
    assertThat(command).isEqualTo(new DecrCommand("mykey", 42L, true));
  }

  @Test
  void throws_on_missing_delta() {
    // given
    var parts = new String[]{"decr", "mykey"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: expected at least 3 fields, got 2");
  }

  @Test
  void throws_on_too_many_fields() {
    // given
    var parts = new String[]{"decr", "mykey", "42", "noreply", "extra"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: expected at most 4 fields, got 5");
  }

  @Test
  void throws_on_invalid_delta() {
    // given
    var parts = new String[]{"decr", "mykey", "invalid"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: delta must be a valid 64-bit integer, got 'invalid'");
  }

  @Test
  void throws_on_negative_delta() {
    // given
    var parts = new String[]{"decr", "mykey", "-1"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: delta must be a valid 64-bit integer, got '-1'");
  }

  @Test
  void throws_on_invalid_noreply_token() {
    // given
    var parts = new String[]{"decr", "mykey", "42", "NOREPLY"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: expected 'noreply', got 'NOREPLY'");
  }
}
