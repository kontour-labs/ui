import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.tasks.TaskProvider

/**
 * Fails the build if Material reaches this module's classpath.
 *
 * Commit 34862a9 removed Material from this project on purpose. The risk is not
 * that someone types `import androidx.compose.material3` — that is easy to spot
 * in review — but that a convenience library pulls it back in transitively and
 * nobody notices until the app is 400 KB heavier and two type scales deep.
 *
 * This walks the fully resolved JVM runtime graph and fails the build if any
 * Material module appears anywhere in it. One target is enough: neither
 * published module has per-platform dependencies that the JVM does not also
 * see, so anything that reaches Android or iOS reaches the JVM classpath too.
 *
 * Here rather than in one build script because `:ui-nav3` needs it more than
 * `:ui` does. The ready-made adaptive scene strategy for Navigation 3 is
 * Material's, and the whole reason that module exists is that it cannot be
 * used here — so a dependency that quietly brought it back is the failure most
 * worth catching, and one forbidden list is what keeps the two checks agreeing.
 */
fun Project.checkNoMaterial(): TaskProvider<Task> {
    val forbidden = setOf(
        "androidx.compose.material",
        "androidx.compose.material3",
        "org.jetbrains.compose.material",
        "org.jetbrains.compose.material3",
    )
    val root = configurations.named("jvmRuntimeClasspath")
        .flatMap { it.incoming.resolutionResult.rootComponent }
    val here = path

    val check = tasks.register("checkNoMaterial") {
        group = "verification"
        description = "Fails if a Material dependency reaches the $here classpath."
        inputs.property("forbiddenGroups", forbidden)

        doLast {
            val visited = mutableSetOf<String>()
            val offenders = sortedSetOf<String>()

            fun walk(component: ResolvedComponentResult) {
                if (!visited.add(component.id.displayName)) return
                component.moduleVersion?.let { id ->
                    if (id.group in forbidden) {
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
                        appendLine("Material reached the $here classpath:")
                        offenders.forEach { appendLine("  - $it") }
                        appendLine()
                        appendLine("The design system is built on Compose Foundation only.")
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
