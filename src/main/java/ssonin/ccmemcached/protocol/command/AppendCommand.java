package ssonin.ccmemcached.protocol.command;

public record AppendCommand(
  String key,
  int bytes,
  boolean noReply
) implements StorageCommand {

  public static final class Builder {

    private String key;
    private int bytes;
    private boolean noReply;

    public static Builder appendCommand() {
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

    public AppendCommand build() {
      return new AppendCommand(key, bytes, noReply);
    }
  }
}
