package ssonin.ccmemcached.protocol.command.parser;

import ssonin.ccmemcached.protocol.command.GetCommand;
import ssonin.ccmemcached.protocol.error.ClientError;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.unmodifiableList;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseKey;

final class GetCommandParser {

  private GetCommandParser() {
    throw new AssertionError("Utility class");
  }

  static GetCommand parse(String[] parts) {
    if (parts.length < 2) {
      throw new ClientError("expected at least 2 fields, got %d".formatted(parts.length));
    }
    final List<String> keys = new ArrayList<>(parts.length - 1);
    for (var i = 1; i < parts.length; i++) {
      keys.add(parseKey(parts[i]));
    }
    return new GetCommand(unmodifiableList(keys));
  }
}
