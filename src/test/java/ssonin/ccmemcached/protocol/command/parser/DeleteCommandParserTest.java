package ssonin.ccmemcached.protocol.command.parser;

import org.junit.jupiter.api.Test;
import ssonin.ccmemcached.protocol.command.DeleteCommand;
import ssonin.ccmemcached.protocol.error.ClientError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static ssonin.ccmemcached.protocol.command.parser.DeleteCommandParser.parse;

class DeleteCommandParserTest {

  @Test
  void parses_delete_command_without_noreply() {
    // given
    var parts = new String[]{"delete", "mykey"};

    // when
    var command = parse(parts);

    // then
    assertThat(command).isEqualTo(new DeleteCommand("mykey", false));
  }

  @Test
  void parses_delete_command_with_noreply() {
    // given
    var parts = new String[]{"delete", "mykey", "noreply"};

    // when
    var command = parse(parts);

    // then
    assertThat(command).isEqualTo(new DeleteCommand("mykey", true));
  }

  @Test
  void throws_on_missing_key() {
    // given
    var parts = new String[]{"delete"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("expected at least 2 fields, got 1");
  }

  @Test
  void throws_on_too_many_fields() {
    // given
    var parts = new String[]{"delete", "mykey", "noreply", "extra"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("expected at most 3 fields, got 4");
  }

  @Test
  void throws_on_invalid_noreply_token() {
    // given
    var parts = new String[]{"delete", "mykey", "NOREPLY"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("expected 'noreply', got 'NOREPLY'");
  }

  @Test
  void throws_on_invalid_key() {
    // given
    var parts = new String[]{"delete", "bad\u007fkey"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("key contains invalid character 0x7f");
  }
}
