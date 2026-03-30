package ssonin.ccmemcached.protocol.command;

import static ssonin.ccmemcached.protocol.command.CommandName.DELETE;

public record DeleteCommand(
  String key,
  boolean noReply
) implements Command {

  @Override
  public CommandName name() {
    return DELETE;
  }
}
