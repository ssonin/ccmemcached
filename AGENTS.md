# AGENTS.md

## Project Summary

`ccmemcached` is a small Memcached text-protocol server clone built with Java 25, Gradle, Vert.x 5, and Caffeine.

The current implementation supports these text protocol commands:

- `set`
- `get`
- `gets`
- `cas`
- `delete`
- `add`
- `append`
- `prepend`
- `replace`
- `touch`
- `incr`
- `decr`

Cached values are stored in an in-memory Caffeine cache with Memcached-style TTL handling. Values are treated as raw bytes; numeric commands parse existing values as unsigned 64-bit integers.

This repository is under active development. The worktree may already contain user changes when you start. Read before editing and do not overwrite unrelated modifications.

## Useful Commands

```bash
./gradlew build
./gradlew test
./gradlew integrationTest
./gradlew test integrationTest
./gradlew run
./gradlew shadowJar
```

The fat JAR is built with the Shadow plugin. The application entrypoint uses `io.vertx.launcher.application.VertxApplication`, with `ssonin.ccmemcached.App` configured as the main verticle in the JAR manifest.

The project targets Java 25. Java 25 language features such as unnamed pattern variables may be used in production code.

## Architecture

### Entry and server startup

- `src/main/java/ssonin/ccmemcached/App.java`
  - Root verticle.
  - Creates the shared `CacheService`.
  - Configures the cache to expire entries using `CacheEntryExpiry`.
  - Deploys `ServerVerticle` with port config `11211` by default.
  - Supports binding to port `0` in tests and exposes the actual bound port through `actualPort()`.

- `src/main/java/ssonin/ccmemcached/server/ServerVerticle.java`
  - Creates the Vert.x TCP server.
  - Accepts client connections.
  - Instantiates one `ProtocolHandler` per socket.
  - Stores the actual bound port after `listen(...)` succeeds.

### Protocol flow

- `src/main/java/ssonin/ccmemcached/protocol/ProtocolHandler.java`
  - Owns a Vert.x `RecordParser`.
  - Uses a 3-state parser:
    - `AWAITING_COMMAND`
    - `AWAITING_DATA`
    - `AWAITING_TRAILING_CRLF`
  - Command lines are parsed in CRLF-delimited mode.
  - Storage payloads are read in fixed-size mode using the parsed `bytes` field.
  - Storage commands are applied only when no bytes appear between the declared payload and its trailing CRLF.
  - Extra bytes before the trailing CRLF produce a `CLIENT_ERROR`, do not mutate the cache, and reset the parser to command mode.
  - `ApplicationError` subclasses are translated to protocol error responses and the connection stays open.

`ProtocolHandler` currently uses a compact switch-based dispatcher. This is acceptable for the current command count; a command-handler abstraction can be introduced later if dispatch or response formatting grows.

### Command parsing

- `src/main/java/ssonin/ccmemcached/protocol/command/CommandName.java`
  - Lists implemented command names: `ADD`, `APPEND`, `CAS`, `DECR`, `DELETE`, `GET`, `GETS`, `INCR`, `PREPEND`, `REPLACE`, `SET`, `TOUCH`.

- `src/main/java/ssonin/ccmemcached/protocol/command/parser/CommandParser.java`
  - Parses the command line into a command record.
  - Dispatches to package-private command-specific parser utilities.

- `src/main/java/ssonin/ccmemcached/protocol/command/parser/ParsingSupport.java`
  - Contains shared parser validation helpers.
  - Validates:
    - key presence and max length
    - control characters in keys
    - `flags` numeric range
    - numeric `exptime`
    - non-negative `bytes`
    - max value size
    - maximum of 100 keys for multi-key `get` and `gets`
    - unsigned 64-bit numeric deltas
    - optional `noreply`

Command-specific parser classes are utility classes with private constructors and package-private `parse(...)` methods.

### Cache layer

- `src/main/java/ssonin/ccmemcached/cache/CacheService.java`
  - Wraps Caffeine cache access.
  - Implements storage, retrieval, delete, touch, increment, and decrement behaviour.
  - Converts Memcached expiration semantics into a `Duration`:
    - `<= 0` means effectively never expire
    - small positive values are treated as relative seconds
    - large values are treated as absolute Unix timestamps
  - Uses Java unsigned `long` helpers for numeric command parsing and formatting.
  - Numeric commands preserve the existing entry TTL and flags.
  - `append` and `prepend` preserve existing entry TTL and flags while updating data and CAS unique.
  - Metadata storage commands reset expiry when updating an existing entry.
  - `touch` updates TTL while preserving flags and data.

- `src/main/java/ssonin/ccmemcached/cache/CacheEntry.java`
  - Record for cached item metadata and bytes.
  - Use the `cacheEntry()` builder rather than calling the record constructor directly.
  - Stores `casUnique`, an opaque 64-bit token exposed by `gets` and used by `cas`.
  - Includes `ExpiryUpdate` metadata so Caffeine can distinguish updates that reset expiry from updates that preserve the existing expiry duration.

- `src/main/java/ssonin/ccmemcached/cache/CacheEntryExpiry.java`
  - Caffeine expiry policy.
  - Uses entry TTL on create.
  - Preserves or resets expiry on update according to `CacheEntry.expiryUpdate()`.

## Error Model

- `src/main/java/ssonin/ccmemcached/protocol/error/ApplicationError.java`
  - Base protocol exception.

- `src/main/java/ssonin/ccmemcached/protocol/error/CommandNameError.java`
  - Unknown command name.
  - Formats as `ERROR` on the wire.

- `src/main/java/ssonin/ccmemcached/protocol/error/ClientError.java`
  - Invalid client input.
  - Formats as `CLIENT_ERROR <message>` on the wire.

`ApplicationError` keeps its diagnostic message separate from its error type. `ProtocolHandler` formats errors at the wire boundary.

Unexpected runtime failures during command processing are logged, returned as the generic `SERVER_ERROR internal server error`, and close only the affected client connection.

Transport-level socket exceptions are logged by `ServerVerticle` without attempting to write a protocol response.

## Testing

There are unit tests and integration tests.

- Unit tests live under `src/test/java`.
  - Parser tests cover successful parsing and invalid input cases.
  - `ProtocolHandlerTest` covers parser state transitions, command dispatch, responses, and recovery after protocol errors.
  - Cache tests cover TTL evaluation, expiry update semantics, storage operations, and numeric command edge cases.

- Integration tests live under `src/integrationTest/java`.
  - They use real TCP sockets against a deployed Vert.x app.
  - Test servers bind to port `0` and connect through `App.actualPort()` to avoid free-port probing races.
  - Fake ticker backed tests are used where TTL behaviour needs deterministic time control.

Run both suites before calling broad protocol changes complete:

```bash
./gradlew test integrationTest
```

## Current Gaps and Likely Next Work

- Consider enforcing command-line length while bytes are accumulating, not only after `RecordParser` emits a full line.
- Consider extracting response formatting or command handlers if the switch-based dispatcher starts to grow too much.
