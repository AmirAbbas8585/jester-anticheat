import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission
import versioning.BuildConfig

plugins {
    `maven-publish`
    jester.`base-conventions`
    jester.`shadow-conventions`
    id("de.eldoria.plugin-yml.bukkit") version "0.8.0"
    id("xyz.jpenilla.run-paper") version "3.0.0-beta.1"
}

repositories {
    // 1. Fallback for non-exclusive deps (e.g. Maven Central deps)
    if (BuildConfig.mavenLocalOverride) mavenLocal()

    // 2. Exclusive Repositories (One HTTP request per dep)
    exclusive("https://repo.papermc.io/repository/maven-public/", { name = "papermc" }) {
        includeGroup("io.papermc.paper")
        includeGroup("net.md-5")
    }

    exclusive("https://libraries.minecraft.net", { mavenContent { releasesOnly() } }) {
        includeModule("com.mojang", "brigadier")
    }

    exclusive("https://repo.extendedclip.com/content/repositories/placeholderapi/") {
        includeGroup("me.clip")
    }

    // Grim artifacts from grim snapshots; PacketEvents releases from CodeMC
    exclusiveContent {
        if (BuildConfig.mavenLocalOverride) {
            forRepositories(
                mavenLocal(),
                maven("https://repo.grim.ac/snapshots"),
                maven("https://repo.codemc.io/repository/maven-releases/")
            )
        } else {
            forRepositories(
                maven("https://repo.grim.ac/snapshots"),
                maven("https://repo.codemc.io/repository/maven-releases/")
            )
        }
        filter {
            includeGroup("ac.grim.grimac")
            includeGroup("com.github.retrooper")
        }
    }

    exclusive("https://nexus.scarsz.me/content/repositories/releases", { mavenContent { releasesOnly() } }) {
        includeGroup("github.scarsz")
    }

    mavenCentral()
}


dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.placeholderapi)

    if (BuildConfig.shadePE) {
        implementation(libs.packetevents.spigot)
    } else {
        compileOnly(libs.packetevents.spigot)
    }
    implementation(libs.cloud.paper)
    implementation(libs.adventure.platform.bukkit)
    implementation(libs.grim.bukkit.internal)

    implementation(project(":common"))
    shadow(project(":common"))
}

bukkit {
    name = "JesterAntiCheat"
    author = "Jester"
    main = "ac.jester.anticheat.platform.bukkit.JesterAntiCheatPlugin"
    website = "https://modrinth.com/plugin/jester-anticheat"
    // Minimum server version. Bukkit has no "max" field — the plugin loads on
    // this version and every newer one automatically. Matches upstream Grim
    // (1.13), so the load range is the same as the Grim engine itself; servers
    // older than 1.13 don't enforce this field and load it in legacy mode, and
    // pre-1.13 clients are covered via ViaVersion on a modern server.
    apiVersion = "1.13"
    foliaSupported = true

    if (!BuildConfig.shadePE) {
        depend = listOf("packetevents")
    }

    softDepend = listOf(
        "ProtocolLib",
        "ProtocolSupport",
        "Essentials",
        "ViaVersion",
        "ViaBackwards",
        "ViaRewind",
        "FastLogin",
        "PlaceholderAPI",
        "GSit",
        "DeluxeCombat",
        "AuraSkills",
        "CrazyEnchantments",
        "WorldGuard",
        "LPX",
        "ItemsAdder",
        "Slimefun",
        "PremiumVanish",
        "AxiomPaper",
    )

    permissions {
        register("jester.violations") {
            description = "View per-check violation breakdown for a player"
            default = Permission.Default.OP
        }

        register("jester.info") {
            description = "View detailed real-time player state"
            default = Permission.Default.OP
        }

        // Staff-notification permissions are LuckPerms-driven, NOT op-based:
        // default FALSE so ONLY players explicitly granted the node (e.g. via
        // LuckPerms) receive alerts. Ops without the node get nothing.
        register("jester.alerts") {
            description = "Receive violation alerts (auto-enabled on join)"
            default = Permission.Default.FALSE
        }

        register("jester.verbose") {
            description = "Toggle verbose alerts"
            default = Permission.Default.FALSE
        }

        // Verbose is spammy (every flag), so it stays opt-in: needs this node too
        register("jester.verbose.enable-on-join") {
            description = "Enable verbose alerts on join"
            default = Permission.Default.FALSE
        }

        register("jester.brand") {
            description = "Receive client brand alerts (auto-enabled on join)"
            default = Permission.Default.FALSE
        }

        register("jester.freeze") {
            description = "Freeze/unfreeze players"
            default = Permission.Default.OP
        }

        register("jester.profile") {
            description = "View player profile"
            default = Permission.Default.OP
        }

        register("jester.performance") {
            description = "View performance metrics"
            default = Permission.Default.OP
        }

        register("jester.cps") {
            description = "View player CPS"
            default = Permission.Default.OP
        }

        register("jester.list") {
            description = "List tracked players"
            default = Permission.Default.OP
        }

        register("jester.knockback") {
            description = "View knockback debug info"
            default = Permission.Default.OP
        }

        register("jester.tp") {
            description = "Teleport to a player"
            default = Permission.Default.OP
        }

        register("jester.rotate") {
            description = "Rotate a player's head"
            default = Permission.Default.OP
        }

        register("jester.update") {
            description = "Be notified on join when a plugin update is available"
            default = Permission.Default.OP
        }

        register("jester.stats") {
            description = "View server-wide anticheat stats"
            default = Permission.Default.OP
        }

        register("jester.setback") {
            description = "Manually setback a player"
            default = Permission.Default.OP
        }

        register("jester.clearviolations") {
            description = "Clear all violations for a player"
            default = Permission.Default.OP
        }

        register("jester.help") {
            description = "View help information"
            default = Permission.Default.TRUE
        }

        register("jester.history") {
            description = "View violation history"
            default = Permission.Default.OP
        }

        register("jester.version") {
            description = "View plugin version"
            default = Permission.Default.OP
        }

        register("jester.spectate") {
            description = "Spectate players"
            default = Permission.Default.OP
        }

        register("jester.spectate.stophere") {
            description = "Stop spectating at current position"
            default = Permission.Default.OP
        }

        register("jester.reload") {
            description = "Reload configuration"
            default = Permission.Default.OP
        }

        register("jester.log") {
            description = "View/toggle check logs"
            default = Permission.Default.OP
        }

        register("jester.dump") {
            description = "Dump player data for debugging"
            default = Permission.Default.OP
        }

        register("jester.debug") {
            description = "Toggle debug mode for a player"
            default = Permission.Default.OP
        }

        register("jester.logs") {
            description = "Open the violation log GUI"
            default = Permission.Default.OP
        }

        register("jester.exempt") {
            description = "Exempt from all checks"
            default = Permission.Default.FALSE
        }

        register("jester.nosetback") {
            description = "Disable setback"
            default = Permission.Default.FALSE
        }

        register("jester.nomodifypacket") {
            description = "Disable packet modification"
            default = Permission.Default.FALSE
        }

    }
}

