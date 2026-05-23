package ssonin.ccmemcached.protocol.command;

import static ssonin.ccmemcached.protocol.command.CommandName.INCR;

public record IncrCommand(
  String key,
  long delta,
  boolean noReply
) implements NumericCommand {

  @Override
  public CommandName name() {
    return INCR;
  }
}
