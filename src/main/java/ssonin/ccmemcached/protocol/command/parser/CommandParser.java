package ssonin.ccmemcached.protocol.command.parser;

import io.vertx.core.buffer.Buffer;
import ssonin.ccmemcached.protocol.command.Command;
import ssonin.ccmemcached.protocol.command.CommandName;
import ssonin.ccmemcached.protocol.error.ClientError;
import ssonin.ccmemcached.protocol.error.CommandNameError;

import java.util.Map;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static ssonin.ccmemcached.protocol.command.CommandName.DELETE;
import static ssonin.ccmemcached.protocol.command.CommandName.GET;
import static ssonin.ccmemcached.protocol.command.CommandName.SET;

public final class CommandParser {

  private static final Map<CommandName, Function<String[], Command>> parsers = Map.of(
    DELETE, DeleteCommandParser::parse,
    GET, GetCommandParser::parse,
    SET, SetCommandParser::parse
  );

  public static Command parseCommand(Buffer buffer) {
    requireNonNull(buffer, "buffer must not be null");
    final var parts = buffer.toString().split(" ");
    if (parts.length < 2) {
      throw new ClientError("expected at least 2 fields, got %d".formatted(parts.length));
    }
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
