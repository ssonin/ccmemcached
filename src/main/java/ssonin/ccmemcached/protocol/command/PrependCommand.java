package ssonin.ccmemcached.protocol.command;

import static ssonin.ccmemcached.protocol.command.CommandName.PREPEND;

public record PrependCommand(
  String key,
  int bytes,
  boolean noReply
) implements StorageCommand {

  @Override
  public CommandName name() {
    return PREPEND;
  }

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
