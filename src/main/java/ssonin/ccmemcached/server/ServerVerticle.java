package ssonin.ccmemcached.server;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

public final class ServerVerticle extends VerticleBase {

  private static final Logger LOG = getLogger(ServerVerticle.class);

  @Override
  public Future<?> start() {
    final var port = config().getInteger("http.port");
    return vertx.createNetServer()
      .connectHandler(sock -> {
        LOG.info("Client connected: {}", sock.remoteAddress());
        handle(sock);
      })
      .listen(port)
      .onSuccess(http -> {
        LOG.info("TCP server started on port {}", port);
      })
      .onFailure(Throwable::printStackTrace);
  }

  private void handle(NetSocket sock) {
    sock.closeHandler(s -> LOG.info("Client disconnected: {}", sock.remoteAddress()));
    sock.handler(sock::write);
  }
}
