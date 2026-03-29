package ssonin.ccmemcached.protocol;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ssonin.ccmemcached.cache.CacheService;

import java.util.Arrays;

import static io.vertx.core.buffer.Buffer.buffer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.times;
import static ssonin.ccmemcached.protocol.command.SetCommand.Builder.setCommand;

class ProtocolHandlerTest {

  private final CacheService cacheService = Mockito.mock(CacheService.class);
  private final NetSocket socket = Mockito.mock(NetSocket.class);
  private final RecordParser parser = parser();

  private final ProtocolHandler tested = new ProtocolHandler(cacheService, socket, parser);

  private Handler<Buffer> parserHandler;

  @Test
  void start_registers_parser_as_socket_handler() {
    // when
    tested.start();

    // then
    then(socket).should().handler(parser);
  }

  @Test
  void switches_parser_to_fixed_size_mode_when_set_command_is_received() {
    // when
    parserHandler.handle(buffer("set mykey 42 900 5"));

    // then
    then(parser).should().fixedSizeMode(5);
  }

  @Test
  void stores_value_and_writes_stored_response_when_set_command_completes() {
    // given
    var expectedCommand = setCommand()
      .key("mykey")
      .flags(42)
      .expTime(900)
      .bytes(5)
      .build();

    // when
    parserHandler.handle(buffer("set mykey 42 900 5"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer("\r\n"));

    // then
    then(cacheService).should().put(eq(expectedCommand), argThat(data -> Arrays.equals(data, "value".getBytes())));
    then(parser).should(times(2)).delimitedMode("\r\n");
    then(socket).should().write("STORED\r\n");
  }

  @Test
  void stores_value_without_writing_response_when_set_uses_noreply() {
    // given
    var expectedCommand = setCommand()
      .key("mykey")
      .flags(7)
      .expTime(900)
      .bytes(5)
      .noReply(true)
      .build();

    // when
    parserHandler.handle(buffer("set mykey 7 900 5 noreply"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer("\r\n"));

    // then
    then(cacheService).should().put(eq(expectedCommand), argThat(data -> Arrays.equals(data, "value".getBytes())));
    then(socket).shouldHaveNoMoreInteractions();
  }

  @Test
  void writes_error_and_resets_state_after_invalid_command() {
    // given
    var expectedCommand = setCommand()
      .key("next")
      .flags(9)
      .expTime(60)
      .bytes(5)
      .build();

    // when
    parserHandler.handle(buffer("set mykey 0 900"));
    parserHandler.handle(buffer("set next 9 60 5"));
    parserHandler.handle(buffer("hello"));
    parserHandler.handle(buffer("\r\n"));

    // then
    then(socket).should().write("CLIENT_ERROR: expected at least 5 fields, got 4\r\n");
    then(socket).should().write("STORED\r\n");
    then(parser).should(times(3)).delimitedMode("\r\n");
    then(parser).should().fixedSizeMode(5);
    then(cacheService).should().put(eq(expectedCommand), argThat(data -> Arrays.equals(data, "hello".getBytes())));
  }

  private RecordParser parser() {
    var parser = Mockito.mock(RecordParser.class);
    willAnswer(invocation -> {
      parserHandler = invocation.getArgument(0);
      return null;
    }).given(parser).handler(any());
    return parser;
  }
}
