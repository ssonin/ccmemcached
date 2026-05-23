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
class DecrCommandIntegrationTest {

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
  void decr_decrements_existing_numeric_value_and_value_is_retrievable_via_get() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "counter", 7, 60, "42");

      // when
      writeAscii(client, "decr counter 1\r\n");

      // then
      assertThat(readLine(client)).isEqualTo("41\r\n");

      // when
      writeAscii(client, "get counter\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo("VALUE counter 7 2\r\n41\r\nEND\r\n");
    }
  }

  @Test
  void decr_clamps_result_to_zero() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "counter", 7, 60, "2");

      // when
      writeAscii(client, "decr counter 5\r\n");

      // then
      assertThat(readLine(client)).isEqualTo("0\r\n");

      // when
      writeAscii(client, "get counter\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo("VALUE counter 7 1\r\n0\r\nEND\r\n");
    }
  }

  @Test
  void decr_returns_not_found_for_missing_key() throws Exception {
    try (var client = connect()) {
      // when
      writeAscii(client, "decr missing 1\r\n");

      // then
      assertThat(readLine(client)).isEqualTo("NOT_FOUND\r\n");
    }
  }

  @Test
  void decr_with_noreply_updates_value_without_immediate_response() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "counter", 7, 60, "42");

      // when
      writeAscii(client, "decr counter 1 noreply\r\n");

      // then
      client.setSoTimeout(200);
      assertThatThrownBy(() -> client.getInputStream().read())
        .isInstanceOf(SocketTimeoutException.class);

      // when
      client.setSoTimeout(2000);
      writeAscii(client, "get counter\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo("VALUE counter 7 2\r\n41\r\nEND\r\n");
    }
  }

  @Test
  void decr_handles_unsigned_value_above_signed_long_range() throws Exception {
    try (var client = connect()) {
      // given
      sendSet(client, "counter", 7, 60, "18446744073709551615");

      // when
      writeAscii(client, "decr counter 1\r\n");

      // then
      assertThat(readLine(client)).isEqualTo("18446744073709551614\r\n");

      // when
      writeAscii(client, "get counter\r\n");

      // then
      assertThat(readUntilEnd(client)).isEqualTo("VALUE counter 7 20\r\n18446744073709551614\r\nEND\r\n");
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
}
