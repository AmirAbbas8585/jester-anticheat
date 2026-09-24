import versioning.BuildConfig

plugins {
    `maven-publish`
    jester.`base-conventions`
}

repositories {
    if (BuildConfig.mavenLocalOverride) mavenLocal()

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

    exclusive("https://repo.viaversion.com", { mavenContent { releasesOnly() } }) {
        includeGroup("com.viaversion")
    }

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
    implementation(libs.mysql.connector)

    api(libs.grim.api)
    api(libs.grim.internal)
    compileOnly(libs.grim.internal.shims)

    compileOnly(libs.viaversion)
    compileOnly(libs.netty)

    compileOnly("com.google.guava:guava:32.1.2-jre")
    compileOnly("com.google.code.gson:gson:2.10.1")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
}

publishing.publications.create<MavenPublication>("maven") {
    from(components["java"])
}

val validateBundledConfig by tasks.registering {
    val files = fileTree("src/main/resources") { include("config/*.yml", "messages/*.yml") }
    inputs.files(files)
    doLast {
        val keyLine = Regex("""^(\s*)(- )?('[^']*'|"[^"]*"|[^\s#'"-][^:]*?):(\s|$)""")
        val problems = mutableListOf<String>()
        files.forEach { file ->
            val stack = ArrayDeque<Triple<Int, String, MutableSet<String>>>()
            stack.addLast(Triple(-1, "", mutableSetOf()))
            file.readLines().forEachIndexed { index, raw ->
                val line = raw.trimEnd('\r')
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("---")) return@forEachIndexed
                val match = keyLine.find(line) ?: return@forEachIndexed
                var indent = match.groupValues[1].length
                val key = match.groupValues[3].trim('\'', '"')
                if (match.groupValues[2].isNotEmpty()) {
                    while (stack.last().first >= indent) stack.removeLast()
                    stack.addLast(Triple(indent, stack.last().second + "[-]", mutableSetOf()))
                    indent += 2
                }
                while (stack.last().first >= indent) stack.removeLast()
                val parent = stack.last()
                val path = if (parent.second.isEmpty()) key else parent.second + "." + key
                if (!parent.third.add(key)) {
                    problems += "${file.parentFile.name}/${file.name}:${index + 1}: duplicate key '$path'"
                }
                stack.addLast(Triple(indent, path, mutableSetOf()))
            }
            if (file.readLines().none { it.startsWith("config-version:") }) {
                problems += "${file.parentFile.name}/${file.name}: missing top-level config-version"
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Bundled config is invalid:\n  " + problems.joinToString("\n  "))
        }
    }
}
tasks.named("processResources") { dependsOn(validateBundledConfig) }
