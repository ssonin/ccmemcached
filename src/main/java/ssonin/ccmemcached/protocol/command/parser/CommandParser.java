package ssonin.ccmemcached.protocol.command.parser;

import io.vertx.core.buffer.Buffer;
import ssonin.ccmemcached.protocol.command.Command;
import ssonin.ccmemcached.protocol.error.CommandNameError;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import static java.util.Map.entry;
import static java.util.Objects.requireNonNull;

public final class CommandParser {

  private CommandParser() {
    throw new AssertionError("Utility class");
  }

  private static final Map<String, Function<String[], Command>> parsers = Map.ofEntries(
    entry("add", AddCommandParser::parse),
    entry("append", AppendCommandParser::parse),
    entry("cas", CasCommandParser::parse),
    entry("decr", DecrCommandParser::parse),
    entry("delete", DeleteCommandParser::parse),
    entry("get", GetCommandParser::parse),
    entry("gets", GetsCommandParser::parse),
    entry("incr", IncrCommandParser::parse),
    entry("prepend", PrependCommandParser::parse),
    entry("replace", ReplaceCommandParser::parse),
    entry("set", SetCommandParser::parse),
    entry("touch", TouchCommandParser::parse)
  );

  public static Command parseCommand(Buffer buffer) {
    requireNonNull(buffer, "buffer must not be null");
    final var parts = buffer.toString().split(" ");
    final var name = parts[0].toLowerCase(Locale.ROOT);
    final var parser = parsers.get(name);
    if (parser == null) {
      throw new CommandNameError(parts[0]);
    }
    return parser.apply(parts);
  }
}
