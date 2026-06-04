package ssonin.ccmemcached.protocol.command.parser;

import org.slf4j.Logger;
import ssonin.ccmemcached.protocol.error.ClientError;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Long.parseUnsignedLong;
import static java.util.Collections.unmodifiableList;
import static org.slf4j.LoggerFactory.getLogger;
import static ssonin.ccmemcached.protocol.MemcachedLimits.MAX_GET_KEYS;
import static ssonin.ccmemcached.protocol.MemcachedLimits.MAX_VALUE_BYTES;

final class ParsingSupport {

  private static final int MAX_KEY_LENGTH = 250;

  private static final Logger log = getLogger(ParsingSupport.class);

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

  static List<String> parseKeys(String[] parts) {
    if (parts.length < 2) {
      throw new ClientError("expected at least 2 fields, got %d".formatted(parts.length));
    }
    final var keyCount = parts.length - 1;
    if (keyCount > MAX_GET_KEYS) {
      throw new ClientError("key count exceeds maximum of %d, got %d".formatted(MAX_GET_KEYS, keyCount));
    }
    final List<String> keys = new ArrayList<>(keyCount);
    for (var i = 1; i < parts.length; i++) {
      keys.add(parseKey(parts[i]));
    }
    return unmodifiableList(keys);
  }

  static boolean parseNoReply(String[] parts, int noReplyIndex) {
    if (parts.length == noReplyIndex + 1) {
      if (!parts[noReplyIndex].equals("noreply")) {
        throw new ClientError("expected 'noreply', got '%s'".formatted(parts[noReplyIndex]));
      }
      return true;
    }
    return false;
  }

  static int parseFlags(String value) {
    try {
      final var flags = Integer.parseInt(value);
      if (flags < 0 || flags > 65535) {
        throw new ClientError("flags must be between 0 and 65535, got %d".formatted(flags));
      }
      return flags;
    } catch (NumberFormatException e) {
      log.debug("Invalid flags value: {}", value, e);
      throw new ClientError("flags must be a valid integer, got '%s'".formatted(value));
    }
  }

  static int parseExpTime(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      log.debug("Invalid exptime value: {}", value, e);
      throw new ClientError("exptime must be a valid integer, got '%s'".formatted(value));
    }
  }

  static int parseBytes(String value) {
    try {
      final var bytes = Integer.parseInt(value);
      if (bytes < 0) {
        throw new ClientError("bytes must be >= 0, got %d".formatted(bytes));
      }
      if (bytes > MAX_VALUE_BYTES) {
        throw new ClientError("bytes exceeds maximum size of %d".formatted(MAX_VALUE_BYTES));
      }
      return bytes;
    } catch (NumberFormatException e) {
      log.debug("Invalid bytes value: {}", value, e);
      throw new ClientError("bytes must be a valid integer, got '%s'".formatted(value));
    }
  }

  static long parseDelta(String value) {
    try {
      return parseUnsignedLong(value);
    } catch (NumberFormatException e) {
      log.debug("Invalid delta value: {}", value, e);
      throw new ClientError("delta must be a valid 64-bit integer, got '%s'".formatted(value));
    }
  }

  static long parseCasUnique(String value) {
    try {
      return parseUnsignedLong(value);
    } catch (NumberFormatException e) {
      log.debug("Invalid cas unique value: {}", value, e);
      throw new ClientError("cas unique must be a valid 64-bit integer, got '%s'".formatted(value));
    }
  }
}
