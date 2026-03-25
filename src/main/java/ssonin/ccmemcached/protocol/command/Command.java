package ssonin.ccmemcached.protocol.command;

public record Command(
  CommandName name,
  String key,
  int flags,
  int expTime,
  int bytes,
  boolean noReply
) {

  public CommandType type() {
    return name.type();
  }

  public static final class Builder {

    private CommandName name;
    private String key;
    private int flags;
    private int expTime;
    private int bytes;
    private boolean noReply;

    public static Builder command() {
      return new Builder();
    }

    public Builder name(CommandName name) {
      this.name = name;
      return this;
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

    public Command build() {
      return new Command(name, key, flags, expTime, bytes, noReply);
    }
  }
}
