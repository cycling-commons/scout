pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/hammerheadnav/karoo-ext")
            credentials {
                username = providers.gradleProperty("gpr.user").getOrElse(System.getenv("USERNAME") ?: "")
                password = providers.gradleProperty("gpr.key").getOrElse(System.getenv("TOKEN") ?: "")
            }
        }
    }
}

rootProject.name = "ScoutKaroo"
include(":app")
include(":domain")

includeBuild(".deps/karoo-ext") {
    dependencySubstitution {
        substitute(module("io.hammerhead:karoo-ext")).using(project(":lib"))
    }
}
