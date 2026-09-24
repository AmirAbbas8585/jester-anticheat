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
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}
