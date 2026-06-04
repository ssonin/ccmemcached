package ssonin.ccmemcached.protocol.command.parser;

import org.junit.jupiter.api.Test;
import ssonin.ccmemcached.protocol.command.GetsCommand;
import ssonin.ccmemcached.protocol.error.ClientError;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static ssonin.ccmemcached.protocol.command.parser.GetsCommandParser.parse;

class GetsCommandParserTest {

  @Test
  void parses_gets_command_with_multiple_keys() {
    // given
    var parts = new String[]{"gets", "first", "second"};

    // when
    var command = parse(parts);

    // then
    assertThat(command).isEqualTo(new GetsCommand(List.of("first", "second")));
  }

  @Test
  void throws_on_missing_keys() {
    // given
    var parts = new String[]{"gets"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("expected at least 2 fields, got 1");
  }

  @Test
  void throws_on_invalid_key() {
    // given
    var parts = new String[]{"gets", "my\u0001key"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("key contains invalid character 0x1");
  }
}
