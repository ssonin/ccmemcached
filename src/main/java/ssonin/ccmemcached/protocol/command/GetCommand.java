package ssonin.ccmemcached.protocol.command;

import java.util.List;

import static ssonin.ccmemcached.protocol.command.CommandName.GET;

public record GetCommand(
  List<String> keys
) implements Command {

  @Override
  public CommandName name() {
    return GET;
  }
}
