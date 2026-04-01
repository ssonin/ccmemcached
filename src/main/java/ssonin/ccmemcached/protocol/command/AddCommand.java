package ssonin.ccmemcached.protocol.command;

import static ssonin.ccmemcached.protocol.command.CommandName.ADD;

public record AddCommand(
  String key,
  int flags,
  int expTime,
  int bytes,
  boolean noReply
) implements StorageCommand {

  @Override
  public CommandName name() {
    return ADD;
  }

  public static final class Builder {

    private String key;
    private int flags;
    private int expTime;
    private int bytes;
    private boolean noReply;

    public static Builder addCommand() {
      return new Builder();
    }

    public Builder key(String key) {
      this.key = key;
      return this;
    }

    public Builder flags(int flags) {
      this.flags = flags;
      return this;
    }

    public Builder expTime(int expTime) {
      this.expTime = expTime;
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

    public AddCommand build() {
      return new AddCommand(key, flags, expTime, bytes, noReply);
    }
  }
}
