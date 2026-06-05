package ssonin.ccmemcached.protocol.command;

public record DecrCommand(
  String key,
  long delta,
  boolean noReply
) implements NumericCommand {
}
