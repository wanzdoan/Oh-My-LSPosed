pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "OhMyLSPosed"
include(":app", ":core")
listOf("zalo", "locket", "tiktok", "facebook", "messenger", "chrome").forEach {
    include(":features:$it")
}
