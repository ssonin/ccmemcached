package ssonin.ccmemcached.protocol.command;

public sealed interface StorageCommand extends Command
  permits MetadataStorageCommand {

  String key();

  int bytes();

  @Override
  boolean noReply();
}
