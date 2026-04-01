package ssonin.ccmemcached.protocol;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import ssonin.ccmemcached.cache.CacheService;
import ssonin.ccmemcached.protocol.command.AddCommand;
import ssonin.ccmemcached.protocol.command.Command;
import ssonin.ccmemcached.protocol.command.DeleteCommand;
import ssonin.ccmemcached.protocol.command.GetCommand;
import ssonin.ccmemcached.protocol.command.SetCommand;
import ssonin.ccmemcached.protocol.command.StorageCommand;
import ssonin.ccmemcached.protocol.error.ApplicationError;
import ssonin.ccmemcached.protocol.error.ClientError;

import static io.vertx.core.buffer.Buffer.buffer;
import static ssonin.ccmemcached.protocol.ProtocolHandler.State.AWAITING_COMMAND;
import static ssonin.ccmemcached.protocol.ProtocolHandler.State.AWAITING_DATA;
import static ssonin.ccmemcached.protocol.ProtocolHandler.State.AWAITING_TRAILING_CRLF;
import static ssonin.ccmemcached.protocol.command.parser.CommandParser.parseCommand;

public final class ProtocolHandler {

  private static final int MAX_COMMAND_LINE_BYTES = 8 * 1024;

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
      socket.write(e.getMessage() + "\r\n");
    }
  }

  private void resetState() {
    state = AWAITING_COMMAND;
    pendingStorageCommand = null;
    data = null;
    parser.delimitedMode("\r\n");
  }

  private void process(Buffer buffer) {
    switch (state) {
      case AWAITING_COMMAND -> handleCommandLine(buffer);
      case AWAITING_DATA -> handleStorageData(buffer);
      case AWAITING_TRAILING_CRLF -> completeStorageWrite();
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
      case DeleteCommand deleteCommand -> handleDelete(deleteCommand);
      case GetCommand getCommand -> startRetrieval(getCommand);
      case SetCommand setCommand -> startStorage(setCommand);
      default -> throw new ClientError("command '%s' is not implemented".formatted(command.name().name().toLowerCase()));
    }
  }

  private void handleDelete(DeleteCommand command) {
    final var deleted = cacheService.delete(command.key());
    if (!command.noReply()) {
      final var response = deleted ? "DELETED" : "NOT_FOUND";
      socket.write("%s\r\n".formatted(response));
    }
  }

  private void startRetrieval(GetCommand command) {
    final var entries = cacheService.getAllPresent(command.keys());
    command.keys().forEach(key -> {
      final var entry = entries.get(key);
      if (entry != null) {
        socket.write("VALUE %s %s %s\r\n".formatted(key, entry.flags(), entry.data().length));
        socket.write(buffer(entry.data()));
        socket.write("\r\n");
      }
    });
    socket.write("END\r\n");
  }

  private void startStorage(StorageCommand command) {
    pendingStorageCommand = command;
    state = AWAITING_DATA;
    parser.fixedSizeMode(command.bytes());
  }

  private void handleStorageData(Buffer buffer) {
    data = buffer.getBytes();
    state = AWAITING_TRAILING_CRLF;
    parser.delimitedMode("\r\n");
  }

  private void completeStorageWrite() {
    final var response = switch (pendingStorageCommand) {
      case AddCommand addCommand -> cacheService.add(addCommand, data) ? "STORED" : "NOT_STORED";
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
