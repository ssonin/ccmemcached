package ssonin.ccmemcached.protocol.command.parser;

import org.junit.jupiter.api.Test;
import ssonin.ccmemcached.protocol.error.ClientError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static ssonin.ccmemcached.protocol.command.AddCommand.Builder.addCommand;
import static ssonin.ccmemcached.protocol.command.parser.AddCommandParser.parse;

class AddCommandParserTest {

  private static final int MAX_VALUE_BYTES = 1024 * 1024;

  @Test
  void parses_add_command_without_noreply() {
    var parts = new String[]{"add", "mykey", "0", "900", "5"};

    var command = parse(parts);

    assertThat(command).isEqualTo(addCommand()
      .key("mykey")
      .flags(0)
      .expTime(900)
      .bytes(5)
      .build());
  }

  @Test
  void parses_add_command_with_noreply() {
    var parts = new String[]{"add", "mykey", "0", "900", "5", "noreply"};

    var command = parse(parts);

    assertThat(command).isEqualTo(addCommand()
      .key("mykey")
      .flags(0)
      .expTime(900)
      .bytes(5)
      .noReply(true)
      .build());
  }

  @Test
  void throws_on_too_few_fields() {
    var parts = new String[]{"add", "mykey", "0", "900"};

    var thrown = catchThrowable(() -> parse(parts));

    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: expected at least 5 fields, got 4");
  }

  @Test
  void throws_on_too_many_fields() {
    var parts = new String[]{"add", "mykey", "0", "900", "5", "noreply", "extra"};

    var thrown = catchThrowable(() -> parse(parts));

    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: expected at most 6 fields, got 7");
  }

  @Test
  void throws_on_non_numeric_flags() {
    var parts = new String[]{"add", "mykey", "abc", "900", "5"};

    var thrown = catchThrowable(() -> parse(parts));

    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: flags must be a valid integer, got 'abc'");
  }

  @Test
  void throws_on_negative_flags() {
    var parts = new String[]{"add", "mykey", "-1", "900", "5"};

    var thrown = catchThrowable(() -> parse(parts));

    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: flags must be between 0 and 65535, got -1");
  }

  @Test
  void throws_on_flags_above_max() {
    var parts = new String[]{"add", "mykey", "65536", "900", "5"};

    var thrown = catchThrowable(() -> parse(parts));

    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: flags must be between 0 and 65535, got 65536");
  }

  @Test
  void throws_on_non_numeric_exptime() {
    var parts = new String[]{"add", "mykey", "0", "abc", "5"};

    var thrown = catchThrowable(() -> parse(parts));

    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: exptime must be a valid integer, got 'abc'");
  }

  @Test
  void throws_on_non_numeric_bytes() {
    var parts = new String[]{"add", "mykey", "0", "900", "abc"};

    var thrown = catchThrowable(() -> parse(parts));

    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: bytes must be a valid integer, got 'abc'");
  }

  @Test
  void throws_on_negative_bytes() {
    var parts = new String[]{"add", "mykey", "0", "900", "-1"};

    var thrown = catchThrowable(() -> parse(parts));

    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: bytes must be >= 0, got -1");
  }

  @Test
  void parses_bytes_at_maximum_value_size() {
    var parts = new String[]{"add", "mykey", "0", "900", Integer.toString(MAX_VALUE_BYTES)};

    var command = parse(parts);

    assertThat(command).isEqualTo(addCommand()
      .key("mykey")
      .flags(0)
      .expTime(900)
      .bytes(MAX_VALUE_BYTES)
      .build());
  }

  @Test
  void throws_on_bytes_above_maximum_value_size() {
    var parts = new String[]{"add", "mykey", "0", "900", Integer.toString(MAX_VALUE_BYTES + 1)};

    var thrown = catchThrowable(() -> parse(parts));

    assertThat(thrown).isInstanceOf(ClientError.class)
      .hasMessage("CLIENT_ERROR: bytes exceeds maximum size of 1048576");
  }
}
