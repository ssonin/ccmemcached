package ssonin.ccmemcached.protocol.command;

import static ssonin.ccmemcached.protocol.command.CommandName.DECR;

public record DecrCommand(
  String key,
  long delta,
  boolean noReply
) implements NumericCommand {

  @Override
  public CommandName name() {
    return DECR;
  }
}
