plugins {
    // Resolves the Java 21 toolchain automatically (no global JDK 21 needed).
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "taskflow-backend"
