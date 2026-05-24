package ssonin.ccmemcached.protocol.command.parser;

import ssonin.ccmemcached.protocol.command.GetsCommand;

import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseKeys;

final class GetsCommandParser {

  private GetsCommandParser() {
    throw new AssertionError("Utility class");
  }

  static GetsCommand parse(String[] parts) {
    return new GetsCommand(parseKeys(parts));
  }
}
