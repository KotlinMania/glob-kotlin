pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins { kotlin("multiplatform") version "2.3.21" }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
    repositories {
        // km-io isn't published to Maven Central yet; the kotlinmania fork
        // is consumed via publishToMavenLocal out of the sibling km-io
        // worktree at /Volumes/stuff/Projects/kotlinmania/km-io. Once km-io
        // ships its first release this entry can come out.
        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "glob-kotlin"
