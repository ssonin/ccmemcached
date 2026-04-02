package ssonin.ccmemcached.protocol.command;

import static ssonin.ccmemcached.protocol.command.CommandName.REPLACE;

public record ReplaceCommand(
  String key,
  int flags,
  int expTime,
  int bytes,
  boolean noReply
) implements StorageCommand {

  @Override
  public CommandName name() {
    return REPLACE;
  }

  public static final class Builder {

    private String key;
    private int flags;
    private int expTime;
    private int bytes;
    private boolean noReply;

    public static Builder replaceCommand() {
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

    public ReplaceCommand build() {
      return new ReplaceCommand(key, flags, expTime, bytes, noReply);
    }
  }
}
