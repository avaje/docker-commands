package org.avaje.docker.commands;

import org.avaje.docker.container.ContainerConfig;
import org.avaje.docker.container.ContainerFactory;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;

public class MySqlCommandsTest {

  @Test
  public void basic() throws InterruptedException {


    Properties properties = new Properties();
    properties.setProperty("mysql.version", "5.7");
    properties.setProperty("mysql.containerName", "temp_mysql");
    properties.setProperty("mysql.port", "7306");

    properties.setProperty("mysql.name", "test_roberto");
    properties.setProperty("mysql.user", "test_robino");

    ContainerFactory factory = new ContainerFactory(properties);

    factory.startContainers(s -> System.out.println(">> " + s));

    ContainerConfig config = factory.config("mysql");

    try {
      Connection connection = config.createConnection();

      // String url = config.jdbcUrl();
      // String url = "jdbc:mysql://localhost:" + "7306" + "/" + "test_roberto";
      // Connection connection = DriverManager.getConnection(url, config.dbUser, config.dbPassword);

      exeSql(connection, "drop table if exists test_junk2");
      exeSql(connection, "create table test_junk2 (acol integer)");
      exeSql(connection, "insert into test_junk2 (acol) values (42)");
      exeSql(connection, "insert into test_junk2 (acol) values (43)");

      connection.close();

    } catch (SQLException e) {
      throw new RuntimeException(e);

    } finally {
      factory.stopContainers(s -> System.out.println(">> " + s));
    }
  }

  private void exeSql(Connection connection, String sql) throws SQLException {
    PreparedStatement st = connection.prepareStatement(sql);
    st.execute();
    st.close();
  }
}
