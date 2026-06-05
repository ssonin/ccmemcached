package ssonin.ccmemcached.protocol;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import org.slf4j.Logger;
import ssonin.ccmemcached.cache.CacheEntry;
import ssonin.ccmemcached.cache.CacheService;
import ssonin.ccmemcached.protocol.command.AddCommand;
import ssonin.ccmemcached.protocol.command.AppendCommand;
import ssonin.ccmemcached.protocol.command.CasCommand;
import ssonin.ccmemcached.protocol.command.Command;
import ssonin.ccmemcached.protocol.command.DecrCommand;
import ssonin.ccmemcached.protocol.command.DeleteCommand;
import ssonin.ccmemcached.protocol.command.IncrCommand;
import ssonin.ccmemcached.protocol.command.PrependCommand;
import ssonin.ccmemcached.protocol.command.ReplaceCommand;
import ssonin.ccmemcached.protocol.command.RetrievalCommand;
import ssonin.ccmemcached.protocol.command.SetCommand;
import ssonin.ccmemcached.protocol.command.StorageCommand;
import ssonin.ccmemcached.protocol.command.TouchCommand;
import ssonin.ccmemcached.protocol.error.ApplicationError;
import ssonin.ccmemcached.protocol.error.ClientError;
import ssonin.ccmemcached.protocol.error.ErrorType;

import static io.vertx.core.buffer.Buffer.buffer;
import static org.slf4j.LoggerFactory.getLogger;
import static ssonin.ccmemcached.protocol.MemcachedLimits.MAX_COMMAND_LINE_BYTES;
import static ssonin.ccmemcached.protocol.MemcachedLimits.MAX_VALUE_BYTES;
import static ssonin.ccmemcached.protocol.ProtocolHandler.State.AWAITING_COMMAND;
import static ssonin.ccmemcached.protocol.ProtocolHandler.State.AWAITING_DATA;
import static ssonin.ccmemcached.protocol.ProtocolHandler.State.AWAITING_TRAILING_CRLF;
import static ssonin.ccmemcached.protocol.command.parser.CommandParser.parseCommand;
import static ssonin.ccmemcached.protocol.error.ErrorType.SERVER_ERROR;

public final class ProtocolHandler {

  private static final Logger log = getLogger(ProtocolHandler.class);

  private final CacheService cacheService;
  private final NetSocket socket;
  private final RecordParser parser;

  private State state = AWAITING_COMMAND;
  private StorageCommand pendingStorageCommand;
  private byte[] data;

  public ProtocolHandler(CacheService cacheService, NetSocket socket) {
    this(cacheService, socket, RecordParser.newDelimited("\r\n"));
  }

  ProtocolHandler(CacheService cacheService, NetSocket socket, RecordParser parser) {
    this.cacheService = cacheService;
    this.socket = socket;
    this.parser = parser;
    this.parser.maxRecordSize(MAX_COMMAND_LINE_BYTES);
    this.parser.exceptionHandler(this::handleParserError);
    this.parser.handler(this::handle);
  }

  public void start() {
    socket.handler(parser);
  }

  private void handle(Buffer buffer) {
    try {
      process(buffer);
    } catch (ApplicationError e) {
      resetState();
      socket.write(errorResponse(e));
    } catch (RuntimeException e) {
      log.error("Unexpected error while processing client command", e);
      socket.end(buffer(errorResponse(SERVER_ERROR, "internal server error")));
    }
  }

  private void handleParserError(Throwable error) {
    log.warn("Closing client connection after record parser failure", error);
    socket.end();
  }

  private String errorResponse(ApplicationError error) {
    return errorResponse(error.type(), error.getMessage());
  }

  private String errorResponse(ErrorType type, String message) {
    return switch (type) {
      case ERROR -> "ERROR\r\n";
      case CLIENT_ERROR, SERVER_ERROR -> "%s %s\r\n".formatted(type, message);
    };
  }

  private void resetState() {
    state = AWAITING_COMMAND;
    pendingStorageCommand = null;
    data = null;
    delimitedMode();
  }

  private void process(Buffer buffer) {
    switch (state) {
      case AWAITING_COMMAND -> handleCommandLine(buffer);
      case AWAITING_DATA -> handleStorageData(buffer);
      case AWAITING_TRAILING_CRLF -> completeStorageWrite(buffer);
    }
  }

  private void handleCommandLine(Buffer buffer) {
    if (buffer.length() > MAX_COMMAND_LINE_BYTES) {
      throw new ClientError("command line exceeds maximum length of %d bytes".formatted(MAX_COMMAND_LINE_BYTES));
    }
    final var command = parseCommand(buffer);
    dispatch(command);
  }

