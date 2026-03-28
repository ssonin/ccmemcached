package ssonin.ccmemcached.protocol;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import ssonin.ccmemcached.cache.CacheService;
import ssonin.ccmemcached.protocol.command.Command;
import ssonin.ccmemcached.protocol.error.ApplicationError;

import static ssonin.ccmemcached.protocol.ProtocolHandler.State.AWAITING_COMMAND;
import static ssonin.ccmemcached.protocol.ProtocolHandler.State.AWAITING_DATA;
import static ssonin.ccmemcached.protocol.ProtocolHandler.State.AWAITING_TRAILING_CRLF;
import static ssonin.ccmemcached.protocol.command.CommandParser.parseCommand;

public final class ProtocolHandler {

  private final CacheService cacheService;
  private final NetSocket socket;
  private final RecordParser parser;
  private State state = AWAITING_COMMAND;
  private Command command;
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
    command = null;
    data = null;
    parser.delimitedMode("\r\n");
  }

  private void process(Buffer buffer) {
    switch (state) {
      case AWAITING_COMMAND -> {
        command = parseCommand(buffer);
        switch (command.type()) {
          case STORAGE ->  {
            state = AWAITING_DATA;
            parser.fixedSizeMode(command.bytes());
          }
          case RETRIEVAL -> {
            throw new UnsupportedOperationException("Not implemented yet");
          }
        }
      }
      case AWAITING_DATA -> {
        data = buffer.getBytes();
        state = AWAITING_TRAILING_CRLF;
        parser.delimitedMode("\r\n");
      }
      case AWAITING_TRAILING_CRLF -> {
        cacheService.put(command, data);
        if (!command.noReply()) {
          socket.write("STORED\r\n");
        }
        state = AWAITING_COMMAND;
        command = null;
        data = null;
      }
    }
  }

  enum State {
    AWAITING_COMMAND,
    AWAITING_DATA,
    AWAITING_TRAILING_CRLF
  }
}
