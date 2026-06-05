package ssonin.ccmemcached.protocol.command;

public record TouchCommand(
  String key,
  int expTime,
  boolean noReply
) implements Command {
}
