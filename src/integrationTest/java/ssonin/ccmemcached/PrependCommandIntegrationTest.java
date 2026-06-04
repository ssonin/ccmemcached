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

class PrependCommandIntegrationTest {

  private static final int MAX_VALUE_BYTES = 1024 * 1024;

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
  void prepend_existing_key_concatenates_value_and_preserves_flags() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "mykey", 7, 60, "second");

      // when
      writeAscii(client, "prepend mykey 99 120 5\r\nfirst\r\n");

      // then
      assertThat(readLine(client)).isEqualTo("STORED\r\n");
      writeAscii(client, "get mykey\r\n");
      assertThat(readUntilEnd(client)).isEqualTo(normalizeCrlf("""
        VALUE mykey 7 11
        firstsecond
        END
        """));
    }
  }

  @Test
  void prepend_returns_not_stored_when_key_is_missing() throws Exception {
    try (var client = connect()) {
      // when
      writeAscii(client, "prepend missing 1 60 5\r\nvalue\r\n");

      // then
      assertThat(readLine(client)).isEqualTo("NOT_STORED\r\n");
    }
  }

  @Test
  void prepend_with_noreply_stores_value_without_immediate_response() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "quiet", 3, 60, "second");

      // when
      writeAscii(client, "prepend quiet 9 120 5 noreply\r\nfirst\r\n");

      // then
      client.setSoTimeout(200);
      assertThatThrownBy(() -> client.getInputStream().read())
        .isInstanceOf(SocketTimeoutException.class);

      // when
      client.setSoTimeout(2000);
      writeAscii(client, "get quiet\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo(normalizeCrlf("""
        VALUE quiet 3 11
        firstsecond
        END
        """));
    }
  }

  @Test
  void successful_prepend_advances_cas_unique() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "mykey", 1, 60, "second");

      // when
      writeAscii(client, "prepend mykey 1 60 5\r\nfirst\r\n");
      assertThat(readLine(client)).isEqualTo("STORED\r\n");
      writeAscii(client, "gets mykey\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo("VALUE mykey 1 11 2\r\nfirstsecond\r\nEND\r\n");
    }
  }

  @Test
  void successful_prepend_does_not_extend_existing_ttl() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "mykey", 1, 1, "second");
      ticker.advance(800, TimeUnit.MILLISECONDS);

      // when
      writeAscii(client, "prepend mykey 2 60 5\r\nfirst\r\n");
      assertThat(readLine(client)).isEqualTo("STORED\r\n");
      ticker.advance(300, TimeUnit.MILLISECONDS);
      cacheService.cleanUp();
      writeAscii(client, "get mykey\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo("END\r\n");
    }
  }

  @Test
  void prepend_returns_not_stored_when_combined_value_exceeds_maximum_size() throws Exception {
    try (var client = connect()) {
      // given
      var value = "a".repeat(MAX_VALUE_BYTES);
      sendSet(client, "mykey", 1, 60, value);

      // when
      writeAscii(client, "prepend mykey 1 60 1\r\nb\r\n");

      // then
      assertThat(readLine(client)).isEqualTo("NOT_STORED\r\n");
      writeAscii(client, "delete mykey\r\n");
      assertThat(readLine(client)).isEqualTo("DELETED\r\n");
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
