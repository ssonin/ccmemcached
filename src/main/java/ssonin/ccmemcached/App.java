package ssonin.ccmemcached;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import ssonin.ccmemcached.server.ServerVerticle;

import static org.slf4j.LoggerFactory.getLogger;

public final class App extends VerticleBase {

  private static final Logger LOG = getLogger(App.class);

  @Override
  public Future<?> start() {
    return vertx.deployVerticle(
        new ServerVerticle(),
        new DeploymentOptions().setConfig(new JsonObject().put("http.port", 11211)))
      .onSuccess(id -> {
        LOG.info("Config: {}", config());
        LOG.info("ssonin.ccmemcached.server.NetVerticle deployed, id: {}", id);
      })
      .onFailure(Throwable::printStackTrace);
  }
}
