package ssonin.ccmemcached.protocol.error;

import static ssonin.ccmemcached.protocol.error.ErrorType.CLIENT_ERROR;

public final class ClientError extends ApplicationError {

  public ClientError(String message) {
    super(CLIENT_ERROR, message);
  }
}
