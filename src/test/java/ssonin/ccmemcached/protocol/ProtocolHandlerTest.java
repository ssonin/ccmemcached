package ssonin.ccmemcached.protocol;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ssonin.ccmemcached.cache.CacheEntry;
import ssonin.ccmemcached.cache.CacheService;
import ssonin.ccmemcached.cache.StoreResult;
import ssonin.ccmemcached.protocol.command.DecrCommand;
import ssonin.ccmemcached.protocol.command.IncrCommand;
import ssonin.ccmemcached.protocol.command.TouchCommand;
import ssonin.ccmemcached.protocol.error.ApplicationError;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static io.vertx.core.buffer.Buffer.buffer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static ssonin.ccmemcached.cache.CacheEntry.cacheEntry;
import static ssonin.ccmemcached.protocol.command.AddCommand.Builder.addCommand;
import static ssonin.ccmemcached.protocol.command.AppendCommand.Builder.appendCommand;
import static ssonin.ccmemcached.protocol.command.CasCommand.Builder.casCommand;
import static ssonin.ccmemcached.protocol.command.PrependCommand.Builder.prependCommand;
import static ssonin.ccmemcached.protocol.command.ReplaceCommand.Builder.replaceCommand;
import static ssonin.ccmemcached.protocol.command.SetCommand.Builder.setCommand;
import static ssonin.ccmemcached.protocol.error.ErrorType.SERVER_ERROR;

class ProtocolHandlerTest {

  private static final int MAX_COMMAND_LINE_BYTES = 8192;
  private static final int MAX_VALUE_BYTES = 1024 * 1024;

  private final CacheService cacheService = Mockito.mock(CacheService.class);
  private final NetSocket socket = Mockito.mock(NetSocket.class);
  private final RecordParser parser = parser();
  private final ResponseFormatter responseFormatter = Mockito.mock(ResponseFormatter.class);

  private final ProtocolHandler tested = new ProtocolHandler(cacheService, socket, parser, responseFormatter);

  private Handler<Buffer> parserHandler;
  private Handler<Throwable> parserExceptionHandler;

  ProtocolHandlerTest() {
    stubResponseFormatter();
  }

