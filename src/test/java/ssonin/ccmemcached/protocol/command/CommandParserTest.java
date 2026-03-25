package ssonin.ccmemcached.protocol.command;

import org.junit.jupiter.api.Test;
import ssonin.ccmemcached.protocol.error.ApplicationError;
import ssonin.ccmemcached.protocol.error.CommandNameError;

import static io.vertx.core.buffer.Buffer.buffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static ssonin.ccmemcached.protocol.command.Command.Builder.command;
import static ssonin.ccmemcached.protocol.command.CommandName.SET;
import static ssonin.ccmemcached.protocol.command.CommandParser.parseCommand;

class CommandParserTest {

  @Test
  void parses_storage_command_without_noreply() {
    // given
    var buffer = buffer("set mykey 0 900 5");

    // when
    var command = parseCommand(buffer);

    // then
    assertThat(command).isEqualTo(command()
      .name(SET)
      .key("mykey")
      .flags(0)
      .expTime(900)
      .bytes(5)
      .build());
  }

  @Test
  void parses_storage_command_with_noreply() {
    // given
    var buffer = buffer("set mykey 0 900 5 noreply");

    // when
    var command = parseCommand(buffer);

    // then
    assertThat(command).isEqualTo(command()
      .name(SET)
      .key("mykey")
      .flags(0)
      .expTime(900)
      .bytes(5)
      .noReply(true)
      .build());
  }

  @Test
  void parses_command_name_case_insensitively() {
    // when
    var fromUpper = parseCommand(buffer("SET mykey 0 900 5"));
    var fromMixed = parseCommand(buffer("Set mykey 0 900 5"));

    // then
    assertThat(fromUpper.name()).isEqualTo(SET);
    assertThat(fromMixed.name()).isEqualTo(SET);
  }

  @Test
  void accepts_flags_at_min() {
    // when
    var command = parseCommand(buffer("set mykey 0 900 5"));

    // then
    assertThat(command).isEqualTo(command().name(SET).key("mykey").flags(0).expTime(900).bytes(5).build());
  }

  @Test
  void accepts_flags_at_max() {
    // when
    var command = parseCommand(buffer("set mykey 65535 900 5"));

    // then
    assertThat(command).isEqualTo(command().name(SET).key("mykey").flags(65535).expTime(900).bytes(5).build());
  }

  @Test
  void accepts_zero_exptime() {
    // when
    var command = parseCommand(buffer("set mykey 0 0 5"));

    // then
    assertThat(command).isEqualTo(command().name(SET).key("mykey").flags(0).expTime(0).bytes(5).build());
  }

  @Test
  void accepts_negative_exptime() {
    // when
    var command = parseCommand(buffer("set mykey 0 -1 5"));

    // then
    assertThat(command).isEqualTo(command().name(SET).key("mykey").flags(0).expTime(-1).bytes(5).build());
  }

  @Test
  void accepts_zero_bytes() {
    // when
    var command = parseCommand(buffer("set mykey 0 900 0"));

    // then
    assertThat(command).isEqualTo(command().name(SET).key("mykey").flags(0).expTime(900).bytes(0).build());
  }

  @Test
  void throws_on_null_buffer() {
    // when
    var thrown = catchThrowable(() -> parseCommand(null));

    // then
    assertThat(thrown).isInstanceOf(NullPointerException.class);
  }

  @Test
  void throws_on_too_few_fields() {
    // when
    var thrown = catchThrowable(() -> parseCommand(buffer("set mykey 0 900")));

    // then
    assertThat(thrown).isInstanceOf(ApplicationError.class)
      .hasMessageStartingWith("CLIENT_ERROR");
  }

  @Test
  void throws_on_too_many_fields() {
    // when
    var thrown = catchThrowable(() -> parseCommand(buffer("set mykey 0 900 5 noreply extra")));

    // then
    assertThat(thrown).isInstanceOf(ApplicationError.class)
      .hasMessageStartingWith("CLIENT_ERROR");
  }

