import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.tasks.TaskProvider

/**
 * Fails the build if Compose reaches this module's classpath.
 *
 * For `:haptics`, whose point is that it has none: it plays effects, and an app
 * that wants them without a UI toolkit — or with a different one — should not
 * get Compose for asking. The risk is the same one `checkNoMaterial` guards
 * against, a convenience dependency pulling it in without anybody typing an
 * import, so this is the same walk over the resolved JVM runtime graph with a
 * wider net: any module whose group starts with a Compose group.
 */
fun Project.checkNoCompose(): TaskProvider<Task> {
    val forbiddenPrefixes = listOf("androidx.compose", "org.jetbrains.compose")
    val root = configurations.named("jvmRuntimeClasspath")
        .flatMap { it.incoming.resolutionResult.rootComponent }
    val here = path

    val check = tasks.register("checkNoCompose") {
        group = "verification"
        description = "Fails if a Compose dependency reaches the $here classpath."
        inputs.property("forbiddenGroupPrefixes", forbiddenPrefixes)

        doLast {
            val visited = mutableSetOf<String>()
            val offenders = sortedSetOf<String>()

            fun walk(component: ResolvedComponentResult) {
                if (!visited.add(component.id.displayName)) return
                component.moduleVersion?.let { id ->
                    if (forbiddenPrefixes.any { id.group == it || id.group.startsWith("$it.") }) {
                        offenders += "${id.group}:${id.name}:${id.version}"
                    }
                }
                component.dependencies
                    .filterIsInstance<ResolvedDependencyResult>()
                    .forEach { walk(it.selected) }
            }
            walk(root.get())

            if (offenders.isNotEmpty()) {
                throw GradleException(
                    buildString {
                        appendLine("Compose reached the $here classpath:")
                        offenders.forEach { appendLine("  - $it") }
                        appendLine()
                        appendLine("This module plays haptics and nothing else; it must not need a UI toolkit.")
                        appendLine("Find which dependency pulls this in with:")
                        appendLine("  ./gradlew $here:dependencies --configuration jvmRuntimeClasspath")
                    }
                )
            }
        }
    }

    tasks.named("check") { dependsOn(check) }
    return check
}
