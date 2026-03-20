# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

`ccmemcached` is a Memcached server clone built with Vert.x 5 (Java 21).

## Commands

```bash
# Build
./gradlew build

# Build fat JAR
./gradlew shadowJar

# Run
./gradlew run

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "ssonin.ccmemcached.AppTest"
```

## Architecture

The server uses two Vert.x verticles:

- **`App`** - root verticle, entry point deployed by `VertxApplication`. Deploys `ServerVerticle` with config (`http.port: 11211`).
- **`ServerVerticle`** - TCP server listening on port 11211 (standard Memcached port). Currently echoes all data back (stub).

The fat JAR manifest sets `Main-Verticle` to `App`, which `VertxApplication` picks up as the verticle to deploy.

Tests use `vertx-junit5` with `@ExtendWith(VertxExtension.class)` for async Vert.x test support.
