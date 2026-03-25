package ssonin.ccmemcached.protocol.command;

import io.vertx.core.buffer.Buffer;
import org.slf4j.Logger;
import ssonin.ccmemcached.protocol.error.ClientError;
import ssonin.ccmemcached.protocol.error.CommandNameError;

import java.util.Objects;

import static org.slf4j.LoggerFactory.getLogger;
import static ssonin.ccmemcached.protocol.command.Command.Builder.command;

public final class CommandParser {

  private static final Logger logger = getLogger(CommandParser.class);
  private static final int MAX_KEY_LENGTH = 250;

  public static Command parseCommand(Buffer buffer) {
    Objects.requireNonNull(buffer, "buffer must not be null");
    final var parts = buffer.toString().split(" ");
    if (parts.length < 5) {
      throw new ClientError("expected at least 5 fields, got %d".formatted(parts.length));
    }
    if (parts.length > 6) {
      throw new ClientError("expected at most 6 fields, got %d".formatted(parts.length));
    }
    return command()
      .name(parseName(parts[0]))
      .key(parseKey(parts[1]))
      .flags(parseFlags(parts[2]))
      .expTime(parseExpTime(parts[3]))
      .bytes(parseBytes(parts[4]))
      .noReply(parseNoReply(parts))
      .build();
  }

  private static CommandName parseName(String name) {
    try {
      return CommandName.valueOf(name.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new CommandNameError(name);
    }
  }

  private static String parseKey(String key) {
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

  private static int parseFlags(String value) {
    try {
      final var flags = Integer.parseInt(value);
      if (flags < 0 || flags > 65535) {
        throw new ClientError("flags must be between 0 and 65535, got %d".formatted(flags));
      }
      return flags;
    } catch (NumberFormatException e) {
      logger.debug("Invalid flags value: {}", value, e);
      throw new ClientError("flags must be a valid integer, got '%s'".formatted(value));
    }
  }

  private static int parseExpTime(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      logger.debug("Invalid exptime value: {}", value, e);
      throw new ClientError("exptime must be a valid integer, got '%s'".formatted(value));
    }
  }

  private static int parseBytes(String value) {
    try {
      final var bytes = Integer.parseInt(value);
      if (bytes < 0) {
        throw new ClientError("bytes must be >= 0, got %d".formatted(bytes));
      }
      return bytes;
    } catch (NumberFormatException e) {
      logger.debug("Invalid bytes value: {}", value, e);
      throw new ClientError("bytes must be a valid integer, got '%s'".formatted(value));
    }
  }

  private static boolean parseNoReply(String[] parts) {
    if (parts.length == 6) {
      if (!parts[5].equals("noreply")) {
        throw new ClientError("expected 'noreply', got '%s'".formatted(parts[5]));
      }
      return true;
    }
    return false;
  }
}
