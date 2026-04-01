package ssonin.ccmemcached;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(VertxExtension.class)
class AddCommandIntegrationTest {

  private String deploymentId;
  private int port;

  @BeforeEach
  void deploy_app(Vertx vertx, VertxTestContext testContext) throws IOException {
    var testPort = nextFreePort();
    vertx.deployVerticle(
        new App(),
        new DeploymentOptions().setConfig(new JsonObject().put("http.port", testPort)))
      .onComplete(testContext.succeeding(id -> testContext.verify(() -> {
        deploymentId = id;
        port = testPort;
        testContext.completeNow();
      })));
  }

  @AfterEach
  void undeploy_app(Vertx vertx, VertxTestContext testContext) {
    if (deploymentId == null) {
      testContext.completeNow();
      return;
    }

    vertx.undeploy(deploymentId).onComplete(testContext.succeeding(v -> testContext.completeNow()));
  }

  @Test
  void add_persists_missing_value_retrievable_via_get() throws Exception {
    try (var client = connect()) {
      sendAdd(client, "mykey", 7, 60, "value");
      writeAscii(client, "get mykey\r\n");

      assertThat(readUntilEnd(client)).isEqualTo(normalizeCrlf("""
        VALUE mykey 7 5
        value
        END
        """));
    }
  }

  @Test
  void add_returns_not_stored_and_preserves_existing_value() throws Exception {
    try (var client = connect()) {
      sendAdd(client, "mykey", 1, 60, "first");

      writeAscii(client, "add mykey 2 60 6\r\nsecond\r\n");
      assertThat(readLine(client)).isEqualTo("NOT_STORED\r\n");

      writeAscii(client, "get mykey\r\n");
      assertThat(readUntilEnd(client)).isEqualTo(normalizeCrlf("""
        VALUE mykey 1 5
        first
        END
        """));
    }
  }

  @Test
  void add_with_noreply_stores_missing_value_without_immediate_response() throws Exception {
    try (var client = connect()) {
      writeAscii(client, "add quiet 3 60 5 noreply\r\nvalue\r\n");

      client.setSoTimeout(200);
      assertThatThrownBy(() -> client.getInputStream().read())
        .isInstanceOf(SocketTimeoutException.class);

      client.setSoTimeout(2000);
      writeAscii(client, "get quiet\r\n");

      assertThat(readUntilEnd(client)).isEqualTo(normalizeCrlf("""
        VALUE quiet 3 5
        value
        END
        """));
    }
  }

  private static int nextFreePort() throws IOException {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private Socket connect() throws IOException {
    return new Socket("127.0.0.1", port);
  }

  private void sendAdd(Socket client, String key, int flags, int exptime, String value) throws IOException {
    writeAscii(client, "add %s %d %d %d\r\n%s\r\n".formatted(key, flags, exptime, value.length(), value));
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
}
