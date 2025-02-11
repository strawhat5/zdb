/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.zdb;

import io.zeebe.zdb.impl.log.LogConsistencyCheck;
import io.zeebe.zdb.impl.log.LogStatus;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Spec;

@Command(
    name = "log",
    mixinStandardHelpOptions = true,
    subcommands = {LogSearchCommand.class, LogPrintCommand.class},
    description = "Allows to inspect the log via sub commands")
public class LogCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  @Option(
      names = {"-p", "--path"},
      paramLabel = "LOG_PATH",
      description = "The path to the partition log data, should end with the partition id.",
      required = true,
      scope = ScopeType.INHERIT)
  private Path partitionPath;

  @Command(name = "status", description = "Print's the status of the log")
  public int status() {
    final var output = new LogStatus().scan(partitionPath);
    System.out.println(output);
    return 0;
  }

  @Command(name = "consistency", description = "Checks the given log for consistency")
  public int consistency() {
    final var output = new LogConsistencyCheck().consistencyCheck(partitionPath);
    System.out.println(output);
    return 0;
  }

  @Override
  public Integer call() {
    spec.commandLine().usage(System.out);
    return 0;
  }
}
