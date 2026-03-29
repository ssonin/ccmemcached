package ssonin.ccmemcached.protocol.command;

import static ssonin.ccmemcached.protocol.command.CommandType.RETRIEVAL;
import static ssonin.ccmemcached.protocol.command.CommandType.STORAGE;

public enum CommandName {

  SET(STORAGE),
  GET(RETRIEVAL);

  private final CommandType type;

  public CommandType type() {
    return type;
  }

  CommandName(CommandType type) {
    this.type = type;
  }
}
