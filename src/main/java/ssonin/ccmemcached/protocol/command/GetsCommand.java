package ssonin.ccmemcached.protocol.command;

import java.util.List;

import static ssonin.ccmemcached.protocol.command.CommandName.GETS;

public record GetsCommand(
  List<String> keys
) implements RetrievalCommand {

  @Override
  public CommandName name() {
    return GETS;
  }

  @Override
  public boolean includeCasUnique() {
    return true;
  }
}
