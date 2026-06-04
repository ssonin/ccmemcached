package ssonin.ccmemcached.server;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import ssonin.ccmemcached.cache.CacheService;
import ssonin.ccmemcached.protocol.ProtocolHandler;

import static org.slf4j.LoggerFactory.getLogger;

public final class ServerVerticle extends VerticleBase {

  private static final Logger log = getLogger(ServerVerticle.class);

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
        log.info("Client connected: {}", socket.remoteAddress());
        handle(socket);
      })
      .listen(port)
      .onSuccess(server -> {
        actualPort = server.actualPort();
        log.info("TCP server started on port {}", server.actualPort());
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
    socket.exceptionHandler(error -> log.warn("Client connection error: {}", socket.remoteAddress(), error));
    socket.closeHandler(_ -> log.info("Client disconnected: {}", socket.remoteAddress()));
    new ProtocolHandler(cacheService, socket).start();
  }
}
