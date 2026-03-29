package ssonin.ccmemcached.protocol.command;

public sealed interface Command
  permits SetCommand {

  CommandName name();

  default CommandType type() {
    return name().type();
  }

  default boolean noReply() {
    return false;
  }
}