  private void dispatch(Command command) {
    switch (command) {
      case AddCommand addCommand -> startStorage(addCommand);
      case AppendCommand appendCommand -> startStorage(appendCommand);
      case CasCommand casCommand -> startStorage(casCommand);
      case DecrCommand decrCommand -> handleDecr(decrCommand);
      case DeleteCommand deleteCommand -> handleDelete(deleteCommand);
      case IncrCommand incrCommand -> handleIncr(incrCommand);
      case PrependCommand prependCommand -> startStorage(prependCommand);
      case ReplaceCommand replaceCommand -> startStorage(replaceCommand);
      case RetrievalCommand retrievalCommand -> startRetrieval(retrievalCommand);
      case SetCommand setCommand -> startStorage(setCommand);
      case TouchCommand touchCommand -> handleTouch(touchCommand);
    }
  }

  private void handleDelete(DeleteCommand command) {
    final var deleted = cacheService.delete(command.key());
    if (!command.noReply()) {
      final var response = deleted ? "DELETED" : "NOT_FOUND";
      socket.write("%s\r\n".formatted(response));
    }
  }

  private void handleIncr(IncrCommand command) {
    final var updatedValue = cacheService.increment(command);
    if (!command.noReply()) {
      socket.write("%s\r\n".formatted(updatedValue.isPresent() ? Long.toUnsignedString(updatedValue.getAsLong()) : "NOT_FOUND"));
    }
  }

  private void handleDecr(DecrCommand command) {
    final var updatedValue = cacheService.decrement(command);
    if (!command.noReply()) {
      socket.write("%s\r\n".formatted(updatedValue.isPresent() ? Long.toUnsignedString(updatedValue.getAsLong()) : "NOT_FOUND"));
    }
  }

  private void startRetrieval(RetrievalCommand command) {
    final var entries = cacheService.getAllPresent(command.keys());
    command.keys().forEach(key -> {
      final var entry = entries.get(key);
      if (entry != null) {
        socket.write(valueLine(key, entry, command.includeCasUnique()));
        socket.write(buffer(entry.data()));
        socket.write("\r\n");
      }
    });
    socket.write("END\r\n");
  }

  private String valueLine(String key, CacheEntry entry, boolean includeCasUnique) {
    if (includeCasUnique) {
      return "VALUE %s %s %s %s\r\n".formatted(
        key,
        entry.flags(),
        entry.data().length,
        Long.toUnsignedString(entry.casUnique())
      );
    }
    return "VALUE %s %s %s\r\n".formatted(key, entry.flags(), entry.data().length);
  }

  private void handleTouch(TouchCommand command) {
    final var touched = cacheService.touch(command);
    if (!command.noReply()) {
      final var response = touched ? "TOUCHED" : "NOT_FOUND";
      socket.write("%s\r\n".formatted(response));
    }
  }

  private void startStorage(StorageCommand command) {
    pendingStorageCommand = command;
    state = AWAITING_DATA;
    parser.maxRecordSize(MAX_VALUE_BYTES);
    parser.fixedSizeMode(command.bytes());
  }

  private void handleStorageData(Buffer buffer) {
    data = buffer.getBytes();
    state = AWAITING_TRAILING_CRLF;
    delimitedMode();
  }

  private void delimitedMode() {
    parser.maxRecordSize(MAX_COMMAND_LINE_BYTES);
    parser.delimitedMode("\r\n");
  }

  private void completeStorageWrite(Buffer trailingData) {
    if (trailingData.length() != 0) {
      throw new ClientError("expected CRLF after data block");
    }
    final var response = switch (pendingStorageCommand) {
      case AddCommand addCommand -> cacheService.add(addCommand, data) ? "STORED" : "NOT_STORED";
      case AppendCommand appendCommand -> cacheService.append(appendCommand, data).name();
      case CasCommand casCommand -> cacheService.cas(casCommand, data).name();
      case PrependCommand prependCommand -> cacheService.prepend(prependCommand, data).name();
      case ReplaceCommand replaceCommand -> cacheService.replace(replaceCommand, data) ? "STORED" : "NOT_STORED";
      case SetCommand setCommand -> {
        cacheService.put(setCommand, data);
        yield "STORED";
      }
    };
    if (!pendingStorageCommand.noReply()) {
      socket.write("%s\r\n".formatted(response));
    }
    resetState();
  }

  enum State {
    AWAITING_COMMAND,
    AWAITING_DATA,
    AWAITING_TRAILING_CRLF
  }
}
