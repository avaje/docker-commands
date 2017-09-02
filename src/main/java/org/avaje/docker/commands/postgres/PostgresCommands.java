package org.avaje.docker.commands.postgres;

import org.avaje.docker.commands.Commands;
import org.avaje.docker.commands.process.ProcessHandler;
import org.avaje.docker.commands.process.ProcessResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Commands for controlling a postgres docker container.
 *
 * <p>
 * References:
 * </p>
 * <ul>
 *   <li>https://github.com/docker-library/postgres/issues/146</li>
 * </ul>
 */
public class PostgresCommands {

  private static final Logger log = LoggerFactory.getLogger(Commands.class);

  // docker exec -i app_db_1 psql -U postgres < app_development.back
  // docker exec -i ut_postgres psql -U postgres < hello.sql

  private final PostgresConfig config;

  private final Commands commands;

  public PostgresCommands(PostgresConfig config) {
    this.config = config;
    this.commands = new Commands(config.docker);
  }

  /**
   * Start the container and wait for it to be ready.
   * <p>
   * This checks if the container is already running.
   * </p>
   * <p>
   * Returns false if the wait for ready was unsuccessful.
   * </p>
   */
  public boolean start() {
    startIfNeeded();
    if (!waitForDatabaseReady()) {
      log.warn("Failed waitForDatabaseReady for postgres container {}", config.name);
      return false;
    }
    if (!userExists()) {
      createUser();
    }
    if (!databaseExists()) {
      createDatabase();
    }

    createDatabaseExtensions();

    if (!waitForIpConnectivity()) {
      log.warn("Failed waiting for connectivity");
      return false;
    }
    return true;
  }

  public boolean startWithDropCreate() {
    startIfNeeded();
    if (!waitForDatabaseReady()) {
      log.warn("Failed waitForDatabaseReady for postgres container {}", config.name);
      return false;
    }
    if (databaseExists()) {
      dropDatabase();
    }
    if (userExists()) {
      dropUser();
    }
    createUser();
    createDatabase();
    createDatabaseExtensions();

    if (!waitForIpConnectivity()) {
      log.warn("Failed waiting for connectivity");
      return false;
    }
    return true;
  }

  /**
   * Start the container checking if it is already running.
   */
  public void startIfNeeded() {

    if (!commands.isRunning(config.name)) {
      if (commands.isRegistered(config.name)) {
        commands.start(config.name);

      } else {
        log.debug("run postgres container {}", config.name);
        ProcessHandler.process(run());
      }
    }
  }

  /**
   * Stop and remove the container effectively deleting the database.
   */
  public void stopRemove() {
    commands.stopRemove(config.name);
  }

  /**
   * Stop the postgres container.
   */
  public void stop() {
    commands.stopIfRunning(config.name);
  }

  /**
   * Return true if the database exists.
   */
  public boolean databaseExists() {
    return !hasZeroRows(databaseExists(config.dbName));
  }

  /**
   * Return true if the database user exists.
   */
  public boolean userExists() {
    return !hasZeroRows(roleExists(config.dbUser));
  }

  /**
   * Create the database user.
   */
  public boolean createUser() {
    log.debug("create postgres user {}", config.name);
    ProcessBuilder pb = createRole(config.dbUser, config.dbPassword);
    List<String> stdOutLines = ProcessHandler.process(pb).getStdOutLines();
    return stdOutLines.size() == 2;
  }

  /**
   * Create the database.
   */
  public boolean createDatabase() {
    log.debug("create postgres database {} with owner {}", config.dbName, config.dbUser);
    ProcessBuilder pb = createDatabase(config.dbName, config.dbUser);
    List<String> stdOutLines = ProcessHandler.process(pb).getStdOutLines();
    return stdOutLines.size() == 2;
  }

  /**
   * Create the database.
   */
  public void createDatabaseExtensions() {

    String extn = config.dbExtensions;
    if (extn != null) {
      log.debug("create database extensions {}", extn);
      String[] extns = extn.split(",");
      for (String extension : extns) {
        ProcessHandler.process(createDatabaseExtension(extension));
      }
    }
  }

  private ProcessBuilder createDatabaseExtension(String extension) {
    //docker exec -i ut_postgres psql -U postgres -d test_db -c "create extension if not exists pgcrypto";
    List<String> args = new ArrayList<>();
    args.add(config.docker);
    args.add("exec");
    args.add("-i");
    args.add(config.name);
    args.add("psql");
    args.add("-U");
    args.add("postgres");
    args.add("-d");
    args.add(config.dbName);
    args.add("-c");
    args.add("create extension if not exists "+extension);

    return createProcessBuilder(args);
  }

  /**
   * Drop the database.
   */
  public boolean dropDatabase() {
    log.debug("drop postgres database {}", config.dbName);
    ProcessBuilder pb = dropDatabase(config.dbName);
    List<String> stdOutLines = ProcessHandler.process(pb).getStdOutLines();
    return stdOutLines.size() == 1;
  }

