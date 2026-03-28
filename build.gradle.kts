plugins {
    java
    pmd
    id("com.gradleup.shadow") version "9.3.2"
    id("xyz.jpenilla.run-paper") version "2.3.1"
    // Note: net.ltgt.errorprone plugin is disabled because it requires a JDK with
    // compiler API (javac). The VPS only has a JRE. CI has a full JDK 21 so it
    // can be re-enabled there via gradle.properties if needed.
    // id("net.ltgt.errorprone") version "4.1.0"
    // Note: foojay-resolver-convention removed because it downloads a minimal JDK
    // without javac. CI manages its own JDK 21 installation.
    // id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

group = property("group") as String
version = property("version") as String

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")

    // Paper API
    compileOnly("io.papermc.paper:paper-api:${property("paperVersion")}")
    testImplementation("io.papermc.paper:paper-api:${property("paperVersion")}")

    // Vault API (economy integration)
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    testImplementation("com.github.MilkBowl:VaultAPI:1.7.1")

    // PlaceholderAPI (optional — exposes prices, trends, economy stats as placeholders)
    compileOnly("me.clip:placeholderapi:2.11.6")

    // Command Framework - Cloud
    implementation("org.incendo:cloud-core:${property("cloudCoreVersion")}")
    implementation("org.incendo:cloud-paper:${property("cloudPaperVersion")}")
    implementation("org.incendo:cloud-annotations:${property("cloudCoreVersion")}")
    implementation("org.incendo:cloud-minecraft-extras:${property("cloudPaperVersion")}")

    // Dependency Injection
    implementation("com.google.inject:guice:${property("guiceVersion")}")

    // Database
    implementation("com.zaxxer:HikariCP:${property("hikariVersion")}")
    implementation("org.jdbi:jdbi3-core:${property("jdbiVersion")}")
    implementation("org.jdbi:jdbi3-sqlobject:${property("jdbiVersion")}")
    implementation("org.xerial:sqlite-jdbc:${property("sqliteVersion")}")
    implementation("org.mariadb.jdbc:mariadb-java-client:${property("mariadbVersion")}")

    // Web Server
    implementation("io.javalin:javalin:${property("javalinVersion")}")
    implementation("com.google.code.gson:gson:${property("gsonVersion")}")

    // Inventory GUI Framework
    implementation("com.github.stefvanschie.inventoryframework:IF:${property("inventoryFrameworkVersion")}")

    // Annotations
    compileOnly("org.jetbrains:annotations:26.0.1")
}

pmd {
    toolVersion = "7.14.0"
    isConsoleOutput = true
    isIgnoreFailures = true
    // Focus on Error Prone rules only — these catch actual bugs.
    // Code Style (2900+ violations), Design, Best Practices, and Performance
    // are non-blocking style issues that add noise and bury real problems.
    ruleSets = listOf("category/java/errorprone.xml")
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    val installWebDeps by registering(Exec::class) {
        workingDir = file("web")
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val npm = if (isWindows) "npm.cmd" else "npm"
        val lockFile = file("web/package-lock.json")

        commandLine(
            npm,
            if (lockFile.exists()) "ci" else "install",
            "--no-audit",
            "--no-fund"
        )
    }

    val buildWeb by registering(Exec::class) {
        dependsOn(installWebDeps)
        workingDir = file("web")
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        commandLine(if (isWindows) "npm.cmd" else "npm", "run", "export")
    }

    shadowJar {
        archiveClassifier.set("")

        relocate("org.incendo.cloud", "com.noahblclarkson.autotune.lib.cloud")
        relocate("com.zaxxer.hikari", "com.noahblclarkson.autotune.lib.hikari")
        relocate("org.jdbi", "com.noahblclarkson.autotune.lib.jdbi")
        relocate("io.javalin", "com.noahblclarkson.autotune.lib.javalin")
        relocate("org.eclipse.jetty", "com.noahblclarkson.autotune.lib.jetty")
        relocate("com.github.stefvanschie.inventoryframework", "com.noahblclarkson.autotune.lib.inventoryframework")
        relocate("com.google.inject", "com.noahblclarkson.autotune.lib.guice")

        minimize {
            exclude(dependency("org.xerial:.*"))
            exclude(dependency("org.mariadb.jdbc:.*"))
            exclude(dependency("io.javalin:.*"))
            exclude(dependency("org.eclipse.jetty:.*"))
        }
    }

    processResources {
        dependsOn(buildWeb)
        from(file("web/out")) {
            into("web")
        }

        val props = mapOf(
            "version" to version,
            "name" to rootProject.name
        )
        inputs.properties(props)
        filesMatching(listOf("paper-plugin.yml", "plugin.yml")) {
            expand(props)
        }
    }

    runServer {
        minecraftVersion("1.21.4")
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    test {
        useJUnitPlatform()
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf(
        "-Xlint:all",
        "-Xlint:-processing",
        "-Xlint:-classfile"
    ))
}
