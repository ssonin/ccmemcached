package ssonin.ccmemcached.protocol;

public final class MemcachedLimits {

  public static final int MAX_VALUE_BYTES = 1024 * 1024;
  public static final int MAX_COMMAND_LINE_BYTES = 8 * 1024;
  public static final int MAX_GET_KEYS = 100;

  private MemcachedLimits() {
    throw new AssertionError("Utility class");
  }
}
