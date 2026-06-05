package ssonin.ccmemcached.protocol.command;

import java.util.List;

public record GetCommand(
  List<String> keys
) implements RetrievalCommand {

  @Override
  public boolean includeCasUnique() {
    return false;
  }
}
