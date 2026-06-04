package ssonin.ccmemcached;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.testing.FakeTicker;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssonin.ccmemcached.cache.CacheEntryExpiry;
import ssonin.ccmemcached.cache.CacheService;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncrCommandIntegrationTest {

  private Vertx vertx;
  private CacheService cacheService;
  private FakeTicker ticker;
  private int port;

  @BeforeEach
  void setUp() throws Exception {
    vertx = Vertx.vertx();
    ticker = new FakeTicker();
    cacheService = new CacheService(
      Caffeine.newBuilder()
        .expireAfter(new CacheEntryExpiry())
        .executor(Runnable::run)
        .ticker(ticker::read)
        .build(),
      () -> Instant.ofEpochMilli(ticker.read() / 1_000_000L)
    );
    var app = new App(() -> cacheService);
    await(vertx.deployVerticle(
      app,
      new DeploymentOptions().setConfig(new JsonObject().put("http.port", 0))
    ));
    port = app.actualPort();
  }

  @AfterEach
  void tearDown() {
    if (vertx != null) {
      await(vertx.close());
    }
  }

  @Test
  void incr_increments_existing_numeric_value_and_value_is_retrievable_via_get() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "counter", 7, 60, "41");

      // when
      writeAscii(client, "incr counter 1\r\n");

      // then
      assertThat(readLine(client)).isEqualTo("42\r\n");

      // when
      writeAscii(client, "get counter\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo("VALUE counter 7 2\r\n42\r\nEND\r\n");
    }
  }

  @Test
  void incr_returns_not_found_for_missing_key() throws Exception {
    try (var client = connect()) {
      // when
      writeAscii(client, "incr missing 1\r\n");

      // then
      assertThat(readLine(client)).isEqualTo("NOT_FOUND\r\n");
    }
  }

  @Test
  void incr_with_noreply_updates_value_without_immediate_response() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "counter", 7, 60, "41");

      // when
      writeAscii(client, "incr counter 1 noreply\r\n");

      // then
      client.setSoTimeout(200);
      assertThatThrownBy(() -> client.getInputStream().read())
        .isInstanceOf(SocketTimeoutException.class);

      // when
      client.setSoTimeout(2000);
      writeAscii(client, "get counter\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo("VALUE counter 7 2\r\n42\r\nEND\r\n");
    }
  }

  @Test
  void incr_rejects_non_numeric_stored_value_and_keeps_connection_usable() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "counter", 7, 60, "value");

      // when
      writeAscii(client, "incr counter 1\r\n");

      // then
      assertThat(readLine(client)).isEqualTo("CLIENT_ERROR value is not a valid unsigned integer\r\n");

      // when
      sendSet(client, "next", 9, 60, "hello");
      writeAscii(client, "get next\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo("VALUE next 9 5\r\nhello\r\nEND\r\n");
    }
  }

  @Test
  void incr_wraps_unsigned_64_bit_overflow() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "counter", 7, 60, "18446744073709551615");

      // when
      writeAscii(client, "incr counter 1\r\n");

      // then
      assertThat(readLine(client)).isEqualTo("0\r\n");

      // when
      writeAscii(client, "get counter\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo("VALUE counter 7 1\r\n0\r\nEND\r\n");
    }
  }

  @Test
  void incr_does_not_extend_existing_ttl() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "counter", 7, 1, "1");
      ticker.advance(800, TimeUnit.MILLISECONDS);

      // when
      writeAscii(client, "incr counter 1\r\n");

      // then
      assertThat(readLine(client)).isEqualTo("2\r\n");

      // when
      ticker.advance(300, TimeUnit.MILLISECONDS);
      cacheService.cleanUp();
      writeAscii(client, "get counter\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo("END\r\n");
    }
  }

  @Test
  void touch_then_incr_does_not_extend_touched_ttl_again() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "counter", 7, 1, "1");
      writeAscii(client, "touch counter 2\r\n");
      assertThat(readLine(client)).isEqualTo("TOUCHED\r\n");
      ticker.advance(1500, TimeUnit.MILLISECONDS);
      cacheService.cleanUp();

      // when
      writeAscii(client, "incr counter 1\r\n");

      // then
      assertThat(readLine(client)).isEqualTo("2\r\n");

      // when
      ticker.advance(600, TimeUnit.MILLISECONDS);
      cacheService.cleanUp();
      writeAscii(client, "get counter\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo("END\r\n");
    }
  }

  private Socket connect() throws IOException {
    return new Socket("127.0.0.1", port);
  }

  private void sendSet(Socket client, String key, int flags, int exptime, String value) throws IOException {
    writeAscii(client, "set %s %d %d %d\r\n%s\r\n".formatted(key, flags, exptime, value.length(), value));
    assertThat(readLine(client)).isEqualTo("STORED\r\n");
  }

  private void writeAscii(Socket client, String command) throws IOException {
    var output = client.getOutputStream();
    output.write(command.getBytes(UTF_8));
    output.flush();
  }

  private String readUntilEnd(Socket client) throws IOException {
    var input = client.getInputStream();
    var buffer = new StringBuilder();
    while (!buffer.toString().endsWith("END\r\n")) {
      var next = input.read();
      if (next == -1) {
        throw new IOException("connection closed before END");
      }
      buffer.append((char) next);
    }
    return buffer.toString();
  }

  private String readLine(Socket client) throws IOException {
    var input = client.getInputStream();
    var buffer = new StringBuilder();
    while (!buffer.toString().endsWith("\r\n")) {
      var next = input.read();
      if (next == -1) {
        throw new IOException("connection closed before CRLF");
      }
      buffer.append((char) next);
    }
    return buffer.toString();
  }

  private static <T> T await(io.vertx.core.Future<T> future) {
    return future.toCompletionStage().toCompletableFuture().join();
  }
}
