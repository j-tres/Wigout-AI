import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.plugins.jvm.JvmTestSuite

plugins {
    // Apply the Java plugin to add support for Java
    java
    id("com.gradleup.shadow") version "8.3.0"
}

group = "org.wigout"
version = "0.10.1-j-tres.1"

repositories {
    mavenCentral()

    // Add the Bitwig Maven repository for the extension API
    maven {
        url = uri("https://maven.bitwig.com")
    }
}

dependencies {
    // Bitwig Extension API
    implementation("com.bitwig:extension-api:25")

    // MCP Java SDK
    // you can look up the documentation with tool context7
    // example implementation: https://modelcontextprotocol.io/sdk/java/mcp-server
    implementation(platform("io.modelcontextprotocol.sdk:mcp-bom:0.11.0"))
    implementation("io.modelcontextprotocol.sdk:mcp")
    implementation("jakarta.servlet:jakarta.servlet-api:6.0.0")
    testImplementation("io.modelcontextprotocol.sdk:mcp-test")

    // Jetty 11 for embedded server and servlet support (EE9)
    implementation("org.eclipse.jetty:jetty-server:11.0.20")
    implementation("org.eclipse.jetty:jetty-servlet:11.0.20")

    // Use JUnit Jupiter for testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}

java {
    // Configure Java 21 LTS
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    // Policy: API v25 only, no deprecated calls (deprecated calls halt the
    // extension at runtime in Bitwig 6.x). Fail the build on any deprecation.
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Werror"))
}

// Configure testing using the Test Suites DSL (avoids deprecated auto-loading in Gradle 9)
testing {
    suites {
        // Configure the built-in 'test' suite to use JUnit Jupiter
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
    }
}

// Configure the Shadow JAR (fat JAR with all dependencies)
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveFileName.set("wigout-mcp-all-${project.version}.jar")
    manifest.inheritFrom(tasks.named<org.gradle.api.tasks.bundling.Jar>("jar").get().manifest)
    mergeServiceFiles() // Merge META-INF/services for SPI
}

// Task to create the .bwextension file
tasks.register<Jar>("bwextension") {
    group = "build"
    description = "Creates the .bwextension file, a JAR with a proper manifest and all dependencies."

    dependsOn(tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar"))

    archiveFileName.set("Wigout.bwextension")

    destinationDirectory.set(layout.buildDirectory.dir("extensions"))

    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version.toString(),
            "Implementation-Vendor" to project.group.toString(),
            "Created-By" to "Gradle ${gradle.gradleVersion}",
        )
    }

    // Include all content from the shadowJar.
    // This makes Wigout.bwextension effectively BE the shadow JAR,
    // but with the desired name and the manifest defined directly above.
    from(tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar").map { zipTree(it.archiveFile) })
}

// Make the build task also create the .bwextension file
tasks.named("build") {
    dependsOn("bwextension")
}

// Resolve Bitwig's Extensions folder per OS. On Windows with OneDrive-redirected
// Documents, Bitwig reads %OneDrive%\Documents\..., NOT %USERPROFILE%\Documents\...
// — target the redirected path when OneDrive is present, else fall back to the plain
// Documents folder. macOS/Linux paths below are best-effort and not live-verified in
// this environment (see docs/superpowers/specs/2026-07-31-open-source-distribution-design.md,
// section D) - contributors on those platforms should confirm and report back.
val bitwigExtensionsDir: String = run {
    val home = System.getProperty("user.home")
    val osName = System.getProperty("os.name").lowercase()
    when {
        osName.contains("win") -> {
            val oneDrive = System.getenv("OneDrive") ?: System.getenv("OneDriveConsumer")
            val base = if (oneDrive != null && file("$oneDrive/Documents").isDirectory) "$oneDrive/Documents" else "$home/Documents"
            "$base/Bitwig Studio/Extensions"
        }
        osName.contains("mac") -> "$home/Documents/Bitwig Studio/Extensions"
        else -> "$home/Bitwig Studio/Extensions"
    }
}

tasks.register<Copy>("deploy") {
    group = "build"
    description = "Copies Wigout.bwextension into Bitwig's Extensions folder."
    dependsOn("bwextension")
    from(layout.buildDirectory.dir("extensions"))
    include("Wigout.bwextension")
    into(bitwigExtensionsDir)
    doLast { println("[deploy] copied Wigout.bwextension -> $bitwigExtensionsDir") }
}
