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
import java.time.Instant;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class GetsCommandIntegrationTest {

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
  void gets_returns_single_hit_with_cas_unique() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "alpha", 7, 30, "value");

      // when
      writeAscii(client, "gets alpha\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo(normalizeCrlf("""
        VALUE alpha 7 5 1
        value
        END
        """));
    }
  }

  @Test
  void gets_returns_end_for_single_miss() throws Exception {
    try (var client = connect()) {
      // when
      writeAscii(client, "gets missing\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo("END\r\n");
    }
  }

  @Test
  void gets_returns_found_keys_in_request_order_and_skips_misses() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "alpha", 1, 30, "one");
      sendSet(client, "beta", 2, 30, "two");

      // when
      writeAscii(client, "gets beta missing alpha\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo(normalizeCrlf("""
        VALUE beta 2 3 2
        two
        VALUE alpha 1 3 1
        one
        END
        """));
    }
  }

  @Test
  void gets_returns_duplicate_keys_as_requested() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "alpha", 1, 30, "one");
      sendSet(client, "beta", 2, 30, "two");

      // when
      writeAscii(client, "gets alpha beta alpha\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo(normalizeCrlf("""
        VALUE alpha 1 3 1
        one
        VALUE beta 2 3 2
        two
        VALUE alpha 1 3 1
        one
        END
        """));
    }
  }

  @Test
  void gets_cas_unique_is_stable_until_value_changes() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "counter", 7, 30, "41");

      // when
      writeAscii(client, "gets counter\r\n");
      var first = readUntilEnd(client);
      writeAscii(client, "gets counter\r\n");
      var second = readUntilEnd(client);
      writeAscii(client, "incr counter 1\r\n");
      assertThat(readLine(client)).isEqualTo("42\r\n");
      writeAscii(client, "gets counter\r\n");

      // then
      assertThat(first).isEqualTo("VALUE counter 7 2 1\r\n41\r\nEND\r\n");
      assertThat(second).isEqualTo(first);
      assertThat(readUntilEnd(client)).isEqualTo("VALUE counter 7 2 2\r\n42\r\nEND\r\n");
    }
  }

  @Test
  void touch_does_not_change_cas_unique() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "alpha", 7, 30, "value");

      // when
      writeAscii(client, "touch alpha 60\r\n");
      assertThat(readLine(client)).isEqualTo("TOUCHED\r\n");
      writeAscii(client, "gets alpha\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo("VALUE alpha 7 5 1\r\nvalue\r\nEND\r\n");
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
