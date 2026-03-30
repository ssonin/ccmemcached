package ssonin.ccmemcached.protocol.command.parser;

import org.slf4j.Logger;
import ssonin.ccmemcached.protocol.command.SetCommand;
import ssonin.ccmemcached.protocol.error.ClientError;

import static org.slf4j.LoggerFactory.getLogger;
import static ssonin.ccmemcached.protocol.command.SetCommand.Builder.setCommand;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseKey;
import static ssonin.ccmemcached.protocol.command.parser.ParsingSupport.parseNoReply;

final class SetCommandParser {

  private static final Logger logger = getLogger(SetCommandParser.class);

  public static SetCommand parse(String[] parts) {
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
}
