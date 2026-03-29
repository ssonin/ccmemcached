package ssonin.ccmemcached.protocol.command.parser;

import org.junit.jupiter.api.Test;
import ssonin.ccmemcached.protocol.command.GetCommand;
import ssonin.ccmemcached.protocol.error.ApplicationError;
import ssonin.ccmemcached.protocol.error.CommandNameError;

import java.util.List;

import static io.vertx.core.buffer.Buffer.buffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static ssonin.ccmemcached.protocol.command.CommandName.GET;
import static ssonin.ccmemcached.protocol.command.CommandName.SET;
import static ssonin.ccmemcached.protocol.command.SetCommand.Builder.setCommand;
import static ssonin.ccmemcached.protocol.command.parser.CommandParser.parseCommand;

class CommandParserTest {

  @Test
  void dispatches_to_set_command_parser() {
    // given
    var input = buffer("set mykey 0 900 5");

    // when
    var command = parseCommand(input);

    // then
    assertThat(command).isEqualTo(setCommand()
      .key("mykey")
      .flags(0)
      .expTime(900)
      .bytes(5)
      .build());
  }

  @Test
  void resolves_command_name_case_insensitively() {
    // when
    var fromUpper = parseCommand(buffer("SET mykey 0 900 5"));
    var fromMixed = parseCommand(buffer("Set mykey 0 900 5"));

    // then
    assertThat(fromUpper.name()).isEqualTo(SET);
    assertThat(fromMixed.name()).isEqualTo(SET);
  }

  @Test
  void throws_on_null_buffer() {
    // when
    var thrown = catchThrowable(() -> parseCommand(null));

    // then
    assertThat(thrown).isInstanceOf(NullPointerException.class);
  }

  @Test
  void throws_on_too_few_fields_for_command_line() {
    // when
    var thrown = catchThrowable(() -> parseCommand(buffer("set")));

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
  void dispatches_to_get_command_parser() {
    // when
    var command = parseCommand(buffer("get mykey other"));

    // then
    assertThat(command).isEqualTo(new GetCommand(List.of("mykey", "other")));
    assertThat(command.name()).isEqualTo(GET);
  }
}
