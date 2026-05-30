package ssonin.ccmemcached.protocol.command;

public sealed interface MetadataStorageCommand extends StorageCommand
  permits AddCommand, CasCommand, ReplaceCommand, SetCommand {

  int flags();

  int expTime();
}
