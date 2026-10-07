// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.3.1" apply false
    // Kotlin is built into AGP 9 (no org.jetbrains.kotlin.android plugin). This plugin's
    // version also sets the Kotlin Gradle Plugin version AGP compiles with.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
