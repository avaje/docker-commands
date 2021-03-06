package io.ebean.docker.commands;

import io.ebean.docker.container.Container;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

/**
 * Commands for controlling a SqlServer docker container.
 */
public class SqlServerContainer extends JdbcBaseDbContainer implements Container {

  public static SqlServerContainer create(String version, Properties properties) {
    return new SqlServerContainer(new SqlServerConfig(version, properties));
  }

  public SqlServerContainer(SqlServerConfig config) {
    super(config);
  }

  @Override
  void createDatabase() {
    createRoleAndDatabase(false);
  }

  @Override
  void dropCreateDatabase() {
    createRoleAndDatabase(true);
  }

  private void createRoleAndDatabase(boolean withDrop) {
    try (Connection connection = config.createAdminConnection()) {
      if (withDrop) {
        dropDatabaseIfExists(connection);
      }
      createDatabase(connection);
      createLogin(connection);
      createUser();

    } catch (SQLException e) {
      throw new RuntimeException("Error when creating database and role", e);
    }
  }

  private void createUser() {
    try (Connection dbConnection = dbConfig.createAdminConnection(dbConfig.jdbcUrl())) {
      createUser(dbConnection);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void createLogin(Connection connection) {
    if (!loginExists(connection, dbConfig.getUsername())) {
       createLogin(connection, dbConfig.getUsername(), dbConfig.getPassword());
    }
  }

  private void createUser(Connection dbConnection) {
    if (!userExists(dbConnection, dbConfig.getUsername())) {
      createUser(dbConnection, dbConfig.getUsername(), dbConfig.getUsername());
      grantOwner(dbConnection, dbConfig.getUsername());
    }
  }

  private void createDatabase(Connection connection) {
    if (!databaseExists(connection, dbConfig.getDbName())) {
      createDatabase(connection, dbConfig.getDbName());
    }
  }

  private void dropDatabaseIfExists(Connection connection) {
    if (databaseExists(connection, dbConfig.getDbName())) {
      dropDatabase(connection, dbConfig.getDbName());
    }
  }

  private void dropDatabase(Connection connection, String dbName) {
    sqlRun(connection, "drop database " + dbName);
  }

//  private void dropLogin(Connection connection) {
//    if (loginExists(connection, dbConfig.username)) {
//      sqlRun(connection, "drop login " + dbConfig.username);
//    }
//  }

  private void createDatabase(Connection connection, String dbName) {
    sqlRun(connection, "create database " + dbName);
  }

  private void createLogin(Connection connection, String login, String pass) {
    sqlRun(connection, "create login " + login + " with password = '" + pass + "'");
  }

  private void createUser(Connection dbConnection, String roleName, String login) {
    sqlRun(dbConnection, "create user " + roleName + " for login " + login);
  }

  private void grantOwner(Connection dbConnection, String roleName) {
    sqlRun(dbConnection, "exec sp_addrolemember 'db_owner', " + roleName);
  }

  private boolean userExists(Connection dbConnection, String userName) {
    return sqlHasRow(dbConnection, "select 1 from sys.database_principals where name = '" + userName + "'");
  }

  private boolean loginExists(Connection connection, String roleName) {
    return sqlHasRow(connection, "select 1 from master.dbo.syslogins where loginname = '" + roleName + "'");
  }

  private boolean databaseExists(Connection connection, String dbName) {
    return sqlHasRow(connection, "select 1 from sys.databases where name='" + dbName + "'");
  }

  @Override
  protected ProcessBuilder runProcess() {

    List<String> args = dockerRun();
    args.add("-e");
    args.add("ACCEPT_EULA=Y");
    args.add("-e");
    args.add("SA_PASSWORD=" + dbConfig.getAdminPassword());

    if (config.isDefaultCollation()) {
      // do nothing, use server default
    } else if (config.isExplicitCollation()) {
      args.add("-e");
      args.add("MSSQL_COLLATION=" + dbConfig.getCollation());
    } else {
      // use case sensitive collation by default
      args.add("-e");
      args.add("MSSQL_COLLATION=Latin1_General_100_BIN2");
    }
    args.add(config.getImage());
    return createProcessBuilder(args);
  }

}
