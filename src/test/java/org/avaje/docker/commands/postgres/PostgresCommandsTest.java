package org.avaje.docker.commands.postgres;

import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class PostgresCommandsTest {

  @Test
  public void basic() throws InterruptedException {

    PostgresConfig config =
      new PostgresConfig()
        .withName("junk_postgres")
        .withHostPort("9823")
        .withDbExtensions("hstore,pgcrypto");

    PostgresCommands pg = new PostgresCommands(config);

    pg.startWithDropCreate();


    String url = "jdbc:postgresql://localhost:"+config.hostPort+"/"+config.dbName;

    try {
      Connection connection = DriverManager.getConnection(url, config.dbUser, config.dbPassword);

      exeSql(connection, "drop table if exists test_junk");
      exeSql(connection, "create table test_junk (acol integer, map hstore)");
      exeSql(connection, "insert into test_junk (acol) values (42)");
      exeSql(connection, "insert into test_junk (acol) values (43)");

    } catch (SQLException e) {
      throw new RuntimeException(e);

    } finally {
      pg.stopRemove();
    }
  }

  private void exeSql(Connection connection, String sql) throws SQLException {
    PreparedStatement st = connection.prepareStatement(sql);
    st.execute();
    st.close();
  }
}