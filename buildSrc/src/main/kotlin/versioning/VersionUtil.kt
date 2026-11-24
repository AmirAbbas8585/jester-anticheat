package versioning

import java.io.ByteArrayOutputStream

object VersionUtil {
    fun computeVersion(baseVersion: String): String {
        if (BuildConfig.release) {
            return baseVersion
        }

        val commitHash = getGitCommitHash()
        val branch = getGitBranch()

        val modifiers = buildList {
            if (!BuildConfig.shadePE) add("lite")
            if (!BuildConfig.relocate) add("no_relocate")
        }.joinToString("-").takeIf { it.isNotEmpty() }

        return buildString {
            append(baseVersion)
            append("-")
            branch?.let { append("$it-") }
            append(commitHash)
            modifiers?.let { append("+$it") }
        }
    }

    fun getGitCommitHash(full: Boolean = false): String {
        val stdout = ByteArrayOutputStream()
        val command = listOfNotNull("git", "rev-parse", if (full) null else "--short", "HEAD")
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
            .apply { waitFor() }
        if (process.exitValue() != 0) return "nogit"
        process.inputStream.use { stdout.writeBytes(it.readAllBytes()) }
        val fullCommit = stdout.toString().trim()
        return fullCommit.take(minOf(fullCommit.length, 7))
    }

    fun getGitBranch(raw: Boolean = false): String? {
        val stdout = ByteArrayOutputStream()

        val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
            .redirectErrorStream(true)
            .start()
            .apply { waitFor() }
        if (process.exitValue() != 0) return null
        process.inputStream.use { stdout.writeBytes(it.readAllBytes()) }

        if (raw) return stdout.toString().trim()

        val branch = stdout.toString().trim()
            .replace(Regex("[^a-zA-Z0-9_.-]+"), "_")
            .replace(Regex("_{2,}"), "_")
            .trim(' ', '.', '_', '-')
            .removePrefix("heads_")

        val mainBranch = System.getenv("GRIM_MAIN_BRANCH") ?: "2.0"

        return when (branch) {
            "main", mainBranch -> null
            else -> branch
        }
    }

    fun getGitUser(): String {
        try {
            val stdout = ByteArrayOutputStream()
            ProcessBuilder("git", "config", "user.name")
                .redirectErrorStream(true)
                .start()
                .apply { waitFor() }
                .inputStream.use { stdout.writeBytes(it.readAllBytes()) }
            return stdout.toString().trim()
        } catch (_: Exception) {
            return "unknown"
        }
    }
}
