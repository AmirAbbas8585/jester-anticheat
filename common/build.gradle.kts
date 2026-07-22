import versioning.BuildConfig

plugins {
    `maven-publish`
    jester.`base-conventions`
}

repositories {
    // We still call mavenLocal() conditionally at the top for non-exclusive deps (general fallback)
    if (BuildConfig.mavenLocalOverride) mavenLocal()

    // Grim API from grim snapshots; PacketEvents releases from CodeMC
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

    // ViaVersion
    exclusive("https://repo.viaversion.com", { mavenContent { releasesOnly() } }) {
        includeGroup("com.viaversion")
    }

    // Configuralize
    exclusive("https://nexus.scarsz.me/content/repositories/releases", { mavenContent { releasesOnly() } }) {
        includeGroup("github.scarsz")
    }

    mavenCentral()
}


dependencies {
    if (BuildConfig.shadePE) {
        api(libs.packetevents.api)
    } else {
        compileOnly(libs.packetevents.api)
    }
    api(libs.cloud.core)
    api(libs.cloud.processors.requirements)
    api(libs.configuralize) {
        artifact {
            classifier = "slim"
        }
        exclude(group = "org.yaml", module = "snakeyaml")
    }
    api(libs.snakeyaml)
    api(libs.fastutil)
    api(libs.adventure.text.minimessage)
    api(libs.jetbrains.annotations)
    api(libs.hikaricp)
    // MySQL connector — shaded so MySQL mode works out-of-the-box; SQLite is bundled with Paper
    implementation(libs.mysql.connector)

    api(libs.grim.api)
    api(libs.grim.internal)
    compileOnly(libs.grim.internal.shims)

    compileOnly(libs.viaversion)
    compileOnly(libs.netty)

    // Provided by Paper at runtime (previously pulled in transitively by the
    // removed floodgate-api dependency)
    compileOnly("com.google.guava:guava:32.1.2-jre")
    compileOnly("com.google.code.gson:gson:2.10.1")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
}

publishing.publications.create<MavenPublication>("maven") {
    from(components["java"])
}
