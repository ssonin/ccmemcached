package ssonin.ccmemcached.protocol.command.parser;

import org.junit.jupiter.api.Test;
import ssonin.ccmemcached.protocol.command.GetCommand;
import ssonin.ccmemcached.protocol.error.ClientError;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static ssonin.ccmemcached.protocol.command.parser.GetCommandParser.parse;

class GetCommandParserTest {

  @Test
  void parses_get_command_with_multiple_keys() {
    // given
    var parts = new String[]{"get", "first", "second"};

    // when
    var command = parse(parts);

    // then
    assertThat(command).isEqualTo(new GetCommand(List.of("first", "second")));
  }

  @Test
  void throws_on_missing_keys() {
    // given
    var parts = new String[]{"get"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("expected at least 2 fields, got 1");
  }

  @Test
  void parses_get_command_with_maximum_key_count() {
    // given
    var parts = partsWithKeyCount(100);

    // when
    var command = parse(parts);

    // then
    assertThat(command.keys()).hasSize(100);
  }

  @Test
  void throws_on_key_count_above_maximum() {
    // given
    var parts = partsWithKeyCount(101);

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("key count exceeds maximum of 100, got 101");
  }

  private static String[] partsWithKeyCount(int keyCount) {
    return IntStream.rangeClosed(0, keyCount)
      .mapToObj(i -> i == 0 ? "get" : "key" + i)
      .toArray(String[]::new);
  }
}
