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
    if (BuildConfig.mavenLocalOverride) mavenLocal()

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

        register("jester.alerts") {
            description = "Receive violation alerts (auto-enabled on join)"
            default = Permission.Default.FALSE
        }

        register("jester.verbose") {
            description = "Toggle verbose alerts"
            default = Permission.Default.FALSE
        }

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
