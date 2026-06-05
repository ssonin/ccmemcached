package ssonin.ccmemcached.protocol.command;

public sealed interface Command
  permits DeleteCommand, NumericCommand, RetrievalCommand, StorageCommand, TouchCommand {

  default boolean noReply() {
    return false;
  }
}
