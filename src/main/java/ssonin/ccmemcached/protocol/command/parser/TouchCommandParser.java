package ssonin.ccmemcached.protocol.command.parser;

import ssonin.ccmemcached.protocol.command.TouchCommand;
import ssonin.ccmemcached.protocol.error.ClientError;

import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseExpTime;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseKey;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseNoReply;

final class TouchCommandParser {

  private TouchCommandParser() {
    throw new AssertionError("Utility class");
  }

  static TouchCommand parse(String[] parts) {
    if (parts.length < 3) {
      throw new ClientError("expected at least 3 fields, got %d".formatted(parts.length));
    }
    if (parts.length > 4) {
      throw new ClientError("expected at most 4 fields, got %d".formatted(parts.length));
    }
    return new TouchCommand(parseKey(parts[1]), parseExpTime(parts[2]), parseNoReply(parts, 3));
  }
}
