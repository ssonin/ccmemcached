package ssonin.ccmemcached.protocol.command;

public record DeleteCommand(
  String key,
  boolean noReply
) implements Command {
}
