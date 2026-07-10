import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog") version "2.2.1"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")

    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3")

    intellijPlatform {
        intellijIdeaCommunity("2024.2.6")
        bundledPlugin("Git4Idea")
        pluginVerifier()
    }
}

changelog {
    groups.empty()
    repositoryUrl = "https://github.com/engineer-myoa/idea-git-local-review"
}

fun renderChangeNotes(): String {
    val pluginVersion = providers.gradleProperty("version").get()
    return with(changelog) {
        renderItem(
            (getOrNull(pluginVersion) ?: getUnreleased())
                .withHeader(false)
                .withEmptySections(false),
            Changelog.OutputType.HTML,
        )
    }
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("version")
        changeNotes = renderChangeNotes()
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2024.2.6")
            create(IntelliJPlatformType.IntellijIdeaUltimate, "2026.1.4")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
