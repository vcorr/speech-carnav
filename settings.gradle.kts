pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Versions live here so a plugin is only resolved when a module applies it:
    // the Android plugin is never downloaded when :app is not included.
    plugins {
        id("com.android.application") version "8.13.0"
        id("org.jetbrains.kotlin.android") version "2.4.20"
        id("org.jetbrains.kotlin.jvm") version "2.4.20"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "finnav"

include(":core")

// :app needs the Android SDK. Without one (CI, cloud sessions) only :core is built,
// so the pure logic can still be compiled and tested anywhere with a JDK.
val localSdkDir = file("local.properties").takeIf { it.exists() }
    ?.readLines()
    ?.firstOrNull { it.trim().startsWith("sdk.dir=") }
    ?.substringAfter("=")
val androidSdk = listOfNotNull(localSdkDir, System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"))
    .firstOrNull { file(it).isDirectory }

if (androidSdk != null) {
    include(":app")
} else {
    logger.warn("No Android SDK found (local.properties sdk.dir, ANDROID_HOME): building :core only.")
}
