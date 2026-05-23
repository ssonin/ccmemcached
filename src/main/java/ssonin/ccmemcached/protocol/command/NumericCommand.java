package ssonin.ccmemcached.protocol.command;

public sealed interface NumericCommand extends Command permits DecrCommand, IncrCommand {

  String key();

  long delta();
}
