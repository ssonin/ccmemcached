package ssonin.ccmemcached.protocol.command;

public record IncrCommand(
  String key,
  long delta,
  boolean noReply
) implements NumericCommand {
}
