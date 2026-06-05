package ssonin.ccmemcached.protocol.command;

import java.util.List;

public record GetsCommand(
  List<String> keys
) implements RetrievalCommand {

  @Override
  public boolean includeCasUnique() {
    return true;
  }
}
