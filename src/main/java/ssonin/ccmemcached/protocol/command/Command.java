package ssonin.ccmemcached.protocol.command;

public sealed interface Command
  permits DeleteCommand, GetCommand, StorageCommand, TouchCommand {

  CommandName name();

  default boolean noReply() {
    return false;
  }
}
