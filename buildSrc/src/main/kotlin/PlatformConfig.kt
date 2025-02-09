import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
//import net.minecrell.gradle.licenser.LicenseExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.external.javadoc.CoreJavadocOptions
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

fun Project.applyPlatformAndCoreConfiguration() {
    applyCommonConfiguration()
    apply(plugin = "java")
    apply(plugin = "eclipse")
    apply(plugin = "idea")
    apply(plugin = "maven")
    apply(plugin = "checkstyle")
    apply(plugin = "com.github.johnrengelman.shadow")
    apply(plugin = "com.jfrog.artifactory")
//    apply(plugin = "net.minecrell.licenser")

    ext["internalVersion"] = "$version;${rootProject.ext["gitCommitHash"]}"

    configure<JavaPluginConvention> {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    configure<CheckstyleExtension> {
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        toolVersion = "7.6.1"
    }

    tasks.withType<Test>().configureEach {
        useJUnit()
    }

    dependencies {
        "testCompile"("junit:junit:${Versions.JUNIT}")
        // TODO switch to jupiter - doesn't support abstract test classes so tests need rewriting
        //"testImplementation"("org.junit.jupiter:junit-jupiter-api:${Versions.JUNIT}")
        //"testRuntime"("org.junit.jupiter:junit-jupiter-engine:${Versions.JUNIT}")
    }

    // Java 8 turns on doclint which we fail
    tasks.withType<Javadoc>().configureEach {
        (options as CoreJavadocOptions).addStringOption("Xdoclint:none", "-quiet")
    }

    tasks.register<Jar>("javadocJar") {
        dependsOn("javadoc")
        archiveClassifier.set("javadoc")
        from(tasks.getByName<Javadoc>("javadoc").destinationDir)
    }

    tasks.named("assemble").configure {
        dependsOn("javadocJar")
    }

    artifacts {
        add("archives", tasks.named("jar"))
        add("archives", tasks.named("javadocJar"))
    }

    if (name == "worldguard-core" || name == "worldguard-bukkit") {
        tasks.register<Jar>("sourcesJar") {
            dependsOn("classes")
            archiveClassifier.set("sources")
            from(sourceSets["main"].allSource)
        }

        artifacts {
            add("archives", tasks.named("sourcesJar"))
        }
        tasks.named("assemble").configure {
            dependsOn("sourcesJar")
        }
    }

    tasks.named("check").configure {
        dependsOn("checkstyleMain", "checkstyleTest")
    }

    applyCommonArtifactoryConfig()

//    configure<LicenseExtension> {
//        header = rootProject.file("HEADER.txt")
//        include("**/*.java")
//    }

}

fun Project.applyShadowConfiguration() {
    tasks.named<ShadowJar>("shadowJar") {
        archiveClassifier.set("dist")
        dependencies {
            include(project(":worldguard-libs:core"))
            //include(project(":worldguard-libs:${project.name.replace("worldguard-", "")}"))
            include(project(":worldguard-core"))

            relocate("org.flywaydb", "com.sk89q.worldguard.internal.flywaydb") {
                include(dependency("org.flywaydb:flyway-core:3.0"))
            }
            relocate("com.sk89q.squirrelid", "com.sk89q.worldguard.util.profile")
        }
        exclude("GradleStart**")
        exclude(".cache")
        exclude("LICENSE*")
    }
}
