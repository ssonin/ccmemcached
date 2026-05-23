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

class SetCommandIntegrationTest {

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
  void set_persists_value_retrievable_via_get() throws Exception {
    try (var client = connect()) {
      // when
      sendSet(client, "mykey", 7, 60, "value");
      writeAscii(client, "get mykey\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo(normalizeCrlf("""
        VALUE mykey 7 5
        value
        END
        """));
    }
  }

  @Test
  void set_overwrites_existing_value() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "mykey", 1, 60, "first");

      // when
      sendSet(client, "mykey", 2, 60, "second");
      writeAscii(client, "get mykey\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo(normalizeCrlf("""
        VALUE mykey 2 6
        second
        END
        """));
    }
  }

  @Test
  void set_overwrite_resets_existing_ttl() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "mykey", 1, 1, "first");
      ticker.advance(800, TimeUnit.MILLISECONDS);

      // when
      sendSet(client, "mykey", 2, 60, "second");
      ticker.advance(300, TimeUnit.MILLISECONDS);
      cacheService.cleanUp();
      writeAscii(client, "get mykey\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo(normalizeCrlf("""
        VALUE mykey 2 6
        second
        END
        """));
    }
  }

  @Test
  void set_with_noreply_stores_value_without_immediate_response() throws Exception {
    try (var client = connect()) {
      // when
      writeAscii(client, "set quiet 3 60 5 noreply\r\nvalue\r\n");

      // then
      client.setSoTimeout(200);
      assertThatThrownBy(() -> client.getInputStream().read())
        .isInstanceOf(SocketTimeoutException.class);

      // when
      client.setSoTimeout(2000);
      writeAscii(client, "get quiet\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo(normalizeCrlf("""
        VALUE quiet 3 5
        value
        END
        """));
    }
  }

  @Test
  void set_rejects_invalid_command_and_keeps_connection_usable() throws Exception {
    try (var client = connect()) {
      // when
      writeAscii(client, "set broken 0 60\r\n");

      // then
      assertThat(readLine(client)).isEqualTo("CLIENT_ERROR: expected at least 5 fields, got 4\r\n");

      // when
      sendSet(client, "next", 9, 60, "hello");
      writeAscii(client, "get next\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo(normalizeCrlf("""
        VALUE next 9 5
        hello
        END
        """));
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

  private String normalizeCrlf(String response) {
    return response.replace("\n", "\r\n");
  }

  private static <T> T await(io.vertx.core.Future<T> future) {
    return future.toCompletionStage().toCompletableFuture().join();
  }
}
