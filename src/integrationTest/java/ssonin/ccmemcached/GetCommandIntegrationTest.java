package ssonin.ccmemcached;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.testing.FakeTicker;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssonin.ccmemcached.cache.CacheEntry;
import ssonin.ccmemcached.cache.CacheService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static com.github.benmanes.caffeine.cache.Expiry.creating;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class GetCommandIntegrationTest {

  private static final byte[] BINARY_SAFE_VALUE = "line 1\nline 2".getBytes(UTF_8);

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
        .expireAfter(creating((String key, CacheEntry entry) -> entry.ttl()))
        .executor(Runnable::run)
        .ticker(ticker::read)
        .build(),
      () -> Instant.ofEpochMilli(ticker.read() / 1_000_000L)
    );
    port = findFreePort();
    await(vertx.deployVerticle(
      new App(() -> cacheService),
      new DeploymentOptions().setConfig(new JsonObject().put("http.port", port))
    ));
  }

  @AfterEach
  void tearDown() {
    if (vertx != null) {
      await(vertx.close());
    }
  }

  @Test
  void get_returns_single_hit() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "alpha", 7, 30, "value".getBytes(UTF_8));

      // when
      writeAscii(client, "get alpha\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo(normalizeCrlf("""
        VALUE alpha 7 5
        value
        END
        """));
    }
  }

  @Test
  void get_returns_end_for_single_miss() throws Exception {
    try (var client = connect()) {
      // when
      writeAscii(client, "get missing\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo("END\r\n");
    }
  }

  @Test
  void get_returns_found_keys_in_request_order_and_skips_misses() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "alpha", 1, 30, "one".getBytes(UTF_8));
      sendSet(client, "beta", 2, 30, "two".getBytes(UTF_8));

      // when
      writeAscii(client, "get beta missing alpha\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo(normalizeCrlf("""
        VALUE beta 2 3
        two
        VALUE alpha 1 3
        one
        END
        """));
    }
  }

  @Test
  void get_returns_binary_safe_payload_without_transformation() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "blob", 9, 30, BINARY_SAFE_VALUE);

      // when
      writeAscii(client, "get blob\r\n");

      // then
      assertThat(readLine(client)).isEqualTo("VALUE blob 9 13\r\n");
      assertThat(readExact(client, BINARY_SAFE_VALUE.length)).isEqualTo(BINARY_SAFE_VALUE);
      assertThat(readAscii(client, 2)).isEqualTo("\r\n");
      assertThat(readLine(client)).isEqualTo("END\r\n");
    }
  }

  @Test
  void get_omits_entry_after_relative_ttl_expires() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "short", 0, 10, "ttl".getBytes(UTF_8));

      // when
      ticker.advance(11, TimeUnit.SECONDS);
      cacheService.cleanUp();
      writeAscii(client, "get short\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo("END\r\n");
    }
  }

  @Test
  void get_keeps_entry_when_exptime_is_zero_even_after_large_time_advance() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "forever", 5, 0, "alive".getBytes(UTF_8));

      // when
      ticker.advance(3650, TimeUnit.DAYS);
      cacheService.cleanUp();
      writeAscii(client, "get forever\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo(normalizeCrlf("""
        VALUE forever 5 5
        alive
        END
        """));
    }
  }

  @Test
  void get_returns_duplicate_keys_as_requested() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "alpha", 1, 30, "one".getBytes(UTF_8));
      sendSet(client, "beta", 2, 30, "two".getBytes(UTF_8));

      // when
      writeAscii(client, "get alpha beta alpha\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo(normalizeCrlf("""
        VALUE alpha 1 3
        one
        VALUE beta 2 3
        two
        VALUE alpha 1 3
        one
        END
        """));
    }
  }

  private Socket connect() throws IOException {
    return new Socket("127.0.0.1", port);
  }

  private void sendSet(Socket client, String key, int flags, int exptime, byte[] value) throws Exception {
    var output = client.getOutputStream();
    output.write(("set %s %d %d %d\r\n".formatted(key, flags, exptime, value.length)).getBytes(UTF_8));
    output.write(value);
    output.write("\r\n".getBytes(UTF_8));
    output.flush();
    assertThat(readAscii(client, 8)).isEqualTo("STORED\r\n");
  }

  private void writeAscii(Socket client, String command) throws IOException {
    var output = client.getOutputStream();
    output.write(command.getBytes(UTF_8));
    output.flush();
  }

  private String readUntilEnd(Socket client) throws IOException {
    return new String(readUntilEndBytes(client), UTF_8);
  }

  private String normalizeCrlf(String response) {
    return response.replace("\n", "\r\n");
  }

  private byte[] readUntilEndBytes(Socket client) throws IOException {
    var input = client.getInputStream();
    var buffer = new java.io.ByteArrayOutputStream();
    while (true) {
      var next = input.read();
      if (next == -1) {
        throw new IOException("connection closed before END");
      }
      buffer.write(next);
      if (endsWithEnd(buffer.toByteArray())) {
        return buffer.toByteArray();
      }
    }
  }

  private boolean endsWithEnd(byte[] bytes) {
    var suffix = "END\r\n".getBytes(UTF_8);
    if (bytes.length < suffix.length) {
      return false;
    }
    for (int i = 0; i < suffix.length; i++) {
      if (bytes[bytes.length - suffix.length + i] != suffix[i]) {
        return false;
      }
    }
    return true;
  }

  private byte[] readExact(Socket client, int length) throws IOException {
    var input = client.getInputStream();
    var data = input.readNBytes(length);
    if (data.length != length) {
      throw new IOException("expected %d bytes, got %d".formatted(length, data.length));
    }
    return data;
  }

  private String readAscii(Socket client, int length) throws IOException {
    return new String(readExact(client, length), UTF_8);
  }

  private String readLine(Socket client) throws IOException {
    var input = client.getInputStream();
    var buffer = new java.io.ByteArrayOutputStream();
    while (true) {
      var next = input.read();
      if (next == -1) {
        throw new IOException("connection closed before CRLF");
      }
      buffer.write(next);
      var bytes = buffer.toByteArray();
      if (bytes.length >= 2 && bytes[bytes.length - 2] == '\r' && bytes[bytes.length - 1] == '\n') {
        return buffer.toString(UTF_8);
      }
    }
  }

  private <T> T await(io.vertx.core.Future<T> future) {
    return future.toCompletionStage().toCompletableFuture().join();
  }

  private int findFreePort() throws IOException {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
