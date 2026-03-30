package ssonin.ccmemcached.protocol.command.parser;

import ssonin.ccmemcached.protocol.command.DeleteCommand;
import ssonin.ccmemcached.protocol.error.ClientError;

import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseKey;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseNoReply;

final class DeleteCommandParser {

  private DeleteCommandParser() {
    throw new AssertionError("Utility class");
  }

  static DeleteCommand parse(String[] parts) {
    if (parts.length < 2) {
      throw new ClientError("expected at least 2 fields, got %d".formatted(parts.length));
    }
    if (parts.length > 3) {
      throw new ClientError("expected at most 3 fields, got %d".formatted(parts.length));
    }
    return new DeleteCommand(parseKey(parts[1]), parseNoReply(parts, 2));
  }
}
