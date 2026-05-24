package ssonin.ccmemcached.protocol.command;

import java.util.List;

public sealed interface RetrievalCommand extends Command
  permits GetCommand, GetsCommand {

  List<String> keys();

  boolean includeCasUnique();
}