  @Test
  void throws_on_unknown_command_name() {
    // when
    var thrown = catchThrowable(() -> parseCommand(buffer("foo mykey 0 900 5")));

    // then
    assertThat(thrown).isInstanceOf(CommandNameError.class)
      .hasMessageStartingWith("ERROR");
  }

  @Test
  void throws_on_empty_key() {
    // when
    var thrown = catchThrowable(() -> parseCommand(buffer("set  0 900 5")));

    // then
    assertThat(thrown).isInstanceOf(ApplicationError.class)
      .hasMessageStartingWith("CLIENT_ERROR");
  }

  @Test
  void throws_on_key_exceeding_250_chars() {
    // given
    var key = "a".repeat(251);

    // when
    var thrown = catchThrowable(() -> parseCommand(buffer("set " + key + " 0 900 5")));

    // then
    assertThat(thrown).isInstanceOf(ApplicationError.class)
      .hasMessageStartingWith("CLIENT_ERROR");
  }

  @Test
  void throws_on_key_with_tab() {
    // when
    var thrown = catchThrowable(() -> parseCommand(buffer("set my\tkey 0 900 5")));

    // then
    assertThat(thrown).isInstanceOf(ApplicationError.class)
      .hasMessageStartingWith("CLIENT_ERROR");
  }

  @Test
  void throws_on_key_with_control_character() {
    // when
    var thrown = catchThrowable(() -> parseCommand(buffer("set my\u0001key 0 900 5")));

    // then
    assertThat(thrown).isInstanceOf(ApplicationError.class)
      .hasMessageStartingWith("CLIENT_ERROR");
  }

  @Test
  void throws_on_non_numeric_flags() {
    // when
    var thrown = catchThrowable(() -> parseCommand(buffer("set mykey abc 900 5")));

    // then
    assertThat(thrown).isInstanceOf(ApplicationError.class)
      .hasMessageStartingWith("CLIENT_ERROR");
  }

  @Test
  void throws_on_negative_flags() {
    // when
    var thrown = catchThrowable(() -> parseCommand(buffer("set mykey -1 900 5")));

    // then
    assertThat(thrown).isInstanceOf(ApplicationError.class)
      .hasMessageStartingWith("CLIENT_ERROR");
  }

  @Test
  void throws_on_flags_above_max() {
    // when
    var thrown = catchThrowable(() -> parseCommand(buffer("set mykey 65536 900 5")));

    // then
    assertThat(thrown).isInstanceOf(ApplicationError.class)
      .hasMessageStartingWith("CLIENT_ERROR");
  }

  @Test
  void throws_on_non_numeric_exptime() {
    // when
    var thrown = catchThrowable(() -> parseCommand(buffer("set mykey 0 abc 5")));

    // then
    assertThat(thrown).isInstanceOf(ApplicationError.class)
      .hasMessageStartingWith("CLIENT_ERROR");
  }

  @Test
  void throws_on_non_numeric_bytes() {
    // when
    var thrown = catchThrowable(() -> parseCommand(buffer("set mykey 0 900 abc")));

    // then
    assertThat(thrown).isInstanceOf(ApplicationError.class)
      .hasMessageStartingWith("CLIENT_ERROR");
  }

  @Test
  void throws_on_negative_bytes() {
    // when
    var thrown = catchThrowable(() -> parseCommand(buffer("set mykey 0 900 -1")));

    // then
    assertThat(thrown).isInstanceOf(ApplicationError.class)
      .hasMessageStartingWith("CLIENT_ERROR");
  }

  @Test
  void throws_on_invalid_noreply_value() {
    // when
    var thrown = catchThrowable(() -> parseCommand(buffer("set mykey 0 900 5 NOREPLY")));

    // then
    assertThat(thrown).isInstanceOf(ApplicationError.class)
      .hasMessageStartingWith("CLIENT_ERROR");
  }
}
