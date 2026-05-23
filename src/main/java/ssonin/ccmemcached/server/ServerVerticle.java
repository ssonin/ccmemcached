package ssonin.ccmemcached.server;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import ssonin.ccmemcached.cache.CacheService;
import ssonin.ccmemcached.protocol.ProtocolHandler;

import static org.slf4j.LoggerFactory.getLogger;

public final class ServerVerticle extends VerticleBase {

  private static final Logger logger = getLogger(ServerVerticle.class);

  private final CacheService cacheService;
  private int actualPort;

  public ServerVerticle(CacheService cacheService) {
    this.cacheService = cacheService;
  }

  @Override
  public Future<?> start() {
    final var port = config().getInteger("http.port");
    return vertx.createNetServer()
      .connectHandler(socket -> {
        logger.info("Client connected: {}", socket.remoteAddress());
        handle(socket);
      })
      .listen(port)
      .onSuccess(server -> {
        actualPort = server.actualPort();
        logger.info("TCP server started on port {}", server.actualPort());
      })
      .onFailure(Throwable::printStackTrace);
  }

  public int actualPort() {
    if (actualPort == 0) {
      throw new IllegalStateException("server is not bound yet");
    }
    return actualPort;
  }

  private void handle(NetSocket socket) {
    socket.closeHandler(s -> logger.info("Client disconnected: {}", socket.remoteAddress()));
    new ProtocolHandler(cacheService, socket).start();
  }
}
