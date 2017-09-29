# docker-commands
Ability to control docker containers. e.g. Postgres running as docker container for testing 


## AutoStart use
1. Add a docker-run.properties 
2. Execute AutoStart.run()

Example docker-run.properties

```properties

elastic.version=5.6.0

postgres.version=9.6
postgres.dbName=junk_db
postgres.dbUser=rob
postgres.dbExtensions=hstore,pgcrypto
postgres.port=6432

```

## ContainerFactory use

Example use of ContainerFactory.

```java

    Properties properties = new Properties();
    properties.setProperty("postgres.version", "9.6");
    properties.setProperty("postgres.containerName", "junk_postgres");
    properties.setProperty("postgres.port", "9823");

    properties.setProperty("elastic.version", "5.6.0");
    properties.setProperty("elastic.port", "9201");

//    properties.setProperty("mysql.version", "5.7");
//    properties.setProperty("mysql.containerName", "temp_mysql");
//    properties.setProperty("mysql.port", "7306");


    ContainerFactory factory = new ContainerFactory(properties);

    // start all containers
    factory.startContainers();

    // get a container
    Container postgres = factory.container("postgres");

    // for a DB container we can get JDBC URL & Connection
    String jdbcUrl = postgres.config().jdbcUrl();
    Connection connection = postgres.config().createConnection();
    connection.close();

    // stop all containers
    factory.stopContainers();


```
