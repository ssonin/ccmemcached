package ssonin.ccmemcached;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MainVerticle extends VerticleBase {

  private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public Future<?> start() {
    return vertx.deployVerticle(
        "ssonin.ccmemcached.NetVerticle",
        new DeploymentOptions().setConfig(new JsonObject().put("http.port", 11211)))
      .onSuccess(id -> {
        LOG.info("Config: {}", config());
        LOG.info("ssonin.ccmemcached.NetVerticle deployed, id: {}", id);
      })
      .onFailure(Throwable::printStackTrace);
  }
}
