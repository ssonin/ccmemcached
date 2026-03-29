package ssonin.ccmemcached.protocol.command.parser;

import ssonin.ccmemcached.protocol.error.ClientError;

final class ParsingSupport {

  private static final int MAX_KEY_LENGTH = 250;

  private ParsingSupport() {
    throw new AssertionError("Utility class");
  }

  static String parseKey(String key) {
    if (key.isEmpty()) {
      throw new ClientError("key must not be empty");
    }
    if (key.length() > MAX_KEY_LENGTH) {
      throw new ClientError("key exceeds maximum length of %d".formatted(MAX_KEY_LENGTH));
    }
    for (int i = 0; i < key.length(); i++) {
      final var c = key.charAt(i);
      if (c <= 0x1f || c == 0x7f) {
        throw new ClientError("key contains invalid character 0x%x".formatted((int) c));
      }
    }
    return key;
  }

  static boolean parseNoReply(String[] parts) {
    if (parts.length == 6) {
      if (!parts[5].equals("noreply")) {
        throw new ClientError("expected 'noreply', got '%s'".formatted(parts[5]));
      }
      return true;
    }
    return false;
  }
}
