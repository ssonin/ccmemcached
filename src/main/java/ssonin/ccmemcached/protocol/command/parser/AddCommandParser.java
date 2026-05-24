package ssonin.ccmemcached.protocol.command.parser;

import ssonin.ccmemcached.protocol.command.AddCommand;
import ssonin.ccmemcached.protocol.error.ClientError;

import static ssonin.ccmemcached.protocol.command.AddCommand.Builder.addCommand;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseBytes;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseExpTime;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseFlags;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseKey;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseNoReply;

final class AddCommandParser {

  private AddCommandParser() {
    throw new AssertionError("Utility class");
  }

  static AddCommand parse(String[] parts) {
    if (parts.length < 5) {
      throw new ClientError("expected at least 5 fields, got %d".formatted(parts.length));
    }
    if (parts.length > 6) {
      throw new ClientError("expected at most 6 fields, got %d".formatted(parts.length));
    }
    return addCommand()
      .key(parseKey(parts[1]))
      .flags(parseFlags(parts[2]))
      .expTime(parseExpTime(parts[3]))
      .bytes(parseBytes(parts[4]))
      .noReply(parseNoReply(parts, 5))
      .build();
  }
}
