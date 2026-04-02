package ssonin.ccmemcached.protocol.command;

public sealed interface StorageCommand extends Command
  permits AddCommand, ReplaceCommand, SetCommand {

  String key();

  int flags();

  int expTime();

  int bytes();

  @Override
  boolean noReply();
}
