package ssonin.ccmemcached.protocol.command.parser;

import io.vertx.core.buffer.Buffer;
import ssonin.ccmemcached.protocol.command.Command;
import ssonin.ccmemcached.protocol.command.CommandName;
import ssonin.ccmemcached.protocol.error.ClientError;
import ssonin.ccmemcached.protocol.error.CommandNameError;

import java.util.Map;
import java.util.function.Function;

import static java.util.Map.entry;
import static java.util.Objects.requireNonNull;
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

public final class CommandParser {

  private CommandParser() {
    throw new AssertionError("Utility class");
  }

  private static final Map<CommandName, Function<String[], Command>> parsers = Map.ofEntries(
    entry(ADD, AddCommandParser::parse),
    entry(APPEND, AppendCommandParser::parse),
    entry(CAS, CasCommandParser::parse),
    entry(DECR, DecrCommandParser::parse),
    entry(DELETE, DeleteCommandParser::parse),
    entry(GET, GetCommandParser::parse),
    entry(GETS, GetsCommandParser::parse),
    entry(INCR, IncrCommandParser::parse),
    entry(PREPEND, PrependCommandParser::parse),
    entry(REPLACE, ReplaceCommandParser::parse),
    entry(SET, SetCommandParser::parse),
    entry(TOUCH, TouchCommandParser::parse)
  );

  public static Command parseCommand(Buffer buffer) {
    requireNonNull(buffer, "buffer must not be null");
    final var parts = buffer.toString().split(" ");
    final var name = parseName(parts[0]);
    final var parser = parsers.get(name);
    if (parser == null) {
      throw new ClientError("command '%s' is not implemented".formatted(name.name().toLowerCase()));
    }
    return parser.apply(parts);
  }

  private static CommandName parseName(String name) {
    try {
      return CommandName.valueOf(name.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new CommandNameError(name);
    }
  }
}
