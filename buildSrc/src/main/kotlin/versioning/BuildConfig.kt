package versioning

import org.gradle.api.Project
import org.gradle.internal.extensions.stdlib.toDefaultLowerCase
import versioning.BuildConfig.init
import versioning.BuildConfig.mavenLocalOverride
import versioning.BuildConfig.release
import versioning.BuildConfig.relocate
import versioning.BuildConfig.shadePE

object BuildConfig {
    fun init(project: Project) {
        _shadePE = resolveBool(project, "shadePE", altKey = "SHADE_PE", default = true)
        _relocate = resolveBool(project, "relocate", altKey = "RELOCATE_JAR", default = true)
        _release = resolveBool(project, "release", default = false)
        _mavenLocalOverride = resolveBool(project, "mavenLocalOverride", altKey = "MAVEN_LOCAL_OVERRIDE", default = false)
    }

    private fun resolveRaw(project: Project, key: String): String? =
        System.getProperty(key)
            ?: project.findProperty(key)?.toString()
            ?: System.getenv(key.uppercase())

    private fun resolveBool(project: Project, key: String, altKey: String? = null, default: Boolean): Boolean {
        return resolveRaw(project, key)?.toDefaultLowerCase()?.toBooleanStrictOrNull()
            ?: altKey?.let {
                resolveRaw(project, it)?.toDefaultLowerCase()?.toBooleanStrictOrNull()
            } ?: default
    }

    private var _shadePE: Boolean? = null
    private var _relocate: Boolean? = null
    private var _release: Boolean? = null
    private var _mavenLocalOverride: Boolean? = null

    val shadePE: Boolean get() = _shadePE
        ?: error("BuildConfig.shadePE accessed before init() was called")

    val relocate: Boolean get() = _relocate
        ?: error("BuildConfig.relocate accessed before init() was called")

    val release: Boolean get() = _release
        ?: error("BuildConfig.release accessed before init() was called")

    val mavenLocalOverride: Boolean get() = _mavenLocalOverride
        ?: error("BuildConfig.release accessed before init() was called")
}
