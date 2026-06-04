package ssonin.ccmemcached.protocol.command.parser;

import org.junit.jupiter.api.Test;
import ssonin.ccmemcached.protocol.command.TouchCommand;
import ssonin.ccmemcached.protocol.error.ClientError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static ssonin.ccmemcached.protocol.command.parser.TouchCommandParser.parse;

class TouchCommandParserTest {

  @Test
  void parses_touch_command_without_noreply() {
    // given
    var parts = new String[]{"touch", "mykey", "60"};

    // when
    var command = parse(parts);

    // then
    assertThat(command).isEqualTo(new TouchCommand("mykey", 60, false));
  }

  @Test
  void parses_touch_command_with_noreply() {
    // given
    var parts = new String[]{"touch", "mykey", "60", "noreply"};

    // when
    var command = parse(parts);

    // then
    assertThat(command).isEqualTo(new TouchCommand("mykey", 60, true));
  }

  @Test
  void throws_on_missing_exptime() {
    // given
    var parts = new String[]{"touch", "mykey"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("expected at least 3 fields, got 2");
  }

  @Test
  void throws_on_too_many_fields() {
    // given
    var parts = new String[]{"touch", "mykey", "60", "noreply", "extra"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("expected at most 4 fields, got 5");
  }

  @Test
  void throws_on_invalid_exptime() {
    // given
    var parts = new String[]{"touch", "mykey", "invalid"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("exptime must be a valid integer, got 'invalid'");
  }

  @Test
  void throws_on_invalid_noreply_token() {
    // given
    var parts = new String[]{"touch", "mykey", "60", "NOREPLY"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("expected 'noreply', got 'NOREPLY'");
  }
}
