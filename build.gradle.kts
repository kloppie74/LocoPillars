plugins {
    kotlin("jvm") version "1.9.23"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "org.Kloppie74"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
    maven("https://repo.codemc.io/repository/maven-public/") {
        name = "codemc-repo"
    }
    maven("https://jitpack.io") {
        name = "jitpack"
    }
    maven("https://maven.enginehub.org/repo/") {
        name = "enginehub-repo"
    }
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") {
        name = "placeholderapi-repo"
    }

}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.5-R0.1-SNAPSHOT")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.10")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.14")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // =================== DEVELOPMENT DEPENDENCIES ===================

    // Testing Framework
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Test Database (H2 for unit tests)
    testImplementation("com.h2database:h2:2.2.224")

    // ==================== CORE DEPENDENCIES ===================

    compileOnly("com.github.NEZNAMY", "TAB-API", "5.2.5")
    compileOnly("me.clip", "placeholderapi", "2.11.6")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")

    // =================== DATABASE DEPENDENCIES ===================

    // Connection Pooling - HikariCP (Industry Standard)
    implementation("com.zaxxer:HikariCP:5.0.1")

    // SQLite Database Driver
    implementation("org.xerial:sqlite-jdbc:3.43.0.0")

    // MySQL Database Driver
    implementation("mysql:mysql-connector-java:8.0.33")

    // MariaDB Database Driver
    implementation("org.mariadb.jdbc:mariadb-java-client:3.2.0")

    // H2 Database Driver (In-Memory/File)
    implementation("com.h2database:h2:2.2.224")

    // PostgreSQL Database Driver
    implementation("org.postgresql:postgresql:42.6.0")

    // Database Migration Library (Optional - for advanced migrations)
    implementation("org.flywaydb:flyway-core:9.22.3")
    implementation("org.flywaydb:flyway-mysql:9.22.3")

    // JSON Processing (for complex data storage)
    implementation("com.fasterxml.jackson.core:jackson-core:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks {
    runServer {
        minecraftVersion("1.21.5")
    }
    build {
        dependsOn("shadowJar")
    }
    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}