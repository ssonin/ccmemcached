package ssonin.ccmemcached.protocol;

import ssonin.ccmemcached.cache.CacheEntry;
import ssonin.ccmemcached.cache.StoreResult;
import ssonin.ccmemcached.protocol.error.ApplicationError;
import ssonin.ccmemcached.protocol.error.ErrorType;

import java.util.OptionalLong;

final class ResponseFormatter {

  private static final String CRLF = "\r\n";

  String error(ApplicationError error) {
    return error(error.type(), error.getMessage());
  }

  String error(ErrorType type, String message) {
    return switch (type) {
      case ERROR -> "ERROR" + CRLF;
      case CLIENT_ERROR, SERVER_ERROR -> "%s %s%s".formatted(type.name(), message, CRLF);
    };
  }

  String storage(StoreResult result) {
    return result.name() + CRLF;
  }

  String deleted(boolean deleted) {
    return (deleted ? "DELETED" : "NOT_FOUND") + CRLF;
  }

  String touched(boolean touched) {
    return (touched ? "TOUCHED" : "NOT_FOUND") + CRLF;
  }

  String numeric(OptionalLong value) {
    return (value.isPresent() ? Long.toUnsignedString(value.getAsLong()) : "NOT_FOUND") + CRLF;
  }

  String valueLine(String key, CacheEntry entry, boolean includeCasUnique) {
    if (includeCasUnique) {
      return "VALUE %s %s %s %s%s".formatted(
        key,
        entry.flags(),
        entry.data().length,
        Long.toUnsignedString(entry.casUnique()),
        CRLF
      );
    }
    return "VALUE %s %s %s%s".formatted(key, entry.flags(), entry.data().length, CRLF);
  }

  String crlf() {
    return CRLF;
  }

  String end() {
    return "END" + CRLF;
  }
}