publishing.publications.create<MavenPublication>("maven") {
    artifact(tasks["shadowJar"])
}

tasks {
    runServer {
        minecraftVersion("1.21.4")
    }

    shadowJar {
        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Obfuscated build for public distribution (Modrinth).
//
// Two artifacts from one codebase:
//   • normal:      ./gradlew :bukkit:shadowJar           (own server, readable)
//   • obfuscated:  ./gradlew :bukkit:proguardJar -Pobfuscate=true   (Modrinth)
//
// proguardJar takes the shaded jar and runs ProGuard over it, renaming only our
// own ac.jester.anticheat.** source (libraries are left intact) and stripping
// debug info, producing *-obf.jar — non-trivially-decompilable, source private.
// Rules live in bukkit/proguard-rules.pro. TEST ON A SERVER BEFORE PUBLISHING.
// ─────────────────────────────────────────────────────────────────────────────
val proguardJar = tasks.register<proguard.gradle.ProGuardTask>("proguardJar") {
    group = "build"
    description = "Obfuscates the shaded jar for public (Modrinth) distribution."

    val shadow = tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar")
    dependsOn(shadow)

    // Strip the own-server-only region AFK feature from the public obf jar.
    // (JesterAntiCheatPlugin tolerates the class being absent at runtime.)
    injars(mapOf("filter" to "!ac/jester/anticheat/platform/bukkit/afk/**"),
            shadow.flatMap { it.archiveFile })
    val obfName = "${rootProject.name}-${rootProject.version}-obf.jar"
    outjars(layout.buildDirectory.file("libs/$obfName"))

    // The JDK itself as library references (modules our code + deps touch).
    // Use the project's Java 21 TOOLCHAIN jmods, NOT the JVM running Gradle —
    // that may be much newer (e.g. JDK 25), whose class-file version ProGuard
    // can't yet read. The plugin's bytecode is Java 21, so 21 library refs match.
    val toolchainHome = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    }.get().metadata.installationPath.asFile.absolutePath
    listOf(
        "java.base", "java.sql", "java.naming", "java.logging",
        "java.management", "java.desktop", "jdk.unsupported"
    ).forEach { module ->
        libraryjars(
            mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"),
            "$toolchainHome/jmods/$module.jmod"
        )
    }
    // compileOnly deps (paper-api, and packetevents in lite mode) aren't inside
    // the shaded jar — feed the compile classpath so references resolve. Any
    // overlap with already-shaded classes is harmless (-ignorewarnings).
    libraryjars(configurations.getByName("compileClasspath"))

    configuration(file("proguard-rules.pro"))
    printmapping(layout.buildDirectory.file("proguard/mapping.txt"))
}

// When -Pobfuscate=true, the standard assemble/build also produces the obf jar.
if (BuildConfig.obfuscate) {
    tasks.named("assemble") { dependsOn(proguardJar) }
}
