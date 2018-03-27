package io.ebean.docker.commands;

import io.ebean.docker.commands.process.ProcessHandler;
import io.ebean.docker.container.Container;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.function.BooleanSupplier;

abstract class DbContainer extends BaseContainer implements Container {

  enum Mode {
    Create,
    DropCreate,
    ContainerOnly
  }

  final DbConfig dbConfig;

  Mode startMode;

  DbContainer(DbConfig config) {
    super(config);
    this.dbConfig = config;
  }

  /**
   * Log that the container is already running.
   */
  void logRunning() {
    log.info("Container {} running with {} mode:{}", config.containerName(), dbConfig.summary(), startMode);
  }

  @Override
  void logRun() {
    log.info("Run container {} with {} mode:{}", config.containerName(), dbConfig.summary(), startMode);
  }

  @Override
  void logStart() {
    log.info("Start container {} with {} mode:{}", config.containerName(), dbConfig.summary(), startMode);
  }

  @Override
  public boolean start() {
    return logStarted(startForMode());
  }

  /**
   * Start with a mode of 'create', 'dropCreate' or 'container'.
   * <p>
   * Expected that mode create will be best most of the time.
   */
  protected boolean startForMode() {
    String mode = config.getStartMode().toLowerCase().trim();
    switch (mode) {
      case "create":
        return startWithCreate();
      case "dropcreate":
        return startWithDropCreate();
      case "container":
        return startContainerOnly();
      default:
        return startWithCreate();
    }
  }

  /**
   * Start the DB container ensuring the DB and user exist creating them if necessary.
   */
  public boolean startWithCreate() {
    startMode = Mode.Create;
    return startWithConnectivity();
  }

  /**
   * Start the DB container ensuring the DB and user are dropped and then created.
   */
  public boolean startWithDropCreate() {
    startMode = Mode.DropCreate;
    return startWithConnectivity();
  }

  /**
   * Start the container only without creating database, user, extensions etc.
   */
  public boolean startContainerOnly() {
    startMode = Mode.ContainerOnly;
    startIfNeeded();
    if (!waitForDatabaseReady()) {
      log.warn("Failed waitForDatabaseReady for container {}", config.containerName());
      return false;
    }

    if (!waitForConnectivity()) {
      log.warn("Failed waiting for connectivity for {}", config.containerName());
      return false;
    }
    return true;
  }

  /**
   * Return the ProcessBuilder used to execute the container run command.
   */
  protected abstract ProcessBuilder runProcess();

  /**
   * Return true when the database is ready to take commands.
   */
  protected abstract boolean isDatabaseReady();

  /**
   * Return true when the database is ready to take admin commands.
   */
  protected abstract boolean isDatabaseAdminReady();

  /**
   * Return true when the DB is ready for taking commands (like create database, user etc).
   */
  public boolean waitForDatabaseReady() {
    return conditionLoop(this::isDatabaseReady)
      && conditionLoop(this::isDatabaseAdminReady);
  }

  private boolean conditionLoop(BooleanSupplier condition) {
    for (int i = 0; i < config.getMaxReadyAttempts(); i++) {
      try {
        if (condition.getAsBoolean()) {
          return true;
        }
        pause();
      } catch (CommandException e) {
        pause();
      }
    }
    return false;
  }

  private void pause() {
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Check connectivity via trying to make a JDBC connection.
   */
  boolean checkConnectivity() {
    return checkConnectivity(false);
  }

  /**
   * Check connectivity using admin user or dbUser.
   */
  boolean checkConnectivity(boolean useAdmin) {
    try {
      log.debug("checkConnectivity on {} ... ", config.containerName);
      Connection connection = useAdmin ? config.createAdminConnection() : config.createConnection();
      connection.close();
      log.debug("connectivity confirmed for {}", config.containerName);
      return true;

    } catch (SQLException e) {
      log.trace("connection failed: " + e.getMessage());
      return false;
    }
  }

  boolean defined(String val) {
    return val != null && !val.trim().isEmpty();
  }

  /**
   * Execute looking for expected message in stdout with no error logging.
   */
  boolean execute(String expectedLine, ProcessBuilder pb) {
    return execute(expectedLine, pb, null);
  }

  /**
   * Execute looking for expected message in stdout.
   */
  boolean execute(String expectedLine, ProcessBuilder pb, String errorMessage) {
    List<String> outLines = ProcessHandler.process(pb).getOutLines();
    if (!stdoutContains(outLines, expectedLine)) {
      if (errorMessage != null) {
        log.error(errorMessage + " stdOut:" + outLines + " Expected message:" + expectedLine);
      }
      return false;
    }
    return true;
  }

  /**
   * Execute looking for expected message in stdout.
   */
  boolean executeWithout(String errorMatch, ProcessBuilder pb, String errorMessage) {
    List<String> outLines = ProcessHandler.process(pb).getOutLines();
    if (stdoutContains(outLines, errorMatch)) {
      log.error(errorMessage + " stdOut:" + outLines);
      return false;
    }
    return true;
  }

  /**
   * Return true if the stdout contains the expected text.
   */
  boolean stdoutContains(List<String> outLines, String expectedLine) {
    for (String outLine : outLines) {
      if (outLine.contains(expectedLine)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Execute expecting no output to stdout.
   */
  boolean execute(ProcessBuilder pb, String errorMessage) {
    List<String> outLines = ProcessHandler.process(pb).getOutLines();
    if (!outLines.isEmpty()) {
      log.error(errorMessage + " stdOut:" + outLines);
      return false;
    }
    return true;
  }

}