  /**
   * Drop the database user.
   */
  public boolean dropUser() {
    log.debug("drop postgres user {}", config.dbUser);
    ProcessBuilder pb = dropUser(config.dbUser);
    List<String> stdOutLines = ProcessHandler.process(pb).getStdOutLines();
    return stdOutLines.size() == 1;
  }

  /**
   * Wait for the 'database system is ready' using pg_isready.
   * <p>
   * This means the DB is ready to take server side commands but TCP connectivity may still not be available yet.
   * </p>
   *
   * @return True when we detect the database is ready (to create user and database etc).
   */
  public boolean isDatabaseReady() {

    ProcessBuilder pb = pgIsReady();
    try {
      ProcessResult result = ProcessHandler.process(pb.start());
      return result.success();
    } catch (IOException e) {
      return false;
    }
//
//    return new WaitForLog(config.name, LOG_READY_MATCH)
//      .withMaxWait(config.maxLogReadyAttempts)
//      .waitForReady();
  }

  /**
   * Return true when the DB is ready for taking commands (like create database, user etc).
   */
  public boolean waitForDatabaseReady() {
    try {
      for (int i = 0; i < config.maxLogReadyAttempts; i++) {
        if (isDatabaseReady()) {
          return true;
        }
        Thread.sleep(100);
      }
      return false;

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  /**
   * Return true when we can make (JDBC) IP connections to the database.
   */
  public boolean waitForIpConnectivity() {
    for (int i = 0; i < 20; i++) {
      if (checkJdbcConnection()) {
        return true;
      }
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return false;
  }

  private boolean checkJdbcConnection() {
    String url = "jdbc:postgresql://localhost:" + config.hostPort + "/" + config.dbName;
    try {
      Connection connection = DriverManager.getConnection(url, config.dbUser, config.dbPassword);
      connection.close();
      log.debug("connectivity confirmed");
      return true;

    } catch (SQLException e) {
      log.trace("connection failed: " + e.getMessage());
      return false;
    }
  }

  private boolean hasZeroRows(ProcessBuilder pb) {
    return hasZeroRows(ProcessHandler.process(pb).getStdOutLines());
  }

  private ProcessBuilder dropDatabase(String dbName) {
    return sqlProcess("drop database if exists " + dbName);
  }

  private ProcessBuilder dropUser(String dbUser) {
    return sqlProcess("drop role if exists " + dbUser);
  }

  private ProcessBuilder createDatabase(String dbName, String roleName) {
    return sqlProcess("create database " + dbName + " with owner " + roleName);
  }

  private ProcessBuilder createRole(String roleName, String pass) {
    return sqlProcess("create role " + roleName + " password '" + pass + "' login");//alter role " + roleName + " login;");
  }

  private ProcessBuilder roleExists(String roleName) {
    return sqlProcess("select rolname from pg_roles where rolname = '" + roleName + "'");
  }

  private ProcessBuilder databaseExists(String dbName) {
    return sqlProcess("select 1 from pg_database where datname = '" + dbName + "'");
  }

  private ProcessBuilder sqlProcess(String sql) {
    List<String> args = new ArrayList<>();
    args.add(config.docker);
    args.add("exec");
    args.add("-i");
    args.add(config.name);
    args.add("psql");
    args.add("-U");
    args.add("postgres");
    args.add("-c");
    args.add(sql);

    return createProcessBuilder(args);
  }

  private ProcessBuilder run() {

    List<String> args = new ArrayList<>();
    args.add(config.docker);
    args.add("run");
    args.add("--name");
    args.add(config.name);
    args.add("-p");
    args.add(config.hostPort + ":" + config.pgPort);

    if (config.tmpfs != null) {
      args.add("--tmpfs");
      args.add(config.tmpfs);
    }

    args.add("-e");
    args.add(config.pgPassword);
    args.add("-d");
    args.add(config.image);

    return createProcessBuilder(args);
  }

  private ProcessBuilder pgIsReady() {

    // try not to depend on locally installed pg_isready
    // pg_isready -h localhost -p 9823
//    args.add("pg_isready");
//    args.add("-h");
//    args.add("localhost");
//    args.add("-p");
//    args.add(config.hostPort);

    List<String> args = new ArrayList<>();

    //docker exec -i junk_postgres pg_isready
    args.add(config.docker);
    args.add("exec");
    args.add("-i");
    args.add(config.name);
    args.add("pg_isready");
    args.add("-h");
    args.add("localhost");
    args.add("-p");
    args.add(config.pgPort);

    return createProcessBuilder(args);
  }

  private ProcessBuilder createProcessBuilder(List<String> args) {
    ProcessBuilder pb = new ProcessBuilder();
    pb.command(args);
    return pb;
  }

  private boolean hasZeroRows(List<String> stdOutLines) {
    if (stdOutLines.size() < 2) {
      throw new RuntimeException("Unexpected results - lines:" + stdOutLines);
    }
    return stdOutLines.get(2).equals("(0 rows)");
  }

}