  @Test
  void configures_parser_with_command_line_limit_and_exception_handler() {
    // then
    then(parser).should().maxRecordSize(MAX_COMMAND_LINE_BYTES);
    then(parser).should().exceptionHandler(any());
  }

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
    then(parser).should().maxRecordSize(MAX_VALUE_BYTES);
    then(parser).should().fixedSizeMode(5);
  }

  @Test
  void restores_command_line_limit_when_storage_data_is_received() {
    // when
    parserHandler.handle(buffer("set mykey 42 900 5"));
    parserHandler.handle(buffer("value"));

    // then
    then(parser).should(times(2)).maxRecordSize(MAX_COMMAND_LINE_BYTES);
    then(parser).should().delimitedMode("\r\n");
  }

  @Test
  void closes_connection_without_response_after_parser_failure() {
    // when
    parserExceptionHandler.handle(new IllegalStateException("The current record is too long"));

    // then
    then(socket).should().end();
    then(socket).shouldHaveNoMoreInteractions();
  }

  @Test
  void switches_parser_to_fixed_size_mode_when_add_command_is_received() {
    // when
    parserHandler.handle(buffer("add mykey 42 900 5"));

    // then
    then(parser).should().fixedSizeMode(5);
  }

  @Test
  void switches_parser_to_fixed_size_mode_when_append_command_is_received() {
    // when
    parserHandler.handle(buffer("append mykey 0 900 5"));

    // then
    then(parser).should().fixedSizeMode(5);
  }

  @Test
  void switches_parser_to_fixed_size_mode_when_prepend_command_is_received() {
    // when
    parserHandler.handle(buffer("prepend mykey 0 900 5"));

    // then
    then(parser).should().fixedSizeMode(5);
  }

  @Test
  void switches_parser_to_fixed_size_mode_when_replace_command_is_received() {
    // when
    parserHandler.handle(buffer("replace mykey 42 900 5"));

    // then
    then(parser).should().fixedSizeMode(5);
  }

  @Test
  void switches_parser_to_fixed_size_mode_when_cas_command_is_received() {
    // when
    parserHandler.handle(buffer("cas mykey 42 900 5 1"));

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
    given(cacheService.put(eq(expectedCommand), any(byte[].class))).willReturn(StoreResult.STORED);

    // when
    parserHandler.handle(buffer("set mykey 42 900 5"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer());

    // then
    then(cacheService).should().put(eq(expectedCommand), argThat(data -> Arrays.equals(data, "value".getBytes())));
    then(parser).should(times(2)).delimitedMode("\r\n");
    then(socket).should().write("formatted-storage-STORED");
  }

  @Test
  void rejects_bytes_before_trailing_crlf_without_storing_value_and_recovers() {
    // given
    given(cacheService.delete("next")).willReturn(true);

    // when
    parserHandler.handle(buffer("set mykey 42 900 5"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer("extra"));
    parserHandler.handle(buffer("delete next"));

    // then
    then(cacheService).should(never()).put(any(), any(byte[].class));
    then(socket).should().write("formatted-error");
    then(cacheService).should().delete("next");
    then(socket).should().write("formatted-deleted");
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
    given(cacheService.put(eq(expectedCommand), any(byte[].class))).willReturn(StoreResult.STORED);

    // when
    parserHandler.handle(buffer("set mykey 7 900 5 noreply"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer());

    // then
    then(cacheService).should().put(eq(expectedCommand), argThat(data -> Arrays.equals(data, "value".getBytes())));
    then(socket).shouldHaveNoMoreInteractions();
  }

  @Test
  void stores_value_and_writes_stored_response_when_add_command_completes() {
    // given
    var expectedCommand = addCommand()
      .key("mykey")
      .flags(42)
      .expTime(900)
      .bytes(5)
      .build();
    given(cacheService.add(eq(expectedCommand), any(byte[].class))).willReturn(StoreResult.STORED);

    // when
    parserHandler.handle(buffer("add mykey 42 900 5"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer());

    // then
    then(cacheService).should().add(eq(expectedCommand), argThat(data -> Arrays.equals(data, "value".getBytes())));
    then(parser).should(times(2)).delimitedMode("\r\n");
    then(socket).should().write("formatted-storage-STORED");
  }

  @Test
  void writes_not_stored_when_add_target_already_exists() {
    // given
    var expectedCommand = addCommand()
      .key("mykey")
      .flags(42)
      .expTime(900)
      .bytes(5)
      .build();
    given(cacheService.add(eq(expectedCommand), any(byte[].class))).willReturn(StoreResult.NOT_STORED);

    // when
    parserHandler.handle(buffer("add mykey 42 900 5"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer());

    // then
    then(cacheService).should().add(eq(expectedCommand), argThat(data -> Arrays.equals(data, "value".getBytes())));
    then(socket).should().write("formatted-storage-NOT_STORED");
  }

  @Test
  void stores_value_without_writing_response_when_add_uses_noreply() {
    // given
    var expectedCommand = addCommand()
      .key("mykey")
      .flags(7)
      .expTime(900)
      .bytes(5)
      .noReply(true)
      .build();
    given(cacheService.add(eq(expectedCommand), any(byte[].class))).willReturn(StoreResult.STORED);

    // when
    parserHandler.handle(buffer("add mykey 7 900 5 noreply"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer());

    // then
    then(cacheService).should().add(eq(expectedCommand), argThat(data -> Arrays.equals(data, "value".getBytes())));
    then(socket).shouldHaveNoMoreInteractions();
  }

  @Test
  void appends_value_and_writes_stored_response_when_append_command_completes() {
    // given
    var expectedCommand = appendCommand()
      .key("mykey")
      .bytes(5)
      .build();
    given(cacheService.append(eq(expectedCommand), any(byte[].class))).willReturn(StoreResult.STORED);

    // when
    parserHandler.handle(buffer("append mykey 42 900 5"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer());

    // then
    then(cacheService).should().append(eq(expectedCommand), argThat(data -> Arrays.equals(data, "value".getBytes())));
    then(parser).should(times(2)).delimitedMode("\r\n");
    then(socket).should().write("formatted-storage-STORED");
  }

  @Test
  void writes_not_stored_when_append_target_is_missing() {
    // given
    var expectedCommand = appendCommand()
      .key("mykey")
      .bytes(5)
      .build();
    given(cacheService.append(eq(expectedCommand), any(byte[].class))).willReturn(StoreResult.NOT_STORED);

    // when
    parserHandler.handle(buffer("append mykey 42 900 5"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer());

    // then
    then(cacheService).should().append(eq(expectedCommand), argThat(data -> Arrays.equals(data, "value".getBytes())));
    then(socket).should().write("formatted-storage-NOT_STORED");
  }

  @Test
  void appends_value_without_writing_response_when_append_uses_noreply() {
    // given
    var expectedCommand = appendCommand()
      .key("mykey")
      .bytes(5)
      .noReply(true)
      .build();
    given(cacheService.append(eq(expectedCommand), any(byte[].class))).willReturn(StoreResult.STORED);

    // when
    parserHandler.handle(buffer("append mykey 7 900 5 noreply"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer());

    // then
    then(cacheService).should().append(eq(expectedCommand), argThat(data -> Arrays.equals(data, "value".getBytes())));
    then(socket).shouldHaveNoMoreInteractions();
  }

  @Test
  void prepends_value_and_writes_stored_response_when_prepend_command_completes() {
    // given
    var expectedCommand = prependCommand()
      .key("mykey")
      .bytes(5)
      .build();
    given(cacheService.prepend(eq(expectedCommand), any(byte[].class))).willReturn(StoreResult.STORED);

    // when
    parserHandler.handle(buffer("prepend mykey 42 900 5"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer());

    // then
    then(cacheService).should().prepend(eq(expectedCommand), argThat(data -> Arrays.equals(data, "value".getBytes())));
    then(parser).should(times(2)).delimitedMode("\r\n");
    then(socket).should().write("formatted-storage-STORED");
  }

  @Test
  void writes_not_stored_when_prepend_target_is_missing() {
    // given
    var expectedCommand = prependCommand()
      .key("mykey")
      .bytes(5)
      .build();
    given(cacheService.prepend(eq(expectedCommand), any(byte[].class))).willReturn(StoreResult.NOT_STORED);

    // when
    parserHandler.handle(buffer("prepend mykey 42 900 5"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer());

    // then
    then(cacheService).should().prepend(eq(expectedCommand), argThat(data -> Arrays.equals(data, "value".getBytes())));
    then(socket).should().write("formatted-storage-NOT_STORED");
  }

  @Test
  void prepends_value_without_writing_response_when_prepend_uses_noreply() {
    // given
    var expectedCommand = prependCommand()
      .key("mykey")
      .bytes(5)
      .noReply(true)
      .build();
    given(cacheService.prepend(eq(expectedCommand), any(byte[].class))).willReturn(StoreResult.STORED);

    // when
    parserHandler.handle(buffer("prepend mykey 7 900 5 noreply"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer());

    // then
    then(cacheService).should().prepend(eq(expectedCommand), argThat(data -> Arrays.equals(data, "value".getBytes())));
    then(socket).shouldHaveNoMoreInteractions();
  }

  @Test
  void stores_value_and_writes_stored_response_when_replace_command_completes() {
    // given
    var expectedCommand = replaceCommand()
      .key("mykey")
      .flags(42)
      .expTime(900)
      .bytes(5)
      .build();
    given(cacheService.replace(eq(expectedCommand), any(byte[].class))).willReturn(StoreResult.STORED);

    // when
    parserHandler.handle(buffer("replace mykey 42 900 5"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer());

    // then
    then(cacheService).should().replace(eq(expectedCommand), argThat(data -> Arrays.equals(data, "value".getBytes())));
    then(parser).should(times(2)).delimitedMode("\r\n");
    then(socket).should().write("formatted-storage-STORED");
  }

  @Test
  void writes_not_stored_when_replace_target_is_missing() {
    // given
    var expectedCommand = replaceCommand()
      .key("mykey")
      .flags(42)
      .expTime(900)
      .bytes(5)
      .build();
    given(cacheService.replace(eq(expectedCommand), any(byte[].class))).willReturn(StoreResult.NOT_STORED);

    // when
    parserHandler.handle(buffer("replace mykey 42 900 5"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer());

    // then
    then(cacheService).should().replace(eq(expectedCommand), argThat(data -> Arrays.equals(data, "value".getBytes())));
    then(socket).should().write("formatted-storage-NOT_STORED");
  }

  @Test
  void stores_value_without_writing_response_when_replace_uses_noreply() {
    // given
    var expectedCommand = replaceCommand()
      .key("mykey")
      .flags(7)
      .expTime(900)
      .bytes(5)
      .noReply(true)
      .build();
    given(cacheService.replace(eq(expectedCommand), any(byte[].class))).willReturn(StoreResult.STORED);

    // when
    parserHandler.handle(buffer("replace mykey 7 900 5 noreply"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer());

    // then
    then(cacheService).should().replace(eq(expectedCommand), argThat(data -> Arrays.equals(data, "value".getBytes())));
    then(socket).shouldHaveNoMoreInteractions();
  }

  @Test
  void stores_value_and_writes_stored_response_when_cas_command_matches() {
    // given
    var expectedCommand = casCommand()
      .key("mykey")
      .flags(42)
      .expTime(900)
      .bytes(5)
      .casUnique(1L)
      .build();
    given(cacheService.cas(eq(expectedCommand), any(byte[].class))).willReturn(StoreResult.STORED);

    // when
    parserHandler.handle(buffer("cas mykey 42 900 5 1"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer());

    // then
    then(cacheService).should().cas(eq(expectedCommand), argThat(data -> Arrays.equals(data, "value".getBytes())));
    then(parser).should(times(2)).delimitedMode("\r\n");
    then(socket).should().write("formatted-storage-STORED");
  }

  @Test
  void writes_exists_when_cas_unique_does_not_match() {
    // given
    var expectedCommand = casCommand()
      .key("mykey")
      .flags(42)
      .expTime(900)
      .bytes(5)
      .casUnique(1L)
      .build();
    given(cacheService.cas(eq(expectedCommand), any(byte[].class))).willReturn(StoreResult.EXISTS);

    // when
    parserHandler.handle(buffer("cas mykey 42 900 5 1"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer());

    // then
    then(cacheService).should().cas(eq(expectedCommand), argThat(data -> Arrays.equals(data, "value".getBytes())));
    then(socket).should().write("formatted-storage-EXISTS");
  }

  @Test
  void writes_not_found_when_cas_target_is_missing() {
    // given
    var expectedCommand = casCommand()
      .key("mykey")
      .flags(42)
      .expTime(900)
      .bytes(5)
      .casUnique(1L)
      .build();
    given(cacheService.cas(eq(expectedCommand), any(byte[].class))).willReturn(StoreResult.NOT_FOUND);

    // when
    parserHandler.handle(buffer("cas mykey 42 900 5 1"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer());

    // then
    then(cacheService).should().cas(eq(expectedCommand), argThat(data -> Arrays.equals(data, "value".getBytes())));
    then(socket).should().write("formatted-storage-NOT_FOUND");
  }

  @Test
  void stores_value_without_writing_response_when_cas_uses_noreply() {
    // given
    var expectedCommand = casCommand()
      .key("mykey")
      .flags(7)
      .expTime(900)
      .bytes(5)
      .casUnique(1L)
      .noReply(true)
      .build();
    given(cacheService.cas(eq(expectedCommand), any(byte[].class))).willReturn(StoreResult.STORED);

    // when
    parserHandler.handle(buffer("cas mykey 7 900 5 1 noreply"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer());

    // then
    then(cacheService).should().cas(eq(expectedCommand), argThat(data -> Arrays.equals(data, "value".getBytes())));
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
    given(cacheService.put(eq(expectedCommand), any(byte[].class))).willReturn(StoreResult.STORED);

    // when
    parserHandler.handle(buffer("set mykey 0 900"));
    parserHandler.handle(buffer("set next 9 60 5"));
    parserHandler.handle(buffer("hello"));
    parserHandler.handle(buffer());

    // then
    then(socket).should().write("formatted-error");
    then(socket).should().write("formatted-storage-STORED");
    then(parser).should(times(3)).delimitedMode("\r\n");
    then(parser).should().fixedSizeMode(5);
    then(cacheService).should().put(eq(expectedCommand), argThat(data -> Arrays.equals(data, "hello".getBytes())));
  }

  @Test
  void writes_bare_error_and_recovers_after_unknown_command() {
    // given
    given(cacheService.delete("mykey")).willReturn(true);

    // when
    parserHandler.handle(buffer("unknown"));
    parserHandler.handle(buffer("delete mykey"));

    // then
    then(socket).should().write("formatted-error");
    then(cacheService).should().delete("mykey");
    then(socket).should().write("formatted-deleted");
    then(parser).should(times(1)).delimitedMode("\r\n");
  }

  @Test
  void writes_error_and_recovers_after_invalid_add_command() {
    // given
    var expectedCommand = addCommand()
      .key("next")
      .flags(9)
      .expTime(60)
      .bytes(5)
      .build();
    given(cacheService.add(eq(expectedCommand), any(byte[].class))).willReturn(StoreResult.STORED);

    // when
    parserHandler.handle(buffer("add mykey 0 900"));
    parserHandler.handle(buffer("add next 9 60 5"));
    parserHandler.handle(buffer("hello"));
    parserHandler.handle(buffer());

    // then
    then(socket).should().write("formatted-error");
    then(socket).should().write("formatted-storage-STORED");
    then(parser).should(times(3)).delimitedMode("\r\n");
    then(parser).should().fixedSizeMode(5);
    then(cacheService).should().add(eq(expectedCommand), argThat(data -> Arrays.equals(data, "hello".getBytes())));
  }

  @Test
  void writes_error_and_recovers_after_invalid_replace_command() {
    // given
    var expectedCommand = replaceCommand()
      .key("next")
      .flags(9)
      .expTime(60)
      .bytes(5)
      .build();
    given(cacheService.replace(eq(expectedCommand), any(byte[].class))).willReturn(StoreResult.STORED);

    // when
    parserHandler.handle(buffer("replace mykey 0 900"));
    parserHandler.handle(buffer("replace next 9 60 5"));
    parserHandler.handle(buffer("hello"));
    parserHandler.handle(buffer());

    // then
    then(socket).should().write("formatted-error");
    then(socket).should().write("formatted-storage-STORED");
    then(parser).should(times(3)).delimitedMode("\r\n");
    then(parser).should().fixedSizeMode(5);
    then(cacheService).should().replace(eq(expectedCommand), argThat(data -> Arrays.equals(data, "hello".getBytes())));
  }

  @Test
  void writes_error_and_recovers_after_value_size_exceeds_maximum() {
    // given
    given(cacheService.delete("mykey")).willReturn(true);

    // when
    parserHandler.handle(buffer("set mykey 0 900 " + (MAX_VALUE_BYTES + 1)));
    parserHandler.handle(buffer("delete mykey"));

    // then
    then(socket).should().write("formatted-error");
    then(cacheService).should().delete("mykey");
    then(socket).should().write("formatted-deleted");
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
    then(socket).should().write("formatted-end");
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
    then(socket).should().write("formatted-error");
    then(cacheService).should().delete("mykey");
    then(socket).should().write("formatted-deleted");
    then(parser).should(times(1)).delimitedMode("\r\n");
  }

  @Test
  void writes_values_for_present_get_keys_in_request_order_and_ends_response() {
    // given
    var keys = List.of("second", "missing", "first");
    var entries = Map.of(
      "first", cacheEntry()
        .flags(1)
        .ttl(Duration.ofSeconds(60))
        .data("one".getBytes())
        .casUnique(41L)
        .build(),
      "second", cacheEntry()
        .flags(2)
        .ttl(Duration.ofSeconds(60))
        .data("two".getBytes())
        .casUnique(42L)
        .build()
    );
    given(cacheService.getAllPresent(keys)).willReturn(entries);

    // when
    parserHandler.handle(buffer("get second missing first"));

    // then
    then(cacheService).should().getAllPresent(keys);
    var inOrder = inOrder(socket);
    inOrder.verify(socket).write("formatted-value-line-second-false");
    inOrder.verify(socket).write(buffer("two"));
    inOrder.verify(socket).write("formatted-crlf");
    inOrder.verify(socket).write("formatted-value-line-first-false");
    inOrder.verify(socket).write(buffer("one"));
    inOrder.verify(socket).write("formatted-crlf");
    inOrder.verify(socket).write("formatted-end");
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void writes_values_with_cas_unique_for_present_gets_keys_in_request_order_and_ends_response() {
    // given
    var keys = List.of("second", "missing", "first");
    var entries = Map.of(
      "first", cacheEntry()
        .flags(1)
        .ttl(Duration.ofSeconds(60))
        .data("one".getBytes())
        .casUnique(41L)
        .build(),
      "second", cacheEntry()
        .flags(2)
        .ttl(Duration.ofSeconds(60))
        .data("two".getBytes())
        .casUnique(-1L)
        .build()
    );
    given(cacheService.getAllPresent(keys)).willReturn(entries);

    // when
    parserHandler.handle(buffer("gets second missing first"));

    // then
    then(cacheService).should().getAllPresent(keys);
    var inOrder = inOrder(socket);
    inOrder.verify(socket).write("formatted-value-line-second-true");
    inOrder.verify(socket).write(buffer("two"));
    inOrder.verify(socket).write("formatted-crlf");
    inOrder.verify(socket).write("formatted-value-line-first-true");
    inOrder.verify(socket).write(buffer("one"));
    inOrder.verify(socket).write("formatted-crlf");
    inOrder.verify(socket).write("formatted-end");
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
    then(socket).should().write("formatted-deleted");
  }

  @Test
  void writes_not_found_when_delete_target_is_missing() {
    // given
    given(cacheService.delete("missing")).willReturn(false);

    // when
    parserHandler.handle(buffer("delete missing"));

    // then
    then(cacheService).should().delete("missing");
    then(socket).should().write("formatted-delete-not-found");
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

  @Test
  void touches_existing_key_and_writes_touched_response() {
    // given
    var command = new TouchCommand("mykey", 60, false);
    given(cacheService.touch(command)).willReturn(true);

    // when
    parserHandler.handle(buffer("touch mykey 60"));

    // then
    then(cacheService).should().touch(command);
    then(socket).should().write("formatted-touched");
    then(parser).should(never()).fixedSizeMode(anyInt());
  }

  @Test
  void writes_not_found_when_touch_target_is_missing() {
    // given
    var command = new TouchCommand("missing", 60, false);
    given(cacheService.touch(command)).willReturn(false);

    // when
    parserHandler.handle(buffer("touch missing 60"));

    // then
    then(cacheService).should().touch(command);
    then(socket).should().write("formatted-touch-not-found");
  }

  @Test
  void touches_without_writing_response_when_touch_uses_noreply() {
    // given
    var command = new TouchCommand("quiet", 60, true);
    given(cacheService.touch(command)).willReturn(true);

    // when
    parserHandler.handle(buffer("touch quiet 60 noreply"));

    // then
    then(cacheService).should().touch(command);
    then(socket).shouldHaveNoMoreInteractions();
  }

  @Test
  void increments_existing_key_and_writes_updated_value() {
    // given
    var command = new IncrCommand("mykey", 2L, false);
    given(cacheService.increment(command)).willReturn(OptionalLong.of(42L));

    // when
    parserHandler.handle(buffer("incr mykey 2"));

    // then
    then(cacheService).should().increment(command);
    then(socket).should().write("formatted-numeric-42");
    then(parser).should(never()).fixedSizeMode(anyInt());
  }

  @Test
  void writes_unsigned_updated_value_when_result_exceeds_signed_long_range() {
    // given
    var command = new IncrCommand("mykey", 1L, false);
    given(cacheService.increment(command)).willReturn(OptionalLong.of(-2L));

    // when
    parserHandler.handle(buffer("incr mykey 1"));

    // then
    then(cacheService).should().increment(command);
    then(socket).should().write("formatted-numeric-unsigned");
  }

  @Test
  void writes_not_found_when_incr_target_is_missing() {
    // given
    var command = new IncrCommand("missing", 2L, false);
    given(cacheService.increment(command)).willReturn(OptionalLong.empty());

    // when
    parserHandler.handle(buffer("incr missing 2"));

    // then
    then(cacheService).should().increment(command);
    then(socket).should().write("formatted-numeric-not-found");
  }

  @Test
  void increments_without_writing_response_when_incr_uses_noreply() {
    // given
    var command = new IncrCommand("quiet", 2L, true);
    given(cacheService.increment(command)).willReturn(OptionalLong.of(42L));

    // when
    parserHandler.handle(buffer("incr quiet 2 noreply"));

    // then
    then(cacheService).should().increment(command);
    then(socket).shouldHaveNoMoreInteractions();
  }

  @Test
  void decrements_existing_key_and_writes_updated_value() {
    // given
    var command = new DecrCommand("mykey", 2L, false);
    given(cacheService.decrement(command)).willReturn(OptionalLong.of(40L));

    // when
    parserHandler.handle(buffer("decr mykey 2"));

    // then
    then(cacheService).should().decrement(command);
    then(socket).should().write("formatted-numeric-40");
    then(parser).should(never()).fixedSizeMode(anyInt());
  }

  @Test
  void writes_not_found_when_decr_target_is_missing() {
    // given
    var command = new DecrCommand("missing", 2L, false);
    given(cacheService.decrement(command)).willReturn(OptionalLong.empty());

    // when
    parserHandler.handle(buffer("decr missing 2"));

    // then
    then(cacheService).should().decrement(command);
    then(socket).should().write("formatted-numeric-not-found");
  }

  @Test
  void decrements_without_writing_response_when_decr_uses_noreply() {
    // given
    var command = new DecrCommand("quiet", 2L, true);
    given(cacheService.decrement(command)).willReturn(OptionalLong.of(40L));

    // when
    parserHandler.handle(buffer("decr quiet 2 noreply"));

    // then
    then(cacheService).should().decrement(command);
    then(socket).shouldHaveNoMoreInteractions();
  }

  @Test
  void writes_error_and_recovers_after_invalid_incr_target_value() {
    // given
    var failed = new IncrCommand("counter", 1L, false);
    given(cacheService.increment(failed)).willThrow(new ssonin.ccmemcached.protocol.error.ClientError("value is not a valid unsigned integer"));
    given(cacheService.delete("mykey")).willReturn(true);

    // when
    parserHandler.handle(buffer("incr counter 1"));
    parserHandler.handle(buffer("delete mykey"));

    // then
    then(socket).should().write("formatted-error");
    then(cacheService).should().delete("mykey");
    then(socket).should().write("formatted-deleted");
  }

  @Test
  void writes_server_error_and_closes_connection_after_unexpected_immediate_command_failure() {
    // given
    given(cacheService.delete("mykey")).willThrow(new IllegalStateException("sensitive details"));

    // when
    parserHandler.handle(buffer("delete mykey"));

    // then
    then(socket).should().end(buffer("formatted-server-error"));
    then(socket).shouldHaveNoMoreInteractions();
  }

  @Test
  void writes_server_error_and_closes_connection_after_unexpected_storage_command_failure() {
    // given
    var command = setCommand()
      .key("mykey")
      .flags(7)
      .expTime(60)
      .bytes(5)
      .build();
    willThrow(new IllegalStateException("sensitive details"))
      .given(cacheService).put(eq(command), any(byte[].class));

    // when
    parserHandler.handle(buffer("set mykey 7 60 5"));
    parserHandler.handle(buffer("value"));
    parserHandler.handle(buffer());

    // then
    then(socket).should().end(buffer("formatted-server-error"));
    then(socket).shouldHaveNoMoreInteractions();
  }

  private RecordParser parser() {
    var parser = Mockito.mock(RecordParser.class);
    willAnswer(invocation -> {
      parserExceptionHandler = invocation.getArgument(0);
      return parser;
    }).given(parser).exceptionHandler(any());
    willAnswer(invocation -> {
      parserHandler = invocation.getArgument(0);
      return parser;
    }).given(parser).handler(any());
    return parser;
  }

  private void stubResponseFormatter() {
    willAnswer(invocation -> "formatted-error")
      .given(responseFormatter).error(any(ApplicationError.class));
    given(responseFormatter.error(eq(SERVER_ERROR), eq("internal server error"))).willReturn("formatted-server-error");

    willAnswer(invocation -> "formatted-storage-" + ((StoreResult) invocation.getArgument(0)).name())
      .given(responseFormatter).storage(any(StoreResult.class));
    given(responseFormatter.deleted(true)).willReturn("formatted-deleted");
    given(responseFormatter.deleted(false)).willReturn("formatted-delete-not-found");
    given(responseFormatter.touched(true)).willReturn("formatted-touched");
    given(responseFormatter.touched(false)).willReturn("formatted-touch-not-found");

    given(responseFormatter.numeric(OptionalLong.of(42L))).willReturn("formatted-numeric-42");
    given(responseFormatter.numeric(OptionalLong.of(-2L))).willReturn("formatted-numeric-unsigned");
    given(responseFormatter.numeric(OptionalLong.of(40L))).willReturn("formatted-numeric-40");
    given(responseFormatter.numeric(OptionalLong.empty())).willReturn("formatted-numeric-not-found");

    willAnswer(invocation -> "formatted-value-line-%s-%s".formatted(invocation.getArgument(0), invocation.getArgument(2)))
      .given(responseFormatter).valueLine(any(String.class), any(CacheEntry.class), anyBoolean());
    given(responseFormatter.crlf()).willReturn("formatted-crlf");
    given(responseFormatter.end()).willReturn("formatted-end");
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
