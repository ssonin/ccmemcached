package ssonin.ccmemcached.protocol.command;

public record PrependCommand(
  String key,
  int bytes,
  boolean noReply
) implements StorageCommand {

  public static final class Builder {

    private String key;
    private int bytes;
    private boolean noReply;

    public static Builder prependCommand() {
      return new Builder();
    }

    public Builder key(String key) {
      this.key = key;
      return this;
    }

    public Builder bytes(int bytes) {
      this.bytes = bytes;
      return this;
    }

    public Builder noReply(boolean noReply) {
      this.noReply = noReply;
      return this;
    }

    public PrependCommand build() {
      return new PrependCommand(key, bytes, noReply);
    }
  }
}
