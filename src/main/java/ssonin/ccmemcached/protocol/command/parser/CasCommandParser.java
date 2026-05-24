package ssonin.ccmemcached.protocol.command.parser;

import ssonin.ccmemcached.protocol.command.CasCommand;
import ssonin.ccmemcached.protocol.error.ClientError;

import static ssonin.ccmemcached.protocol.command.CasCommand.Builder.casCommand;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseBytes;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseCasUnique;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseExpTime;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseFlags;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseKey;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseNoReply;

final class CasCommandParser {

  private CasCommandParser() {
    throw new AssertionError("Utility class");
  }

  static CasCommand parse(String[] parts) {
    if (parts.length < 6) {
      throw new ClientError("expected at least 6 fields, got %d".formatted(parts.length));
    }
    if (parts.length > 7) {
      throw new ClientError("expected at most 7 fields, got %d".formatted(parts.length));
    }
    return casCommand()
      .key(parseKey(parts[1]))
      .flags(parseFlags(parts[2]))
      .expTime(parseExpTime(parts[3]))
      .bytes(parseBytes(parts[4]))
      .casUnique(parseCasUnique(parts[5]))
      .noReply(parseNoReply(parts, 6))
      .build();
  }
}
