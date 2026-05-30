package ssonin.ccmemcached.protocol.command;

import static ssonin.ccmemcached.protocol.command.CommandName.CAS;

public record CasCommand(
  String key,
  int flags,
  int expTime,
  int bytes,
  long casUnique,
  boolean noReply
) implements MetadataStorageCommand {

  @Override
  public CommandName name() {
    return CAS;
  }

  public static final class Builder {

    private String key;
    private int flags;
    private int expTime;
    private int bytes;
    private long casUnique;
    private boolean noReply;

    public static Builder casCommand() {
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

    public Builder casUnique(long casUnique) {
      this.casUnique = casUnique;
      return this;
    }

    public Builder noReply(boolean noReply) {
      this.noReply = noReply;
      return this;
    }

    public CasCommand build() {
      return new CasCommand(key, flags, expTime, bytes, casUnique, noReply);
    }
  }
}
