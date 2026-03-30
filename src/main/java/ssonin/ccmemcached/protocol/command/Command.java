package ssonin.ccmemcached.protocol.command;

public sealed interface Command
  permits DeleteCommand, GetCommand, SetCommand {

  CommandName name();

  default boolean noReply() {
    return false;
  }
}
