package org.avaje.docker.commands;

import java.util.Properties;

public class DbConfigFactory {

	/**
	 * Create configuration based on properties.
	 *
	 * A dbPlatform property is generally required.
	 */
	public static DbConfig create(Properties properties) {

		return new DbConfigFactory().createWithDefaults(properties);
	}

	/**
	 * Create a DbConfig with appropriate defaults for the given dbType (postgres, mysql, oracle etc)
	 * and potentially configuration from properties.
	 */
	public DbConfig createWithDefaults(Properties properties) {

		DbConfig dbConfig = new DbConfig();

		if (properties != null) {
			String dbPlatform = properties.getProperty("dbPlatform");
			if (dbPlatform != null) {
				applyDefaults(dbPlatform, dbConfig);
				dbConfig.withProperties(properties);
			}
		}

		return dbConfig;
	}

	private DbConfig applyDefaults(String dbType, DbConfig dbConfig) {
		if ("postgres".equalsIgnoreCase(dbType)) {
			return applyPostgresDefaults(dbConfig);

		} else if ("mysql".equalsIgnoreCase(dbType)) {
			return applyMySqlDefaults(dbConfig);

		} else if ("oracle".equalsIgnoreCase(dbType)) {
			return applyOracleDefaults(dbConfig);
		}

		// default to using postgres
		applyPostgresDefaults(dbConfig);
		return dbConfig;
	}

	private DbConfig applyOracleDefaults(DbConfig dbConfig) {
		// TODO Oracle defaults
		return dbConfig;
	}

	private DbConfig applyMySqlDefaults(DbConfig dbConfig) {
		// TODO MySql defaults
		return dbConfig;
	}

	private DbConfig applyPostgresDefaults(DbConfig dbConfig) {

		dbConfig.image = "postgres:9.6.4";
		dbConfig.name = "ut_postgres";
		dbConfig.dbPort = "6432";
		dbConfig.internalPort = "5432";
		dbConfig.tmpfs = "/var/lib/postgresql/data:rw";

		return dbConfig;
	}

	/**
	 * Create DbCommands based on the platform (Postgres, MySql etc).
	 */
	public static DbCommands createCommands(DbConfig dbConfig) {

		String platform = dbConfig.platform.toLowerCase().trim();
		switch (platform) {
			case "postgres": return new PostgresCommands(dbConfig);
			case "mysql": return new MySqlCommands(dbConfig);
			default: throw new IllegalArgumentException("Unknown DB platform "+platform);
		}
	}
}
