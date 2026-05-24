package ssonin.ccmemcached.protocol.command.parser;

import ssonin.ccmemcached.protocol.command.SetCommand;
import ssonin.ccmemcached.protocol.error.ClientError;

import static ssonin.ccmemcached.protocol.command.SetCommand.Builder.setCommand;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseBytes;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseExpTime;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseFlags;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseKey;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseNoReply;

final class SetCommandParser {

  private SetCommandParser() {
    throw new AssertionError("Utility class");
  }

  static SetCommand parse(String[] parts) {
    if (parts.length < 5) {
      throw new ClientError("expected at least 5 fields, got %d".formatted(parts.length));
    }
    if (parts.length > 6) {
      throw new ClientError("expected at most 6 fields, got %d".formatted(parts.length));
    }
    return setCommand()
      .key(parseKey(parts[1]))
      .flags(parseFlags(parts[2]))
      .expTime(parseExpTime(parts[3]))
      .bytes(parseBytes(parts[4]))
      .noReply(parseNoReply(parts, 5))
      .build();
  }
}
