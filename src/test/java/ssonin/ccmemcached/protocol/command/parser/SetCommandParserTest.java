package ssonin.ccmemcached.protocol.command.parser;

import org.junit.jupiter.api.Test;
import ssonin.ccmemcached.protocol.error.ClientError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static ssonin.ccmemcached.protocol.command.SetCommand.Builder.setCommand;
import static ssonin.ccmemcached.protocol.command.parser.SetCommandParser.parse;

class SetCommandParserTest {

  @Test
  void parses_set_command_without_noreply() {
    // given
    var parts = new String[]{"set", "mykey", "0", "900", "5"};

    // when
    var command = parse(parts);

    // then
    assertThat(command).isEqualTo(setCommand()
      .key("mykey")
      .flags(0)
      .expTime(900)
      .bytes(5)
      .build());
  }

  @Test
  void parses_set_command_with_noreply() {
    // given
    var parts = new String[]{"set", "mykey", "0", "900", "5", "noreply"};

    // when
    var command = parse(parts);

    // then
    assertThat(command).isEqualTo(setCommand()
      .key("mykey")
      .flags(0)
      .expTime(900)
      .bytes(5)
      .noReply(true)
      .build());
  }

  @Test
  void throws_on_too_few_fields() {
    // given
    var parts = new String[]{"set", "mykey", "0", "900"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: expected at least 5 fields, got 4");
  }

  @Test
  void throws_on_too_many_fields() {
    // given
    var parts = new String[]{"set", "mykey", "0", "900", "5", "noreply", "extra"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: expected at most 6 fields, got 7");
  }

  @Test
  void throws_on_non_numeric_flags() {
    // given
    var parts = new String[]{"set", "mykey", "abc", "900", "5"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: flags must be a valid integer, got 'abc'");
  }

  @Test
  void throws_on_negative_flags() {
    // given
    var parts = new String[]{"set", "mykey", "-1", "900", "5"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: flags must be between 0 and 65535, got -1");
  }

  @Test
  void throws_on_flags_above_max() {
    // given
    var parts = new String[]{"set", "mykey", "65536", "900", "5"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: flags must be between 0 and 65535, got 65536");
  }

  @Test
  void throws_on_non_numeric_exptime() {
    // given
    var parts = new String[]{"set", "mykey", "0", "abc", "5"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: exptime must be a valid integer, got 'abc'");
  }

  @Test
  void throws_on_non_numeric_bytes() {
    // given
    var parts = new String[]{"set", "mykey", "0", "900", "abc"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: bytes must be a valid integer, got 'abc'");
  }

  @Test
  void throws_on_negative_bytes() {
    // given
    var parts = new String[]{"set", "mykey", "0", "900", "-1"};

    // when
    var thrown = catchThrowable(() -> parse(parts));

    // then
    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: bytes must be >= 0, got -1");
  }
}
