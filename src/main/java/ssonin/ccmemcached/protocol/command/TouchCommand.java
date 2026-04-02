package ssonin.ccmemcached.protocol.command;

import static ssonin.ccmemcached.protocol.command.CommandName.TOUCH;

public record TouchCommand(
  String key,
  int expTime,
  boolean noReply
) implements Command {

  @Override
  public CommandName name() {
    return TOUCH;
  }
}
