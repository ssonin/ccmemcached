package ssonin.ccmemcached.protocol.command;

public sealed interface Command
  permits DeleteCommand, GetCommand, StorageCommand {

  CommandName name();

  default boolean noReply() {
    return false;
  }
}
