package ssonin.ccmemcached.protocol.command.parser;

import ssonin.ccmemcached.protocol.command.DecrCommand;
import ssonin.ccmemcached.protocol.error.ClientError;

import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseDelta;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseKey;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseNoReply;

final class DecrCommandParser {

  private DecrCommandParser() {
    throw new AssertionError("Utility class");
  }

  static DecrCommand parse(String[] parts) {
    if (parts.length < 3) {
      throw new ClientError("expected at least 3 fields, got %d".formatted(parts.length));
    }
    if (parts.length > 4) {
      throw new ClientError("expected at most 4 fields, got %d".formatted(parts.length));
    }
    return new DecrCommand(parseKey(parts[1]), parseDelta(parts[2]), parseNoReply(parts, 3));
  }
}
