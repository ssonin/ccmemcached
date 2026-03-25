package ssonin.ccmemcached.protocol.error;

import static ssonin.ccmemcached.protocol.error.ErrorType.ERROR;

public final class CommandNameError extends ApplicationError {

  public CommandNameError(String name) {
    super(ERROR, "Nonexistent command name '%s'".formatted(name));
  }
}
