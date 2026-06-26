import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import versioning.BuildConfig

plugins {
    id("com.gradleup.shadow")
}

tasks.named<ShadowJar>("shadowJar") {
    minimize()
    archiveFileName = "${rootProject.name}-${project.name}-${rootProject.version}.jar"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    if (BuildConfig.relocate) {
        if (BuildConfig.shadePE) {
            relocate("io.github.retrooper.packetevents", "ac.jester.anticheat.shaded.io.github.retrooper.packetevents")
            relocate("com.github.retrooper.packetevents", "ac.jester.anticheat.shaded.com.github.retrooper.packetevents")
        }
        // Adventure (text/colour). In lite mode it's bundled directly, so relocate
        // it under shaded.* too — removes the readable top-level net/kyori tree
        // from the distributed jar. (ProGuard keeps shaded.* intact so it works.)
        relocate("net.kyori", "ac.jester.anticheat.shaded.kyori")
        // The Grim engine API/glue our forked code links against. Relocating it
        // strips the "grim" package from the distributed jar entirely (the source
        // still imports ac.grim.grimac — that's compile-time only). Target is
        // under shaded.* so ProGuard keeps it working rather than obfuscating it.
        relocate("ac.grim.grimac", "ac.jester.anticheat.shaded.core")
        // slf4j is intentionally NOT shaded — HikariCP references org.slf4j and
        // Paper already provides slf4j-api with a working (log4j) provider.
        // Shading/relocating it isolated HikariCP from that provider, producing
        // the "No SLF4J providers were found" warning on stderr at startup.
        relocate("github.scarsz.configuralize", "ac.jester.anticheat.shaded.configuralize")
        relocate("com.github.puregero", "ac.jester.anticheat.shaded.com.github.puregero")
        relocate("com.google.code.gson", "ac.jester.anticheat.shaded.gson")
        relocate("alexh", "ac.jester.anticheat.shaded.maps")
        relocate("it.unimi.dsi.fastutil", "ac.jester.anticheat.shaded.fastutil")
        relocate("okhttp3", "ac.jester.anticheat.shaded.okhttp3")
        relocate("okio", "ac.jester.anticheat.shaded.okio")
        relocate("org.yaml.snakeyaml", "ac.jester.anticheat.shaded.snakeyaml")
        relocate("org.json", "ac.jester.anticheat.shaded.json")
        relocate("org.intellij", "ac.jester.anticheat.shaded.intellij")
        relocate("org.jetbrains", "ac.jester.anticheat.shaded.jetbrains")
        relocate("org.incendo", "ac.jester.anticheat.shaded.incendo")
        relocate("io.leangen.geantyref", "ac.jester.anticheat.shaded.geantyref") // Required by cloud
        relocate("com.zaxxer", "ac.jester.anticheat.shaded.zaxxer") // Database history
    }
    // Don't bundle slf4j-api — use Paper's, which has a bound provider
    dependencies {
        exclude(dependency("org.slf4j:slf4j-api"))
    }
    mergeServiceFiles()
}

tasks.named("assemble") {
    dependsOn(tasks.named("shadowJar"))
}
