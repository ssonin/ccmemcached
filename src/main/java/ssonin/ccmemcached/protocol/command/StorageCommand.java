package ssonin.ccmemcached.protocol.command;

public sealed interface StorageCommand extends Command
  permits AppendCommand, MetadataStorageCommand {

  String key();

  int bytes();

  @Override
  boolean noReply();
}
