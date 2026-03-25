package ssonin.ccmemcached.protocol.error;

public abstract class ApplicationError extends RuntimeException {

  protected final ErrorType type;

  ApplicationError(ErrorType type, String message) {
    super(message);
    this.type = type;
  }

  @Override
  public String getMessage() {
    return "%s: %s".formatted(type, super.getMessage());
  }
}
