package ssonin.ccmemcached;

import com.github.benmanes.caffeine.cache.Caffeine;
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class ServerErrorIntegrationTest {

  private Vertx vertx;
  private int port;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    var cacheService = new CacheService(
      Caffeine.newBuilder()
        .expireAfter(new CacheEntryExpiry())
        .build(),
      () -> {
        throw new IllegalStateException("sensitive details");
      }
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
  void unexpected_failure_returns_generic_server_error_and_only_closes_affected_connection() throws Exception {
    try (var failedClient = connect()) {
      // when
      writeAscii(failedClient, "set broken 0 2592001 5\r\nvalue\r\n");

      // then
      assertThat(readLine(failedClient)).isEqualTo("SERVER_ERROR internal server error\r\n");
      failedClient.setSoTimeout(2000);
      assertThat(failedClient.getInputStream().read()).isEqualTo(-1);
    }

    try (var nextClient = connect()) {
      // when
      writeAscii(nextClient, "set next 7 60 5\r\nvalue\r\n");

      // then
      assertThat(readLine(nextClient)).isEqualTo("STORED\r\n");
      writeAscii(nextClient, "get next\r\n");
      assertThat(readUntilEnd(nextClient)).isEqualTo("VALUE next 7 5\r\nvalue\r\nEND\r\n");
    }
  }

  private Socket connect() throws IOException {
    return new Socket("127.0.0.1", port);
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
