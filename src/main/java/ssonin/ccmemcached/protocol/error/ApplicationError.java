package ssonin.ccmemcached.protocol.error;

public abstract class ApplicationError extends RuntimeException {

  private final ErrorType type;

  ApplicationError(ErrorType type, String message) {
    super(message);
    this.type = type;
  }

  public ErrorType type() {
    return type;
  }
}
