package ssonin.ccmemcached.protocol.command.parser;

import org.junit.jupiter.api.Test;
import ssonin.ccmemcached.protocol.command.AddCommand;
import ssonin.ccmemcached.protocol.command.AppendCommand;
import ssonin.ccmemcached.protocol.command.CasCommand;
import ssonin.ccmemcached.protocol.command.DecrCommand;
import ssonin.ccmemcached.protocol.command.DeleteCommand;
import ssonin.ccmemcached.protocol.command.GetCommand;
import ssonin.ccmemcached.protocol.command.GetsCommand;
import ssonin.ccmemcached.protocol.command.IncrCommand;
import ssonin.ccmemcached.protocol.command.PrependCommand;
import ssonin.ccmemcached.protocol.command.ReplaceCommand;
import ssonin.ccmemcached.protocol.command.TouchCommand;
import ssonin.ccmemcached.protocol.error.ApplicationError;
import ssonin.ccmemcached.protocol.error.CommandNameError;

import java.util.List;

import static io.vertx.core.buffer.Buffer.buffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static ssonin.ccmemcached.protocol.command.AddCommand.Builder.addCommand;
import static ssonin.ccmemcached.protocol.command.AppendCommand.Builder.appendCommand;
import static ssonin.ccmemcached.protocol.command.CasCommand.Builder.casCommand;
import static ssonin.ccmemcached.protocol.command.CommandName.ADD;
import static ssonin.ccmemcached.protocol.command.CommandName.APPEND;
import static ssonin.ccmemcached.protocol.command.CommandName.CAS;
import static ssonin.ccmemcached.protocol.command.CommandName.DECR;
import static ssonin.ccmemcached.protocol.command.CommandName.DELETE;
import static ssonin.ccmemcached.protocol.command.CommandName.GET;
import static ssonin.ccmemcached.protocol.command.CommandName.GETS;
import static ssonin.ccmemcached.protocol.command.CommandName.INCR;
import static ssonin.ccmemcached.protocol.command.CommandName.PREPEND;
import static ssonin.ccmemcached.protocol.command.CommandName.REPLACE;
import static ssonin.ccmemcached.protocol.command.CommandName.SET;
import static ssonin.ccmemcached.protocol.command.CommandName.TOUCH;
import static ssonin.ccmemcached.protocol.command.PrependCommand.Builder.prependCommand;
import static ssonin.ccmemcached.protocol.command.ReplaceCommand.Builder.replaceCommand;
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
  void dispatches_to_add_command_parser() {
    // given
    var input = buffer("add mykey 0 900 5");

    // when
    var command = parseCommand(input);

    // then
    assertThat(command).isEqualTo(addCommand()
      .key("mykey")
      .flags(0)
      .expTime(900)
      .bytes(5)
      .build());
    assertThat(command.name()).isEqualTo(ADD);
  }

  @Test
  void dispatches_to_append_command_parser() {
    // given
    var input = buffer("append mykey 0 900 5");

    // when
    var command = parseCommand(input);

    // then
    assertThat(command).isEqualTo(appendCommand()
      .key("mykey")
      .bytes(5)
      .build());
    assertThat(command.name()).isEqualTo(APPEND);
    assertThat(command).isInstanceOf(AppendCommand.class);
  }

  @Test
  void dispatches_to_prepend_command_parser() {
    // given
    var input = buffer("prepend mykey 0 900 5");

    // when
    var command = parseCommand(input);

    // then
    assertThat(command).isEqualTo(prependCommand()
      .key("mykey")
      .bytes(5)
      .build());
    assertThat(command.name()).isEqualTo(PREPEND);
    assertThat(command).isInstanceOf(PrependCommand.class);
  }

  @Test
  void dispatches_to_cas_command_parser() {
    // given
    var input = buffer("cas mykey 0 900 5 42 noreply");

    // when
    var command = parseCommand(input);

    // then
    assertThat(command).isEqualTo(casCommand()
      .key("mykey")
      .flags(0)
      .expTime(900)
      .bytes(5)
      .casUnique(42L)
      .noReply(true)
      .build());
    assertThat(command.name()).isEqualTo(CAS);
    assertThat(command).isInstanceOf(CasCommand.class);
  }

  @Test
  void dispatches_to_replace_command_parser() {
    // given
    var input = buffer("replace mykey 0 900 5");

    // when
    var command = parseCommand(input);

    // then
    assertThat(command).isEqualTo(replaceCommand()
      .key("mykey")
      .flags(0)
      .expTime(900)
      .bytes(5)
      .build());
    assertThat(command.name()).isEqualTo(REPLACE);
  }

  @Test
  void resolves_command_name_case_insensitively() {
    // when
    var fromAdd = parseCommand(buffer("Add mykey 0 900 5"));
    var fromReplace = parseCommand(buffer("RePlAcE mykey 0 900 5"));
    var fromUpper = parseCommand(buffer("SET mykey 0 900 5"));
    var fromMixed = parseCommand(buffer("Set mykey 0 900 5"));

    // then
    assertThat(fromAdd).isInstanceOf(AddCommand.class);
    assertThat(fromReplace).isInstanceOf(ReplaceCommand.class);
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

  @Test
  void dispatches_to_gets_command_parser() {
    // when
    var command = parseCommand(buffer("gets mykey other"));

    // then
    assertThat(command).isEqualTo(new GetsCommand(List.of("mykey", "other")));
    assertThat(command.name()).isEqualTo(GETS);
  }

  @Test
  void dispatches_to_delete_command_parser() {
    // when
    var command = parseCommand(buffer("delete mykey noreply"));

    // then
    assertThat(command).isEqualTo(new DeleteCommand("mykey", true));
    assertThat(command.name()).isEqualTo(DELETE);
  }

  @Test
  void dispatches_to_touch_command_parser() {
    // when
    var command = parseCommand(buffer("touch mykey 60 noreply"));

    // then
    assertThat(command).isEqualTo(new TouchCommand("mykey", 60, true));
    assertThat(command.name()).isEqualTo(TOUCH);
  }

  @Test
  void dispatches_to_incr_command_parser() {
    // when
    var command = parseCommand(buffer("incr mykey 42 noreply"));

    // then
    assertThat(command).isEqualTo(new IncrCommand("mykey", 42L, true));
    assertThat(command.name()).isEqualTo(INCR);
  }

  @Test
  void dispatches_to_decr_command_parser() {
    // when
    var command = parseCommand(buffer("decr mykey 42 noreply"));

    // then
    assertThat(command).isEqualTo(new DecrCommand("mykey", 42L, true));
    assertThat(command.name()).isEqualTo(DECR);
  }
}
