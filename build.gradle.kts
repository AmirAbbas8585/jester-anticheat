/**
 *          Jester Anti Cheat Build Configuration
 *
 * Build Flags:
 * -PshadePE=true   - Enables 'lite' mode
 * -Prelocate=false - Adds 'no_relocate' modifier
 * -Prelease=true   - Removes commit/modifiers for release build
 *
 * Logic in: buildSrc/versioning/BuildConfig.kt & VersionUtil.kt
 */

import versioning.BuildConfig
import versioning.VersionUtil

BuildConfig.init(project)

val baseVersion = "0.0.3"
group = "ac.jester.anticheat"
// Clean, fixed version label for the beta (no git/lite build suffixes).
version = "$baseVersion-beta"
description = "Jester Anti Cheat - Advanced Minecraft Anti-Cheat."

ext["timestamp"] = System.currentTimeMillis().toString()
ext["git_branch"] = VersionUtil.getGitBranch(true) ?: "unknown"
ext["git_commit"] = VersionUtil.getGitCommitHash(true)
ext["git_org"] = System.getenv("GRIM_GIT_ORG") ?: VersionUtil.getGitUser()
ext["git_repo"] = System.getenv("GRIM_GIT_REPO") ?: "Grim"

println("Build configuration:")
println("    shadePE            = ${BuildConfig.shadePE}")
println("    relocate           = ${BuildConfig.relocate}")
println("    mavenLocalOverride = ${BuildConfig.mavenLocalOverride}")
println("    release            = ${BuildConfig.release}")
println("    version            = $version")

tasks.register("printVersion") {
    group = "versioning"
    description = "Prints the computed project version"
    doLast {
        println("VERSION=$version")
    }
}

// ---------- Java Compile Optimization ----------
subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.isFork = true
        options.isIncremental = true
    }
}
