import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    pmd
    id("com.gradleup.shadow") version "9.3.1"
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("net.ltgt.errorprone") version "5.0.0"
}

group = property("group") as String
version = property("version") as String

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")

    // Paper API
    compileOnly("io.papermc.paper:paper-api:${property("paperVersion")}")

    // Vault API (economy integration)
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

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

    // Error Prone (compile-time static analysis)
    errorprone("com.google.errorprone:error_prone_core:${property("errorproneVersion")}")
}

pmd {
    toolVersion = "7.14.0"
    isConsoleOutput = true
    // Use PMD's built-in category rulesets (no custom file needed)
    ruleSets = listOf(
        "category/java/bestpractices.xml",
        "category/java/codestyle.xml",
        "category/java/design.xml",
        "category/java/errorprone.xml",
        "category/java/performance.xml"
    )
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

        // Relocate dependencies to avoid conflicts with other plugins
        relocate("org.incendo.cloud", "com.noahblclarkson.autotune.lib.cloud")
        relocate("com.zaxxer.hikari", "com.noahblclarkson.autotune.lib.hikari")
        relocate("org.jdbi", "com.noahblclarkson.autotune.lib.jdbi")
        relocate("io.javalin", "com.noahblclarkson.autotune.lib.javalin")
        relocate("org.eclipse.jetty", "com.noahblclarkson.autotune.lib.jetty")
        relocate("com.github.stefvanschie.inventoryframework", "com.noahblclarkson.autotune.lib.inventoryframework")
        relocate("com.google.inject", "com.noahblclarkson.autotune.lib.guice")


        // Minimize JAR size but exclude drivers and web server
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
        options.release.set(17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-classfile"))
    options.errorprone.disableWarningsInGeneratedCode = true
}

tasks.test {
    useJUnitPlatform()
}
