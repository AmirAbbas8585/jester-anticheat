plugins {
    `kotlin-dsl`
    kotlin("plugin.serialization") version "2.2.20"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spotless)
    implementation(libs.lombok)
    implementation(libs.shadow)
    // ProGuard — obfuscates the public Modrinth jar (gradle task in :bukkit,
    // gated behind -Pobfuscate=true). Kept on the build classpath via buildSrc.
    implementation("com.guardsquare:proguard-gradle:7.6.1")
    // kotlinx-serialization-json 1.8.x is compatible with Kotlin 2.2.x
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}
