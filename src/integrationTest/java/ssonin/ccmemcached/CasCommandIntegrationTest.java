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

class CasCommandIntegrationTest {

  private Vertx vertx;
  private CacheService cacheService;
  private FakeTicker ticker;
  private int port;

  @BeforeEach
  void setUp() {
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
  void cas_with_current_token_stores_new_value() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "mykey", 1, 60, "first");

      // when
      writeAscii(client, "cas mykey 2 60 6 1\r\nsecond\r\n");

      // then
      assertThat(readLine(client)).isEqualTo("STORED\r\n");
      writeAscii(client, "get mykey\r\n");
      assertThat(readUntilEnd(client)).isEqualTo(normalizeCrlf("""
        VALUE mykey 2 6
        second
        END
        """));
    }
  }

  @Test
  void cas_with_stale_token_returns_exists_and_preserves_value() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "mykey", 1, 60, "first");
      sendSet(client, "mykey", 2, 60, "second");

      // when
      writeAscii(client, "cas mykey 3 60 5 1\r\nthird\r\n");

      // then
      assertThat(readLine(client)).isEqualTo("EXISTS\r\n");
      writeAscii(client, "get mykey\r\n");
      assertThat(readUntilEnd(client)).isEqualTo(normalizeCrlf("""
        VALUE mykey 2 6
        second
        END
        """));
    }
  }

  @Test
  void cas_returns_not_found_when_key_is_missing() throws Exception {
    try (var client = connect()) {
      // when
      writeAscii(client, "cas missing 1 60 5 1\r\nvalue\r\n");

      // then
      assertThat(readLine(client)).isEqualTo("NOT_FOUND\r\n");
    }
  }

  @Test
  void successful_cas_advances_cas_unique() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "mykey", 1, 60, "first");

      // when
      writeAscii(client, "cas mykey 2 60 6 1\r\nsecond\r\n");
      assertThat(readLine(client)).isEqualTo("STORED\r\n");
      writeAscii(client, "gets mykey\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo("VALUE mykey 2 6 2\r\nsecond\r\nEND\r\n");
    }
  }

  @Test
  void failed_cas_does_not_advance_cas_unique() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "mykey", 1, 60, "first");

      // when
      writeAscii(client, "cas mykey 2 60 6 999\r\nsecond\r\n");
      assertThat(readLine(client)).isEqualTo("EXISTS\r\n");
      writeAscii(client, "gets mykey\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo("VALUE mykey 1 5 1\r\nfirst\r\nEND\r\n");
    }
  }

  @Test
  void cas_with_noreply_stores_value_without_immediate_response() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "mykey", 1, 60, "first");

      // when
      writeAscii(client, "cas mykey 2 60 6 1 noreply\r\nsecond\r\n");

      // then
      client.setSoTimeout(200);
      assertThatThrownBy(() -> client.getInputStream().read())
        .isInstanceOf(SocketTimeoutException.class);

      // when
      client.setSoTimeout(2000);
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
  void failed_cas_does_not_extend_existing_ttl() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "mykey", 1, 1, "first");
      ticker.advance(800, TimeUnit.MILLISECONDS);

      // when
      writeAscii(client, "cas mykey 2 60 6 999\r\nsecond\r\n");
      assertThat(readLine(client)).isEqualTo("EXISTS\r\n");
      ticker.advance(300, TimeUnit.MILLISECONDS);
      cacheService.cleanUp();
      writeAscii(client, "get mykey\r\n");

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

  private String normalizeCrlf(String response) {
    return response.replace("\n", "\r\n");
  }

  private static <T> T await(io.vertx.core.Future<T> future) {
    return future.toCompletionStage().toCompletableFuture().join();
  }
}
