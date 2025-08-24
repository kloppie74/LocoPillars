package org.Kloppie74.locoPillars.database

/**
 * Database configuration data class.
 * Contains all database connection and pool settings.
 *
 * @author kloppie74
 * @since 1.0-SNAPSHOT
 */
data class DatabaseConfig(
    // Connection settings
    val type: String = "sqlite",
    val host: String = "localhost",
    val port: Int = 3306,
    val database: String = "locopillars",
    val username: String = "",
    val password: String = "",
    val useSSL: Boolean = false,

    // Connection pool settings - FIXED NAMING
    val maxPoolSize: Int = 10,           // FIXED: was maximumPoolSize
    val minPoolSize: Int = 5,            // FIXED: was minimumIdle
    val connectionTimeoutMs: Long = 30000L,  // FIXED: was connectionTimeout
    val idleTimeoutMs: Long = 600000L,       // FIXED: was idleTimeout
    val maxLifetimeMs: Long = 1800000L,      // FIXED: was maxLifetime
    val leakDetectionThresholdMs: Long = 60000L, // FIXED: was leakDetectionThreshold

    // Maintenance settings
    val cleanupEnabled: Boolean = true,
    val cleanupDays: Int = 30,

    // Performance settings
    val enableQueryLogging: Boolean = false,
    val slowQueryThresholdMs: Long = 1000L,

    // Cache settings
    val cacheSize: Int = 1000,
    val cacheExpirationMinutes: Int = 30
) {

    /**
     * Validates the database configuration.
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        // Validate database type
        if (type.lowercase() !in listOf("sqlite", "h2", "mysql", "mariadb", "postgresql")) {
            errors.add("Unsupported database type: $type")
        }

        // Validate network database settings
        if (type.lowercase() in listOf("mysql", "mariadb", "postgresql")) {
            if (host.isBlank()) {
                errors.add("Host cannot be empty for $type database")
            }
            if (port < 1 || port > 65535) {
                errors.add("Invalid port number: $port")
            }
            if (database.isBlank()) {
                errors.add("Database name cannot be empty")
            }
            if (username.isBlank()) {
                errors.add("Username cannot be empty for $type database")
            }
        }

        // Validate pool settings
        if (maxPoolSize < 1) {
            errors.add("Maximum pool size must be at least 1")
        }
        if (minPoolSize < 0) {
            errors.add("Minimum pool size cannot be negative")
        }
        if (minPoolSize > maxPoolSize) {
            errors.add("Minimum pool size cannot be greater than maximum pool size")
        }
        if (connectionTimeoutMs < 1000) {
            errors.add("Connection timeout must be at least 1000ms")
        }
        if (idleTimeoutMs < 10000) {
            errors.add("Idle timeout must be at least 10000ms")
        }
        if (maxLifetimeMs < 30000) {
            errors.add("Max lifetime must be at least 30000ms")
        }

        // Validate cleanup settings
        if (cleanupDays < 1) {
            errors.add("Cleanup days must be at least 1")
        }

        // Validate performance settings
        if (slowQueryThresholdMs < 100) {
            errors.add("Slow query threshold must be at least 100ms")
        }

        // Validate cache settings
        if (cacheSize < 10) {
            errors.add("Cache size must be at least 10")
        }
        if (cacheExpirationMinutes < 1) {
            errors.add("Cache expiration must be at least 1 minute")
        }

        return errors
    }

    /**
     * Gets a summary of the configuration for logging.
     */
    fun getSummary(): Map<String, Any> {
        return mapOf(
            "type" to type,
            "host" to if (type in listOf("mysql", "mariadb", "postgresql")) host else "N/A",
            "port" to if (type in listOf("mysql", "mariadb", "postgresql")) port else "N/A",
            "database" to database,
            "max_pool_size" to maxPoolSize,
            "min_pool_size" to minPoolSize,
            "connection_timeout_ms" to connectionTimeoutMs,
            "cleanup_enabled" to cleanupEnabled,
            "cleanup_days" to cleanupDays
        )
    }

    /**
     * Creates a JDBC URL for the configured database.
     */
    fun getJdbcUrl(dataFolder: String): String {
        return when (type.lowercase()) {
            "sqlite" -> "jdbc:sqlite:$dataFolder/$database.db"
            "h2" -> "jdbc:h2:$dataFolder/$database;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
            "mysql" -> "jdbc:mysql://$host:$port/$database?useSSL=$useSSL&allowPublicKeyRetrieval=true&serverTimezone=UTC"
            "mariadb" -> "jdbc:mariadb://$host:$port/$database?useSSL=$useSSL&serverTimezone=UTC"
            "postgresql" -> "jdbc:postgresql://$host:$port/$database?ssl=$useSSL&serverTimezone=UTC"
            else -> throw IllegalArgumentException("Unsupported database type: $type")
        }
    }
}