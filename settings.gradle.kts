pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "mostwanted"

// The game: every system the server runs, in one plugin.
include("core")
// The database tables, migrations and message style, shared by core and backend.
include("common")
// The website's API and the Discord bot, in one app (backend/discord-setup is separate: a Python script).
include("backend")
