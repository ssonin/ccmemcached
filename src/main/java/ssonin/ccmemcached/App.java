package ssonin.ccmemcached;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import ssonin.ccmemcached.cache.CacheEntry;
import ssonin.ccmemcached.cache.CacheService;
import ssonin.ccmemcached.server.ServerVerticle;

import static com.github.benmanes.caffeine.cache.Expiry.creating;
import static java.time.InstantSource.system;
import static org.slf4j.LoggerFactory.getLogger;

public final class App extends VerticleBase {

  private static final Logger LOG = getLogger(App.class);

  @Override
  public Future<?> start() {
    final var port = config().getInteger("http.port", 11211);
    final var clock = system();
    final var cacheService = new CacheService(
      Caffeine.newBuilder()
        .expireAfter(creating((String k, CacheEntry v) -> v.ttl()))
        .build(),
      clock
    );
    return vertx.deployVerticle(
        new ServerVerticle(cacheService),
        new DeploymentOptions().setConfig(new JsonObject().put("http.port", port)))
      .onSuccess(id -> {
        LOG.info("Config: {}", config());
        LOG.info("{}}, id: {}", ServerVerticle.class.getName(), id);
      })
      .onFailure(Throwable::printStackTrace);
  }
}
