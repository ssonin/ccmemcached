package ssonin.ccmemcached.protocol;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import ssonin.ccmemcached.cache.CacheEntry;
import ssonin.ccmemcached.cache.CacheService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.vertx.core.buffer.Buffer.buffer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static ssonin.ccmemcached.protocol.command.SetCommand.Builder.setCommand;

class ProtocolHandlerTest {

  private static final int MAX_COMMAND_LINE_BYTES = 8192;
  private static final int MAX_VALUE_BYTES = 1024 * 1024;

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
  void switches_parser_to_fixed_size_mode_when_value_size_is_at_maximum() {
    // when
    parserHandler.handle(buffer("set mykey 42 900 " + MAX_VALUE_BYTES));

    // then
    then(parser).should().fixedSizeMode(MAX_VALUE_BYTES);
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

  @Test
  void writes_error_and_recovers_after_value_size_exceeds_maximum() {
    // given
    given(cacheService.delete("mykey")).willReturn(true);

    // when
    parserHandler.handle(buffer("set mykey 0 900 " + (MAX_VALUE_BYTES + 1)));
    parserHandler.handle(buffer("delete mykey"));

    // then
    then(socket).should().write("CLIENT_ERROR: bytes exceeds maximum size of 1048576\r\n");
    then(cacheService).should().delete("mykey");
    then(socket).should().write("DELETED\r\n");
    then(parser).should(times(1)).delimitedMode("\r\n");
  }

  @Test
  void accepts_command_line_at_maximum_length() {
    // given
    var keys = keysForCommandLineLength(MAX_COMMAND_LINE_BYTES);
    var command = "get " + String.join(" ", keys);
    given(cacheService.getAllPresent(keys)).willReturn(Map.of());

    // when
    parserHandler.handle(buffer(command));

    // then
    then(cacheService).should().getAllPresent(keys);
    then(socket).should().write("END\r\n");
  }

  @Test
  void writes_error_and_recovers_after_command_line_exceeds_maximum_length() {
    // given
    var oversizedKeys = keysForCommandLineLength(MAX_COMMAND_LINE_BYTES + 1);
    given(cacheService.delete("mykey")).willReturn(true);

    // when
    parserHandler.handle(buffer("get " + String.join(" ", oversizedKeys)));
    parserHandler.handle(buffer("delete mykey"));

    // then
    then(socket).should().write("CLIENT_ERROR: command line exceeds maximum length of 8192 bytes\r\n");
    then(cacheService).should().delete("mykey");
    then(socket).should().write("DELETED\r\n");
    then(parser).should(times(1)).delimitedMode("\r\n");
  }

  @Test
  void writes_values_for_present_get_keys_in_request_order_and_ends_response() {
    // given
    var keys = List.of("second", "missing", "first");
    var entries = Map.of(
      "first", new CacheEntry(1, Duration.ofSeconds(60), "one".getBytes()),
      "second", new CacheEntry(2, Duration.ofSeconds(60), "two".getBytes())
    );
    given(cacheService.getAllPresent(keys)).willReturn(entries);

    // when
    parserHandler.handle(buffer("get second missing first"));

    // then
    then(cacheService).should().getAllPresent(keys);
    InOrder inOrder = inOrder(socket);
    inOrder.verify(socket).write("VALUE second 2 3\r\n");
    inOrder.verify(socket).write(buffer("two"));
    inOrder.verify(socket).write("\r\n");
    inOrder.verify(socket).write("VALUE first 1 3\r\n");
    inOrder.verify(socket).write(buffer("one"));
    inOrder.verify(socket).write("\r\n");
    inOrder.verify(socket).write("END\r\n");
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void deletes_existing_key_and_writes_deleted_response() {
    // given
    given(cacheService.delete("mykey")).willReturn(true);

    // when
    parserHandler.handle(buffer("delete mykey"));

    // then
    then(cacheService).should().delete("mykey");
    then(socket).should().write("DELETED\r\n");
  }

  @Test
  void writes_not_found_when_delete_target_is_missing() {
    // given
    given(cacheService.delete("missing")).willReturn(false);

    // when
    parserHandler.handle(buffer("delete missing"));

    // then
    then(cacheService).should().delete("missing");
    then(socket).should().write("NOT_FOUND\r\n");
  }

  @Test
  void deletes_without_writing_response_when_delete_uses_noreply() {
    // given
    given(cacheService.delete("quiet")).willReturn(true);

    // when
    parserHandler.handle(buffer("delete quiet noreply"));

    // then
    then(cacheService).should().delete("quiet");
    then(socket).shouldHaveNoMoreInteractions();
  }

  private RecordParser parser() {
    var parser = Mockito.mock(RecordParser.class);
    willAnswer(invocation -> {
      parserHandler = invocation.getArgument(0);
      return null;
    }).given(parser).handler(any());
    return parser;
  }

  private static List<String> keysForCommandLineLength(int targetLength) {
    var keys = new ArrayList<String>();
    var currentLength = "get".length();
    var separatorLength = 1;
    while (currentLength < targetLength) {
      var remaining = targetLength - currentLength - separatorLength;
      var keyLength = Math.min(250, remaining);
      keys.add("k".repeat(keyLength));
      currentLength += separatorLength + keyLength;
    }
    return List.copyOf(keys);
  }
}
