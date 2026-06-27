import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import versioning.BuildConfig

plugins {
    id("com.gradleup.shadow")
}

tasks.named<ShadowJar>("shadowJar") {
    minimize()
    archiveFileName = "${rootProject.name}-${rootProject.version}.jar"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    if (BuildConfig.relocate) {
        if (BuildConfig.shadePE) {
            relocate("io.github.retrooper.packetevents", "ac.jester.anticheat.shaded.io.github.retrooper.packetevents")
            relocate("com.github.retrooper.packetevents", "ac.jester.anticheat.shaded.com.github.retrooper.packetevents")
            // Adventure may ONLY be relocated when we also shade PacketEvents.
            // In lite mode the plugin serializes messages through PacketEvents'
            // OWN bundled Adventure serializer, which expects the real (un-
            // relocated) net.kyori Component — relocating it there causes
            // NoSuchMethodError on every alert and disconnects the player.
            relocate("net.kyori", "ac.jester.anticheat.shaded.kyori")
        }
        // The bundled engine API/glue our forked code links against. shadowJar
        // relocation (unlike ProGuard obfuscation) rewrites class references AND
        // the string constants used by the engine's own reflection, so it can
        // rename both the package AND the brand-named classes without breaking it.
        // The specific per-class rules below (longest-first so prefixes like
        // GrimEvent/GrimEventHandler don't collide) rename the brand-named classes;
        // the trailing package rule moves everything else. Source still imports
        // ac.grim.grimac — that's compile-time only, against the API dependency.
        relocate("ac.grim.grimac.internal.plugin.resolver.GrimExtensionResolver", "ac.jester.anticheat.shaded.core.internal.plugin.resolver.JesterExtensionResolver")
        relocate("ac.grim.grimac.internal.plugin.resolver.GrimExtensionManager", "ac.jester.anticheat.shaded.core.internal.plugin.resolver.JesterExtensionManager")
        relocate("ac.grim.grimac.api.event.events.GrimVerboseCheckEvent", "ac.jester.anticheat.shaded.core.api.event.events.JesterVerboseCheckEvent")
        relocate("ac.grim.grimac.api.event.events.GrimReloadEvent", "ac.jester.anticheat.shaded.core.api.event.events.JesterReloadEvent")
        relocate("ac.grim.grimac.api.plugin.GrimPluginDescription", "ac.jester.anticheat.shaded.core.api.plugin.JesterPluginDescription")
        relocate("ac.grim.grimac.api.event.events.GrimCheckEvent", "ac.jester.anticheat.shaded.core.api.event.events.JesterCheckEvent")
        relocate("ac.grim.grimac.api.event.events.GrimJoinEvent", "ac.jester.anticheat.shaded.core.api.event.events.JesterJoinEvent")
        relocate("ac.grim.grimac.api.event.events.GrimQuitEvent", "ac.jester.anticheat.shaded.core.api.event.events.JesterQuitEvent")
        relocate("ac.grim.grimac.api.event.events.GrimUserEvent", "ac.jester.anticheat.shaded.core.api.event.events.JesterUserEvent")
        relocate("ac.grim.grimac.api.event.GrimEventListener", "ac.jester.anticheat.shaded.core.api.event.JesterEventListener")
        relocate("ac.grim.grimac.api.plugin.BasicGrimPlugin", "ac.jester.anticheat.shaded.core.api.plugin.BasicJesterPlugin")
        relocate("ac.grim.grimac.api.event.GrimEventHandler", "ac.jester.anticheat.shaded.core.api.event.JesterEventHandler")
        relocate("ac.grim.grimac.api.events.GrimReloadEvent", "ac.jester.anticheat.shaded.core.api.events.JesterReloadEvent")
        relocate("ac.grim.grimac.api.events.GrimUserEvent", "ac.jester.anticheat.shaded.core.api.events.JesterUserEvent")
        relocate("ac.grim.grimac.api.events.GrimQuitEvent", "ac.jester.anticheat.shaded.core.api.events.JesterQuitEvent")
        relocate("ac.grim.grimac.api.events.GrimJoinEvent", "ac.jester.anticheat.shaded.core.api.events.JesterJoinEvent")
        relocate("ac.grim.grimac.api.plugin.GrimPlugin", "ac.jester.anticheat.shaded.core.api.plugin.JesterPlugin")
        relocate("ac.grim.grimac.api.event.GrimEvent", "ac.jester.anticheat.shaded.core.api.event.JesterEvent")
        relocate("ac.grim.grimac.api.GrimAbstractAPI", "ac.jester.anticheat.shaded.core.api.JesterAbstractAPI")
        relocate("ac.grim.grimac.api.GrimAPIProvider", "ac.jester.anticheat.shaded.core.api.JesterAPIProvider")
        relocate("ac.grim.grimac.api.GrimIdentity", "ac.jester.anticheat.shaded.core.api.JesterIdentity")
        relocate("ac.grim.grimac.api.GrimUser", "ac.jester.anticheat.shaded.core.api.JesterUser")
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
