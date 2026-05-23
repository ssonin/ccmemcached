package ssonin.ccmemcached;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import ssonin.ccmemcached.cache.CacheEntryExpiry;
import ssonin.ccmemcached.cache.CacheService;
import ssonin.ccmemcached.server.ServerVerticle;

import java.util.function.Supplier;

import static java.time.InstantSource.system;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

public final class App extends VerticleBase {

  private static final Logger LOG = getLogger(App.class);
  private final Supplier<CacheService> cacheServiceSupplier;

  public App() {
    this(App::defaultCacheService);
  }

  App(Supplier<CacheService> cacheServiceSupplier) {
    this.cacheServiceSupplier = requireNonNull(cacheServiceSupplier, "cacheServiceSupplier must not be null");
  }

  @Override
  public Future<?> start() {
    final var port = config().getInteger("http.port", 11211);
    final var cacheService = cacheServiceSupplier.get();
    return vertx.deployVerticle(
        new ServerVerticle(cacheService),
        new DeploymentOptions().setConfig(new JsonObject().put("http.port", port)))
      .onSuccess(id -> {
        LOG.info("Config: {}", config());
        LOG.info("{}, id: {}", ServerVerticle.class.getName(), id);
      })
      .onFailure(Throwable::printStackTrace);
  }

  private static CacheService defaultCacheService() {
    final var clock = system();
    return new CacheService(
      Caffeine.newBuilder()
        .expireAfter(new CacheEntryExpiry())
        .build(),
      clock
    );
  }
}
