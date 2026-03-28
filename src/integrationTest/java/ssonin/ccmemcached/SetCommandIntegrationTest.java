package ssonin.ccmemcached;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
class SetCommandIntegrationTest {

  private String deploymentId;
  private int port;

  @BeforeEach
  void deploy_app(Vertx vertx, VertxTestContext testContext) throws IOException {
    // given
    var testPort = nextFreePort();

    // when
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
  void stores_value_via_live_tcp_connection(Vertx vertx, VertxTestContext testContext) {
    // given
    var client = vertx.createNetClient();

    // when
    client.connect(port, "127.0.0.1").onComplete(testContext.succeeding(socket -> {
      socket.handler(buffer -> testContext.verify(() -> {
        assertThat(buffer).hasToString("STORED\r\n");
        socket.close();
        close(client);
        testContext.completeNow();
      }));
      socket.write("set mykey 0 60 5\r\nvalue\r\n");
    }));
  }

  private static int nextFreePort() throws IOException {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static void close(NetClient client) {
    client.close();
  }
}
