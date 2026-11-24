import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.InclusiveRepositoryContentDescriptor
import org.gradle.kotlin.dsl.maven
import versioning.BuildConfig

fun RepositoryHandler.exclusive(
    url: String,
    repoConfig: MavenArtifactRepository.() -> Unit = {},
    filterConfig: InclusiveRepositoryContentDescriptor.() -> Unit
) {
    val remote = maven(url, repoConfig)

    exclusive(remote, filterConfig)
}

fun RepositoryHandler.exclusive(
    remote: MavenArtifactRepository,
    filterConfig: InclusiveRepositoryContentDescriptor.() -> Unit
) {
    val local = if (BuildConfig.mavenLocalOverride) mavenLocal() else null

    exclusiveContent {
        if (local != null) {
            forRepositories(local, remote)
        } else {
            forRepositories(remote)
        }
        filter(filterConfig)
    }
}
