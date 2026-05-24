package ssonin.ccmemcached.protocol.command.parser;

import ssonin.ccmemcached.protocol.command.GetCommand;

import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseKeys;

final class GetCommandParser {

  private GetCommandParser() {
    throw new AssertionError("Utility class");
  }

  static GetCommand parse(String[] parts) {
    return new GetCommand(parseKeys(parts));
  }
}
