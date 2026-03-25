package ssonin.ccmemcached.protocol.command;

import static ssonin.ccmemcached.protocol.command.CommandType.STORAGE;

public enum CommandName {

  SET(STORAGE);

  private final CommandType type;

  public CommandType type() {
    return type;
  }

  CommandName(CommandType type) {
    this.type = type;
  }
}
